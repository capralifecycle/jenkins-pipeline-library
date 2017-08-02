#!/usr/bin/groovy

def call(Map args = [:]) {
  def buildStatus = args.buildStatus ?: 'SUCCESS'

  def params = buildConfigParams().slack ?: [:]
  if (params.channel == null) {
    // We require a channel to be set
    throw new Exception("Missing Slack configuration with buildConfig")
  }

  def color
  if (buildStatus == 'STARTED') {
    color = ''
  } else if (buildStatus == 'SUCCESS') {
    color = 'good'
  } else {
    color = 'danger'
  }

  slackSend([
    channel: params.channel ?: '#cals-dev-info',
    color: color,
    message: "${buildStatus}: Job `<${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>`",
    teamDomain: params.teamDomain ?: 'cals-capra',
    tokenCredentialId: params.tokenCredentialId ?: 'slack-cals-webhook-token',
  ])
}
