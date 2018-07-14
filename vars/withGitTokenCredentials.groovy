#!/usr/bin/groovy

/**
 * Setup credentials helper for Git with the user Jenkins uses so that
 * normal git commands will work when using a https endpoint with GitHub.
 *
 * Currently the credentials will work also after leaving this block.
 * However, it should not be relied on and is subject to change with
 * future improvements.
 *
 * See also this issue for future improvements:
 * https://issues.jenkins-ci.org/browse/JENKINS-28335
 */
def call(body) {
  // Enable the credentials cache.
  // Note: This will store credentials in memory and will be available
  // to concurrent jobs running on the same slave. However, all jobs really
  // have access to this anyways if they want.
  sh 'git config --global credential.helper cache'

  withCredentials([
    usernamePassword(
      credentialsId: 'github-calsci-token-with-user',
      passwordVariable: 'GIT_PASSWORD',
      usernameVariable: 'GIT_USERNAME'
    )
  ]) {
    // TODO: Don't split+join username when https://issues.jenkins-ci.org/browse/JENKINS-44860 is fixed
    println "Setting up credentials for GitHub user ${env.GIT_USERNAME.split('').join('_')}"
    sh "echo \"protocol=https\nhost=github.com\nusername=\$GIT_USERNAME\npassword=\$GIT_PASSWORD\n\" | git credential approve"
  }

  // Execute outside withCredentials to avoid having environment variables
  // that is expected not to be used.
  body()
}
