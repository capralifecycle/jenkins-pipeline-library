#!/usr/bin/groovy

/**
 * Setup credentials helper for Git with the user Jenkins uses so that
 * normal git commands will work when using a https endpoint with GitHub.
 *
 * See this issue for future improvements (native pipeline support):
 * https://issues.jenkins-ci.org/browse/JENKINS-28335
 */
def call(args = [:], body) {
  def credentialsId = args.credentialsId ?: 'github-calsci-token-with-user'

  // When running inside Docker, we might not have a stable HOME directory.
  // Setting HOME will not be honored, but Git also uses other directories.
  // See https://git-scm.com/docs/git-config for details.
  def extraEnv = []
  if (!env.XDG_CACHE_HOME) {
    // The socket reference to credentials cache daemon is stored inside
    // this cache directory.
    extraEnv << 'XDG_CACHE_HOME=/home/jenkins/.cache'
  }
  if (!env.XDG_CONFIG_HOME) {
    // If $HOME/.gitconfig actually exists, it will be used as default source
    extraEnv << 'XDG_CONFIG_HOME=/home/jenkins/.config'
  }

  withEnv(extraEnv) {
    // Ensure the config file exist, so that it will be used as a fallback.
    sh 'mkdir -p "$XDG_CONFIG_HOME/git" && touch "$XDG_CONFIG_HOME/git/config"'

    // Enable the credentials cache.
    // Note: This will store credentials in memory and will be available
    // to any concurrent jobs running on the same slave. However, all jobs really
    // have access to this anyways if they want.
    // We use global config so that we can use this block to also clone other
    // repositories without having an existing Git context.
    sh 'git config --global credential.helper cache'

    def username
    try {
      withCredentials([
        usernamePassword(
          credentialsId: credentialsId,
          passwordVariable: 'GIT_PASSWORD',
          usernameVariable: 'GIT_USERNAME'
        )
      ]) {
        username = env.GIT_USERNAME

        // TODO: Don't split+join username when https://issues.jenkins-ci.org/browse/JENKINS-44860 is fixed
        println "Setting up credentials for GitHub user ${env.GIT_USERNAME.split('').join('_')}"

        // Register the credentials to the credential helper.
        // If the credential helper daemon is not already running, this will also spawn the daemon.
        // See https://git-scm.com/docs/git-credential-cache--daemon
        sh "echo \"protocol=https\nhost=github.com\nusername=\$GIT_USERNAME\npassword=\$GIT_PASSWORD\n\" | git credential approve"
      }

      // Execute outside withCredentials to avoid having environment variables
      // that is expected not to be used.
      body()
    } finally {
      try {
        // Remove the credential from the cache.
        // The credentials having the properties given here will be removed.
        if (username != null) {
          sh "echo \"protocol=https\nhost=github.com\nusername=${username}\n\" | git credential reject"
        }
      } catch (e) {
        // Ignored
      }
    }
  }
}
