#!/usr/bin/groovy

/**
 * Send message to Slack and inherit the team domain and
 * credentials from outer scope (see buildConfig) if available,
 * or use default values.
 *
 * This helps us to avoid leaking too many references into all the
 * different pipelines.
 */
def call(Map args = [:]) {
  def globalParams = buildConfigParams().slack ?: [:]

  def channel = args.channel ?: globalParams.channel
  if (channel == null) {
    // We require a channel to be set
    throw new Exception("Missing Slack configuration with buildConfig or explicit channel")
  }

  if (args.message == null) {
    throw new Exception("Message expected")
  }

  // Workaround for name change of Liflig workspace, so we
  // do not have to change every use-site location of this.
  def teamDomain = args.teamDomain ?: globalParams.teamDomain ?: 'liflig'
  if (teamDomain == 'cals-capra') {
    teamDomain = 'liflig'
  }

  slackSend(
    // Parameters passed can be overriden by args
    [
      channel: channel,
      teamDomain: teamDomain,
      tokenCredentialId: args.tokenCredentialId
        ?: globalParams.tokenCredentialId
        ?: 'slack-cals-webhook-token',
    ] + args
  )
}
