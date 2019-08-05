#!/usr/bin/groovy
package no.capraconsulting.buildtools.maven

def pipeline(Closure cl) {
  def config = new ConfigDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  checkNotNull(config.build, 'build')

  buildConfig(config.buildConfigParams) {
    config.build.call(config)
  }
}

def saveJunitReport() {
  def junitFiles = []

  if (fileExists('target/surefire-reports/junitreports')) {
    // Default output directory when using TestNG.
    junitFiles << 'target/surefire-reports/junitreports/*.xml'
  } else if (fileExists('target/surefire-reports')) {
    // Default output directory when not overriding junit default.
    junitFiles << 'target/surefire-reports/*.xml'
  } else {
    echo 'No junit xml report found'
  }

  if (junitFiles.size > 0) {
    junit(
      allowEmptyResults: true,
      testResults: junitFiles.join(','),
    )
  }

  if (fileExists('target/surefire-reports/Surefire suite/Surefire test.html')) {
    publishHTML(
      target: [
        allowMissing: true,
        alwaysLinkToLastBuild: false,
        keepAll: true,
        reportDir: 'target/surefire-reports/Surefire suite',
        reportFiles: 'Surefire test.html',
        reportName: 'Unit Test Report'
      ]
    )
  } else {
    echo 'No junit html report found'
  }
}

def saveJacocoReport() {
  if (fileExists('target/site/jacoco/index.html')) {
    publishHTML(
      target: [
        allowMissing: true,
        alwaysLinkToLastBuild: false,
        keepAll: true,
        reportDir: 'target/site/jacoco',
        reportFiles: 'index.html',
        reportName: 'Test Coverage Report'
      ]
    )
  } else {
    echo 'No Jacoco (coverage) report found'
  }
}

def createBuild(Closure cl) {
  def buildConfig = new CreateBuildDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = buildConfig
  cl()

  // Verify build config requirements that must be set/changed.
  checkNotNull(buildConfig.dockerBuildImage, 'dockerBuildImage')

  def build = { config ->
    dockerNode {
      def buildImage = docker.image(buildConfig.dockerBuildImage)
      buildImage.pull() // Ensure latest version

      buildImage.inside {
        stage('Checkout source') {
          checkout scm
        }

        try {
          withMavenSettings {
            stage('Build and verify') {
              sh "mvn -s \$MAVEN_SETTINGS -B ${buildConfig.mavenArgs} ${buildConfig.mavenGoals}"
            }
          }
        } finally {
          saveJunitReport()
          saveJacocoReport()
        }
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

class ConfigDelegate implements Serializable {
  /**
   * The build process. It will be called with the current config
   * as its first argument.
   */
  Closure build
  /**
   * Parameters passed to `buildConfig`.
   */
  Map buildConfigParams = [:]
}

class CreateBuildDelegate implements Serializable {
  String dockerBuildImage
  /** Optional extra args to mvn command. */
  String mavenArgs = ''
  /** The goal targeted. */
  String mavenGoals = 'verify'
}
