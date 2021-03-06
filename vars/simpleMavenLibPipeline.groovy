#!/usr/bin/groovy

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
 */
def call(Map args) {
  String dockerBuildImage = args["dockerBuildImage"]
    ?: { throw new RuntimeException("Missing arg: dockerBuildImage") }()

  buildConfig(args.buildConfigParams ?: [:]) {
    dockerNode([label: args.dockerNodeLabel]) {
      def buildImage = docker.image(dockerBuildImage)
      buildImage.pull() // Ensure latest version

      buildImage.inside {
        stage('Checkout source') {
          checkout scm
        }

        stage('Build and conditionally release') {
          withMavenDeployVersionByTimeEnv { String revision ->
            withGitTokenCredentials {
              String goal = env.BRANCH_NAME == "master" && changedSinceLatestTag()
                ? "source:jar deploy scm:tag"
                : "verify"

              withGitConfig {
                sh """
                  mvn -s \$MAVEN_SETTINGS -B org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce -Drules=requireReleaseDeps
                  mvn -s \$MAVEN_SETTINGS -B -Dtag=$revision -Drevision=$revision $goal
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
