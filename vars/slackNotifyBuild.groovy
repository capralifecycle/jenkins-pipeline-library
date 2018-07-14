#!/usr/bin/groovy

def call(Map args = [:]) {
  // The build statuses should follow the list in
  // http://javadoc.jenkins-ci.org/hudson/model/Result.html
  def buildStatus = args.buildStatus ?: 'SUCCESS'

  def params = buildConfigParams().slack ?: [:]
  if (params.channel == null) {
    // We require a channel to be set
    throw new Exception("Missing Slack configuration with buildConfig")
  }

  def colorMap = [
    'ABORTED': '',
    'FAILURE': 'danger',
    'NOT_BUILT': '',
    'STARTED': '',
    'SUCCESS': 'good',
    'UNSTABLE': 'warning',
  ]

  slackNotify([
    color: colorMap[buildStatus] != null ? colorMap[buildStatus] : 'danger',
    message: "${buildStatus}: Job `<${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>`",
  ])
}
