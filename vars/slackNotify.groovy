#!/usr/bin/groovy

def call(Map args = [:]) {
  def params = buildConfigParams().slack ?: [:]
  if (params.channel == null) {
    // We require a channel to be set
    throw new Exception("Missing Slack configuration with buildConfig")
  }

  if (args.message == null) {
    throw new Exception("Message expected")
  }

  slackSend(
    // Parameters passed can be overriden by args
    [
      channel: params.channel ?: '#cals-dev-info',
      teamDomain: params.teamDomain ?: 'cals-capra',
      tokenCredentialId: params.tokenCredentialId ?: 'slack-cals-webhook-token',
    ] + args
  )
}
