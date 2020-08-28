#!/usr/bin/groovy

/**
 * Run the provided body and send a Slack notification if the
 * execution fails and we are on the master branch.
 */
def call(Map args, Closure body) {
  if (!args.containsKey("channel")) {
    throw new RuntimeException("Missing channel as arg")
  }
  if (!args.containsKey("what")) {
    throw new RuntimeException("Missing what as arg")
  }

  try {
    body()
  } catch (e) {
    if (env.BRANCH_NAME == "master") {
      slackNotify(
        color: "danger",
        channel: channel,
        message: "$what part of <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]> failed",
      )
    }
    throw e
  }
}
