#!/usr/bin/groovy
package no.capraconsulting.buildtools.mavensimplelibpipeline

def pipeline(Closure cl) {
  createBuild(cl)
}

def createBuild(Closure cl) {
  def buildConfig = new CreateBuildDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = buildConfig
  cl()

  echo "${buildConfig.dockerBuildImage}"

  // Verify build config requirements that must be set/changed.
  checkNotNull(buildConfig.dockerBuildImage, 'dockerBuildImage')

  def build = { config ->
    dockerNode([label: buildConfig.dockerNodeLabel]) {
      def buildImage = docker.image(buildConfig.dockerBuildImage)
      buildImage.pull() // Ensure latest version

      buildImage.inside {
        stage('Checkout source') {
          checkout scm
        }

        try {
          stage('Build and conditionally release') {
            withMavenDeployVersionByTimeEnv { String revision ->
              String goal = env.BRANCH_NAME == "master" && changedSinceLatestTag()
                ? "source:jar deploy scm:tag"
                : "verify"
              sh """
                        mvn -s \$MAVEN_SETTINGS org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce -Drules=requireReleaseDeps
                        mvn -s \$MAVEN_SETTINGS -B -Dtag=$revision -Drevision=$revision $goal
                    """
            }
          }
        } finally {
//          saveJunitReport()
//          saveJacocoReport()
        }
      }

      if (buildConfig.dockerBuild) {
        buildConfig.dockerBuild.call()
      }
    }
  }

  return build
}

void checkNotNull(value, name) {
  if (value == null) {
    throw Exception("$name must be set")
  }
}

class CreateBuildDelegate implements Serializable {
  /** Optional override of Jenkins slave node label. */
  String dockerNodeLabel
  String dockerBuildImage
  /** Optional extra args to mvn command. */
  String mavenArgs = ''
  /** The goal targeted. */
  String mavenGoals = 'verify'
  /**
   * Docker build process. Most of our services we build with Maven
   * builds a Docker image that will be run in an environment.
   * Optional.
   */
  Closure dockerBuild
}

private Boolean changedSinceLatestTag() {
  withGitConfig {
    String currentCommit = headCommitHash()
    String latestTag = previousTagLabel()
    String commitOfLatestTag = underlyingCommit(latestTag)
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
 * @return Tag label for latest pushed tag in current branch.
 */
private String previousTagLabel() {
  sh "git fetch --tags"
  sh(
    script: "git describe --abbrev=0",
    returnStdout: true
  ).trim()
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

def withMavenDeployVersionByTimeEnv(body) {
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

