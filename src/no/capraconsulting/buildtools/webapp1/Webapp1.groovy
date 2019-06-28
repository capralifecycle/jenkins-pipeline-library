#!/usr/bin/groovy
package no.capraconsulting.buildtools.webapp1

def pipeline(Closure cl) {
  def config = new ConfigDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def helpers = new Helpers(this)

  helpers.checkNotNull(config.applicationName, 'applicationName')
  helpers.checkNotNull(config.build, 'build')
  helpers.checkNotNull(config.deployBucketName, 'deployBucketName')
  helpers.checkNotNull(config.deployRole, 'deployRole')

  config.build(config) {
    config.deployVersion = helpers.getPackageJsonVersion()
  }

  // Verify gatherDeployVersion is called.
  helpers.checkNotNull(config.deployVersion, 'deployVersion')

  def deployToQa = helpers.checkDeployToQa(config)

  if (deployToQa) {
    dockerNode {
      stage('Deploy:QA') {
        unstash 'build'
        helpers.deploy(config, 'qa')
      }
    }
  }

  if (env.BRANCH_NAME == 'master') {
    def deployToProd = helpers.checkDeployToProd(config)

    if (deployToProd) {
      dockerNode {
        stage('Deploy:Prod') {
          unstash 'build'
          helpers.deploy(config, 'prod')
        }
      }
    }
  }
}

def createBuild(Closure cl) {
  def buildConfig = new CreateBuildDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = buildConfig
  cl()

  // Verify build config requirements that must be set/changed.
  Helpers.checkNotNull(buildConfig.dockerBuildImage, 'dockerBuildImage')

  def build = { config, gatherDeployVersion ->
    dockerNode {
      def buildImage = docker.image(buildConfig.dockerBuildImage)
      buildImage.pull() // Ensure latest version

      if (buildConfig.sentry) {
        env.SENTRY_AUTH_TOKEN = buildConfig.sentry.authToken
        env.SENTRY_ORG = buildConfig.sentry.org
        env.SENTRY_PROJECT = buildConfig.sentry.project
      }

      buildImage.inside {
        stage('Checkout source') {
          checkout scm
          gatherDeployVersion()
        }

        stage('Install dependencies') {
          sh 'npm ci' // Install from lock-file
        }

        stage('Lint') {
          sh 'npm run lint'
        }

        stage('Test:UNIT') {
          try {
            sh 'npm test'
          } finally {
            if (buildConfig.unitTestCoverageOutputPath) {
              publishHTML target: [
                allowMissing: false,
                alwaysLinkToLastBuild: false,
                keepAll: true,
                reportDir: buildConfig.unitTestCoverageOutputPath,
                reportFiles: 'index.html',
                reportName: 'Test Report'
              ]
            }
          }
        }

        stage('Build') {
          sh 'npm run build'
          stash name: 'build', includes: 'build/**'
        }

        if (buildConfig.runE2eTests) {
          stage('Test:E2E') {
            sh 'npm run test:e2e:jenkins'
          }
        }
      }
    }
  }

  return build
}

class ConfigDelegate implements Serializable {
  String applicationName
  /**
   * The build process. It will be called with the current config
   * as its first argument and a method "gatherDeployVersion" as its second.
   *
   * - It must create a stash named `build` that contains the files to be
   *   deployed in its build directory.
   * - It must call its second argument after checking out files in
   *   order for gathering the deployVersion.
   */
  Closure build
  String deployBucketName
  Boolean autoDeployMasterToQa = true
  /** See comment for build attribute. */
  String deployVersion
  String deployRole
}

class CreateBuildDelegate implements Serializable {
  String dockerBuildImage
  Boolean runE2eTests = true
  /** Set this to the path from project directory to store coverage report. */
  String unitTestCoverageOutputPath

  /** To set up Sentry environment variables before build. */
  BuildSentryDelegate sentry

  void sentry(Closure cl) {
    sentry = new BuildSentryDelegate()
    cl.resolveStrategy = Closure.DELEGATE_FIRST
    cl.delegate = sentry
    cl()

    Helpers.checkNotNull(sentry.authToken, 'sentry.authToken')
    Helpers.checkNotNull(sentry.project, 'sentry.project')
  }
}

class BuildSentryDelegate implements Serializable {
  String org = 'capra-consulting-as'
  String authToken
  String project
}

class Helpers implements Serializable {
  // See https://jenkins.io/doc/book/pipeline/shared-libraries/#accessing-steps
  def steps
  Helpers(steps) {
    this.steps = steps
  }

  static void checkNotNull(value, name) {
    if (value == null) {
      throw Exception("$name must be set")
    }
  }

  boolean checkDeployToQa(ConfigDelegate config) {
    if (config.autoDeployMasterToQa && steps.env.BRANCH_NAME == 'master') {
      return true
    }

    def result
    steps.stage('Q: Deploy to QA?') {
      result = askDeploy(
        config.deployVersion,
        'deploy-to-qa',
        "Deploy ${config.applicationName}-${config.deployVersion} with branch ${steps.env.BRANCH_NAME} to QA?"
      )
    }

    return result
  }

  boolean checkDeployToProd(ConfigDelegate config) {
    def result
    steps.stage('Q: Deploy to PROD?') {
      result = askDeploy(
        config.deployVersion,
        'deploy-to-prod',
        "Deploy ${config.applicationName}-${config.deployVersion} to prod?"
      )
    }

    return result
  }

  String getPackageJsonVersion() {
    return steps.sh(
      script: 'cat package.json | jq -r .version',
      returnStdout: true
    ).trim()
  }

  boolean askDeploy(version, id, message) {
    try {
      def result
      steps.timeout(time: 5, unit: 'MINUTES') {
        result = steps.input(
          id: id,
          message: message,
          parameters: [
            [
              $class: 'BooleanParameterDefinition',
              defaultValue: true,
              description: '',
              name: 'Please confirm that you want to proceed'
            ]
          ]
        )
        steps.echo 'Deploy confirmed manually.'
      }
      return result
    } catch (err) {
      steps.echo 'Deploy aborted.'
      return false
    }
  }

  void deploy(config, environment) {
    steps.withAwsRole(config.deployRole) {
      steps.sh """
        aws s3 cp \\
          build \\
          s3://${config.deployBucketName}/$environment/${config.deployVersion} \\
          --recursive \\
          --exclude 'index.html' \\
          --exclude 'config.json' \\
          --exclude 'config/*' \\
          --exclude '*.map'

        # Deploy config file if present.
        config_file=build/config/${environment}.json
        if [ -e "\$config_file" ]; then
          aws s3 cp \\
            \$config_file \\
            s3://${config.deployBucketName}/$environment/${config.deployVersion}/config.json \\
            --cache-control 'no-cache'
        fi

        aws s3 cp \\
          build/index.html \\
          s3://${config.deployBucketName}/$environment/${config.deployVersion}/index.html \\
          --cache-control 'no-cache'
      """
    }

    steps.slackNotify message: "Deployed new version of ${config.applicationName}. Version: ${config.deployVersion} Environment: $environment"
  }
}
