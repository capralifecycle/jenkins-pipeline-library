#!/usr/bin/groovy

/**
 * Configure job and build docker image used as build tool
 */
def call(Map parameters = [:]) {
  // Extract all parameters so that it is easy to see which parameters
  // are available.
  def dockerImageName = parameters.dockerImage
  def dockerImageTag = parameters.dockerImageTag ?: 'latest'
  def testImageHook = parameters.testImageHook

  def jobProperties = []

  if (env.BRANCH_NAME == 'master') {
    jobProperties << pipelineTriggers([
      // Build a new version every night so we keep up to date with upstream changes
      cron('H H(2-6) * * *'),
    ])
  }

  buildConfig([
    jobProperties: jobProperties,
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
          img.push(dockerImageTag)
          slackNotify message: "New Docker image available: $dockerImageName:$dockerImageTag"
        }
      }
    }
  }
}
