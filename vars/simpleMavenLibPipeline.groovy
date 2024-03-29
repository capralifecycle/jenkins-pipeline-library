#!/usr/bin/groovy

/**
 * A quickfix for allowing simpleMavenLibPipeline to be used as a stage
 * inside existing pipelines. Without this, a Jenkinsfile using simpleMavenLibPipeline
 * as a stage inside an existing pipeline (as opposed to using it as a standalone pipeline)
 * will occupy two Jenkins slaves, which can easily result in resource exhaustion and deadlocks.
*/
def conditionalDockerNode(String dockerNodeLabel, Closure body) {
  if (env.NODE_NAME) {
    echo 'Executing the Maven library pipeline inside the existing Docker node'
    body()
  } else {
    dockerNode([label: dockerNodeLabel]) {
      body()
    }
  }
}

/**
 * Pipeline that creates release when master branch has changes since latest tag.
 * For other branches the build is only verified.
 *
 * To set up a project to use this see
 * https://confluence.capraconsulting.no/x/fckBC
 *
 * Parameters:
 *
 *  - dockerBuildImage (required): The Docker image to use as build container
 *  - dockerNodeLabel: Label used for Jenkins slave
 *  - buildConfigParams: Parameters passed to buildConfig
 *  - useMavenEnforcer: Whether to run maven-enforcer-plugin before deploy or not. Default "true"
 */
def call(Map args) {
  String dockerBuildImage = args["dockerBuildImage"]
    ?: { throw new RuntimeException("Missing arg: dockerBuildImage") }()

  buildConfig(args.buildConfigParams ?: [:]) {
    conditionalDockerNode(args.dockerNodeLabel) {
      def buildImage = docker.image(dockerBuildImage)
      buildImage.pull() // Ensure latest version

      buildImage.inside {
        stage('Checkout source') {
          checkout scm
        }

        stage('Build and conditionally release') {
          withMavenDeployVersionByTimeEnv { String revision ->
            withGitTokenCredentials {
              // When releasing to
              // GitHub Packages, we verify that the project's pom file is properly configured
              def jobNameParts = env.JOB_NAME.tokenize('/') as String[]
              String currentRepository = jobNameParts.length < 2 ? env.JOB_NAME : jobNameParts[jobNameParts.length - 2]
              String targetRepository = sh([
                returnStdout: true,
                // Return the name of a GitHub Packages repository configured in the project's
                // distribution management, if any is defined. If no such URLs are present (e.g., if using other
                // remote repositories), the variable will be null or an empty string.
                script: "awk '/<distributionManagement>/,/<\\/distributionManagement>/' pom.xml | sed -n 's/^.*<url>.*maven\\.pkg\\.github\\.com\\/[^/]\\{1,\\}\\/\\(.*\\)<\\/url>/\\1/p' | head -1"
            ]).trim()
              if (targetRepository != null && !targetRepository.isEmpty() && targetRepository != currentRepository && currentRepository != targetRepository.concat('-pipeline')) {
                error("Maven is configured to publish to GitHub Packages in repository '${targetRepository}', but the current repository is '${currentRepository}'")
              }
              String goal = env.BRANCH_NAME == "master" && changedSinceLatestTag()
                ? "source:jar deploy scm:tag"
                : "verify"

              withGitConfig {
                def useMavenEnforcer = args.useMavenEnforcer == null ?  true : args.useMavenEnforcer
                def enforcer = useMavenEnforcer ? "org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce -Drules=requireReleaseDeps" : ""

                // Run enforcer alongside verify or deploy to make Reactor resolve module dependencies in multi-module projects.
                sh """
                  mvn -s \$MAVEN_SETTINGS -B -Dtag=$revision -Drevision=$revision clean $enforcer $goal
                """
              }
            }
          }
        }
      }
    }
  }
}

private Boolean changedSinceLatestTag() {
  String currentCommit = headCommitHash()
  String latestTag = previousTagLabel()
  String commitOfLatestTag = latestTag != null ? underlyingCommit(latestTag) : null
  echo "Current commit: ${currentCommit}"
  echo "Latest tag: ${latestTag}"
  echo "Latest tag underlying commit: ${commitOfLatestTag}"

  def hasChange = currentCommit != commitOfLatestTag
  if (hasChange)
    echo "Build has change."
  else
    echo "Build has no change."
  hasChange
}

/**
 * @return Underlying commit hash for tag.
 */
private String underlyingCommit(String tagLabel) {
  sh(
    script: "git rev-parse $tagLabel^{}",
    returnStdout: true
  ).trim()
}

/**
 * @return Tag label for latest pushed tag in current branch. Null if no tag found.
 */
private String previousTagLabel() {
  sh "git fetch --tags"
  try {
    sh(
      script: "git describe --abbrev=0",
      returnStdout: true
    ).trim()
  } catch (e) {
    println "No previous tag label found - assuming initial case"
    null
  }
}

/**
 * @return Head commit hash.
 */
private String headCommitHash() {
  sh(
    script: "git rev-parse HEAD",
    returnStdout: true
  ).trim()
}

private def withMavenDeployVersionByTimeEnv(body) {
  withMavenSettings {
    String majorVersion = readMavenPom().getProperties()['major-version']
    String revision = revision(majorVersion)

    withEnv(["revision=$revision"]) {
      body(revision)
    }
  }
}

private static String revision(String majorVersion) {
  def now = new Date()
  def buildDate = now.format("yyyyMMdd", TimeZone.getTimeZone("UTC"))
  def buildTime = now.format("HHmmss", TimeZone.getTimeZone("UTC"))
  "$majorVersion.$buildDate.$buildTime"
}
