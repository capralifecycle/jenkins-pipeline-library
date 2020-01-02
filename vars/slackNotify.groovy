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
  
  // Workaround for name change of Liflig workspace, so we
  // do not have to change every use-site location of this.
  def teamDomain = params.teamDomain ?: 'liflig'
  if (teamDomain == 'cals-capra') {
    teamDomain = 'liflig'
  }

  slackSend(
    // Parameters passed can be overriden by args
    [
      channel: params.channel ?: '#cals-dev-info',
      teamDomain: teamDomain,
      tokenCredentialId: params.tokenCredentialId ?: 'slack-cals-webhook-token',
    ] + args
  )
}
