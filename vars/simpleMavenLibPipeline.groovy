#!/usr/bin/groovy

def call(Map args) {
  String dockerBuildImage = args["dockerBuildImage"]
    ?: { throw new RuntimeException("Missing arg: dockerBuildImage") }()

  String dockerNodeLabel = args["dockerNodeLabel"] ?: "docker"

  echo "${dockerBuildImage}"
  echo "${dockerNodeLabel}"

  dockerNode([label: dockerNodeLabel]) {
    def buildImage = docker.image(dockerBuildImage)
    buildImage.pull() // Ensure latest version

    buildImage.inside {
      stage('Checkout source') {
        checkout scm
      }

      stage('Build and conditionally release') {
        withMavenDeployVersionByTimeEnv { String revision ->
          String goal = env.BRANCH_NAME == "master" && changedSinceLatestTag()
            ? "source:jar deploy scm:tag"
            : "verify"
          sh """
              mvn -s \$MAVEN_SETTINGS -B org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce -Drules=requireReleaseDeps
              mvn -s \$MAVEN_SETTINGS -B -Dtag=$revision -Drevision=$revision $goal
          """
        }
      }
    }
  }
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


