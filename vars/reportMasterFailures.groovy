#!/usr/bin/groovy

/**
 * Run the provided body and send a Slack notification if the
 * execution fails and we are on the master branch.
 *
 * Parameters:
 *   - channel (e.g. #my-channel) (required)
 *   - what (e.g. Build)
 */
def call(Map args, Closure body) {
  if (!args.containsKey("channel")) {
    throw new RuntimeException("Missing channel as arg")
  }

  def prefix = args.containsKey("what") ? "${args.what} part of " : ""

  try {
    body()
  } catch (e) {
    if (env.BRANCH_NAME == "master") {
      slackNotify(
        color: "danger",
        channel: args.channel,
        message: "$prefix<${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]> failed",
      )
    }
    throw e
  }
}
