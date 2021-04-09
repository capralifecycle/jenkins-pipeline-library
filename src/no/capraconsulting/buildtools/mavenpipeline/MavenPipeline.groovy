#!/usr/bin/groovy
package no.capraconsulting.buildtools.mavenpipeline

import no.capraconsulting.buildtools.Utils

def utils = new Utils()

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
    dockerNode([label: buildConfig.dockerNodeLabel]) {
      def buildImage = docker.image(buildConfig.dockerBuildImage)
      buildImage.pull() // Ensure latest version

      buildImage.inside {
        stage('Checkout source') {
          checkout scm
        }

        def revisionMavenArg = buildConfig.setRevisionAsLongTag ? revisionArgs() : ""

        try {
          withMavenSettings {
            stage('Build and verify') {
              if (buildConfig.requireReleaseDeps) {
                sh "mvn -s \$MAVEN_SETTINGS -B org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce -Drules=requireReleaseDeps"
              }

              sh "mvn -s \$MAVEN_SETTINGS -B --update-snapshots ${buildConfig.mavenArgs} ${revisionMavenArg} ${buildConfig.mavenGoals}"
            }
          }
        } finally {
          saveJunitReport()
          saveJacocoReport()
        }
      }

      if (buildConfig.dockerBuild) {
        buildConfig.dockerBuild.call()
      }
    }
  }

  return build
}

private def revisionArgs() {
  def revision = utils.generateLongTag(new Date())
  "-Dtag=$revision -Drevision=$revision"
}

def createDockerBuild(Closure cl = null) {
  def dockerBuildConfig = new CreateDockerBuildDelegate()
  if (cl) {
    cl.resolveStrategy = Closure.DELEGATE_FIRST
    cl.delegate = dockerBuildConfig
    cl()
  }

  def dockerBuild = {
    def baseDir = { body ->
      body()
    }

    if (dockerBuildConfig.baseDir) {
      baseDir = { body ->
        dir(dockerBuildConfig.baseDir) {
          body()
        }
      }
    }

    if (dockerBuildConfig.copyJar) {
      sh """
        f=\$(ls -1 target/*.jar | grep -v original)
        if [ \$(echo "\$f" | wc -l) -ne 1 ]; then
          echo "Not exactly one jar file found."
          echo "Found:"
          echo "\$f"
          exit 1
        fi

        cp "\$f" "${dockerBuildConfig.baseDir ?: '.'}/app.jar"
      """
    }

    baseDir {
      // Use a random name so it will not conflict with any other build.
      def name = UUID.randomUUID().toString()

      stage('Build Docker image') {
        sh "docker build -t $name ${dockerBuildConfig.contextDir}"
      }

      if (dockerBuildConfig.testImage) {
        stage('Test Docker image') {
          dockerBuildConfig.testImage.call(name)
        }
      }
    }

    // TODO: Version the image.
    // TODO: Upload to ECR (for only some branches?)
  }

  return dockerBuild
}

void checkNotNull(value, name) {
  if (value == null) {
    throw new Exception("$name must be set")
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
  /** Optional override of Jenkins slave node label. */
  String dockerNodeLabel
  String dockerBuildImage
  /** Optional extra args to mvn command. */
  String mavenArgs = ''
  /** The goal targeted. */
  String mavenGoals = 'verify'
  /**
   * Set mvn properties tag and revison to generated long tag.
   */
  Boolean setRevisionAsLongTag = false
  /**
   * Verify no snapshots used as dependencies using maven-enforcer-plugin
   * before normal build.
   */
  Boolean requireReleaseDeps = false
  /**
   * Docker build process. Most of our services we build with Maven
   * builds a Docker image that will be run in an environment.
   * Optional.
   */
  Closure dockerBuild
}

class CreateDockerBuildDelegate implements Serializable {
  String baseDir
  String contextDir = '.'
  /** Copy the jar from target directory to 'app.jar' in baseDir. */
  boolean copyJar
  /**
   * Hook for testing the image after being built.
   * Receives the name of the built image as argument.
   */
  Closure testImage
}
