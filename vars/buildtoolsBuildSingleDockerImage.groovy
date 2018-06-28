#!/usr/bin/groovy

/**
 * Configure job and build docker image used as build tool
 */
def call(Map parameters = [:]) {
  // Extract all parameters so that it is easy to see which parameters
  // are available.
  def dockerImageName = parameters.dockerImage
  def testImageHook = parameters.testImageHook

  buildConfig([
    jobProperties: [
      pipelineTriggers([
        // Build a new version every night so we keep up to date with upstream changes
        cron('H H(2-6) * * *'),
      ]),
    ],
    slack: [
      channel: '#cals-dev-info',
      teamDomain: 'cals-capra',
    ],
  ]) {
    dockerNode {
      stage('Checkout source') {
        checkout scm
      }

      def img
      def lastImageId = dockerPullCacheImage(dockerImageName)

      stage('Build Docker image') {
        img = docker.build(dockerImageName, "--cache-from $lastImageId --pull .")
      }

      // Hook for running tests
      if (testImageHook != null) {
        testImageHook(img)
      }

      def isSameImage = dockerPushCacheImage(img, lastImageId)

      if (env.BRANCH_NAME == 'master' && !isSameImage) {
        stage('Push Docker image') {
          def tagName = 'latest'
          img.push(tagName)
          slackNotify message: "New Docker image available: $dockerImageName:$tagName"
        }
      }
    }
  }
}
