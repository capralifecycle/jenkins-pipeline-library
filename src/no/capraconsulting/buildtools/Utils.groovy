#!/usr/bin/groovy
package no.capraconsulting.buildtools

/**
 * Get a safe string value of the branch name that can be
 * used in paths, artifact names etc.
 */
static def getSafeBranchName(branchName) {
  return branchName.replaceAll(/[^a-zA-Z0-9\\-_]/, "-")
}

/**
 * Get full Git commit hash.
 */
def getGitCommit() {
  return sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
}

/**
 * Detect the actual Git commit that is used. Jenkins might create a
 * merge commit when doing pr-merge strategy, which we are not
 * interested in as it is only transient.
 */
def getSourceGitCommit() {
  if (env.CHANGE_ID) {
    // TODO: We should only exclude HEAD if the merge commit is
    //  actually done by Jenkins.
    return sh(
      returnStdout: true,
      script: '''
        if git show -s --format=%s HEAD | grep -q '^Merge commit '; then
          git rev-parse HEAD~1
        else
          git rev-parse HEAD
        fi
      ''',
    ).trim()
  } else {
    return getGitCommit()
  }
}

/**
 * Format a timestamp to be used within a path and similar.
 */
static def formatTimestampForPath(Date buildTimestamp) {
  return buildTimestamp.format("yyyyMMdd-HHmmss'z'", TimeZone.getTimeZone("UTC"))
}

/**
 * Generate tag that can be used to identify a build.
 */
def generateLongTag(Date buildTimestamp) {
  def t = formatTimestampForPath(buildTimestamp)
  def branch = getSafeBranchName(env.BRANCH_NAME)
  def build = env.BUILD_NUMBER
  def gitsha = getSourceGitCommit().take(7)

  return "$t-$branch-$build-$gitsha"
}
