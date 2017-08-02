#!/usr/bin/groovy

def call(Map args = [:]) {
  def buildStatus = args.buildStatus ?: 'SUCCESS'

  def color
  if (buildStatus == 'STARTED') {
    color = ''
  } else if (buildStatus == 'SUCCESS') {
    color = 'good'
  } else {
    color = 'danger'
  }

  slackSend([
    channel: args.channel ?: '#cals-dev-info',
    color: color,
    message: "${buildStatus}: Job `<${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>`",
    teamDomain: args.teamDomain ?: 'cals-capra',
    tokenCredentialId: args.tokenCredentialId ?: 'slack-cals-webhook-token',
  ])
}
