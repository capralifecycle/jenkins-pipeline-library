#!/usr/bin/groovy
package no.capraconsulting.buildtools.serverlessjavapipeline

/**
 * This method allows for easy setup of serverless pipelines using Java
 * with deployment to two different environments (systest and prod) with
 * a release step in betwen and manual confirmation for release and deploy
 * to prod.
 */
def pipeline(Closure cl) {
  def config = new ConfigDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def currentVersion
  def artifactId

  dockerNode {
    checkout scm

    insideMaven(avoidAlpine: true) {
      def pom = readMavenPom file: 'pom.xml'
      currentVersion = pom.version
      artifactId = pom.artifactId

      def goal = 'package'
      if (env.BRANCH_NAME == config.deployBranch) {
        goal = 'deploy'
      }
      stage('Build and test Java project') {
        sh "mvn -s $MAVEN_SETTINGS -B source:jar $goal"
      }
    }

    if (env.BRANCH_NAME == config.deployBranch) {
      if (config.predeployHook) {
        config.predeployHook()
      }

      if (config.deploySystest) {
        stage('Deploy to systest') {
          milestone 1
          lock(resource: "deploy-systest-${env.JOB_NAME}", inversePrecedence: true) {
            milestone 2

            serverlessDeploy(
              config.deploySystest.iamRole,
              [
                "--artifact target/${artifactId}-${currentVersion}.jar",
                "--stage ${config.deploySystest.stage}",
                "--region ${config.deploySystest.region}",
              ].join(' ')
            )
          }
        }
      }
    }
  }

  if (env.BRANCH_NAME == config.deployBranch) {
    def releaseVersion

    milestone 3
    def userInput = askRelease(currentVersion, config)
    if (!userInput) {
      return
    }

    lock(resource: "release-${env.JOB_NAME}", inversePrecedence: true) {
      milestone 4

      dockerNode {
        checkout scm

        stage('Release') {
          releaseVersion = userInput.releaseVersion
          insideMaven(avoidAlpine: true) {
            withGitConfig {
              echo "Releasing as $userInput.releaseVersion"
              sh """
                mvn \\
                  -B \\
                  -s $MAVEN_SETTINGS \\
                  -DreleaseVersion=${userInput.releaseVersion} \\
                  -DdevelopmentVersion=${userInput.nextSnapshotVersion} \\
                  -DpushChanges=false \\
                  -DlocalCheckout=true \\
                  -DpreparationGoals=initialize \\
                  release:prepare \\
                  release:perform
              """

              withGitTokenCredentials {
                sh 'git push --tags'
              }

              stash includes: "serverless.yml,config-*.json,target/checkout/target/$artifactId-*.jar", name: 'release'
            }
          }
        }
      } // end dockerNode
    } // end lock

    if (config.deployProd) {
      milestone 5
      if (!askDeployConfirm(releaseVersion, config)) {
        return
      }

      lock(resource: "deploy-prod-${env.JOB_NAME}", inversePrecedence: true) {
        milestone 6
        stage('Deploy to prod') {
          dockerNode {
            unstash 'release'
            serverlessDeploy(
              config.deployProd.iamRole,
              [
                "--artifact target/checkout/target/${artifactId}-${releaseVersion}.jar",
                "--stage ${config.deployProd.stage}",
                "--region ${config.deployProd.region}",
              ].join(' ')
            )
          }
        }
      } // end lock
    } // end config.deployProd
  }
}

def askRelease(currentVersion, config) {
  stage('Asking to release') {
    try {
      timeout(config.inputTimeout) {
        return input(
          id: 'release',
          message: 'Release?',
          parameters: [
            string(
              defaultValue: "${currentVersion}",
              description: 'Next snapshot version',
              name: 'nextSnapshotVersion',
              trim: true
            ),
            string(
              defaultValue: '',
              description: 'Release version',
              name: 'releaseVersion',
              trim: true
            )
          ]
        )
      }
    } catch (ignored) {
      currentBuild.result = 'ABORTED'
      echo 'Skipping release'
    }
  }
}

def askDeployConfirm(currentVersion, config) {
  stage('Asking to deploy release to prod') {
    try {
      timeout(config.inputTimeout) {
        input(
          id: 'prod',
          message: "Deploy release ${currentVersion} to prod?"
        )
      }
      return true
    } catch (ignored) {
      echo 'Skipping deployment to prod'
      currentBuild.result = 'ABORTED'
      return false
    }
  }
}

class ConfigDelegate implements Serializable {
  String deployBranch = 'master'
  Closure predeployHook

  Env deploySystest
  Env deployProd

  Map inputTimeout = [time: 3, unit: 'DAYS']
}

class Env implements Serializable {
  String iamRole
  String stage
  String region
}
