#!/usr/bin/groovy

/**
 * Provide environment variables used for project and authentication
 * to upload release artifacts to Sentry.
 *
 * For more details:
 * https://docs.sentry.io/cli/configuration/#configuration-values
 */
def call(Map args, Closure body) {
  if (!args.containsKey("project")) {
    throw new RuntimeException("Missing project as arg")
  }

  withEnv(["SENTRY_ORG=liflig", "SENTRY_PROJECT=${args.project}"]) {
    withCredentials([
      string(
        credentialsId: 'sentry-liflig-release-token',
        variable: 'SENTRY_AUTH_TOKEN'
      )
    ]) {
      body()
    }
  }
}
