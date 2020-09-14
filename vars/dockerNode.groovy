#!/usr/bin/groovy

def call(Map args = [:], body) {
  def label = args.label == null
    ? 'docker'
    : args.label

  node(label) {
    debugRunningStuff()

    // We wipe the directory both before and after using the node. The reason
    // for wiping is to enforce consistency between builds and because we
    // might build on different slaves randomly. It also avoids filling up
    // disk capacity. Clean before in case a previous job failed and is not
    // cleaned.
    deleteDir()

    // Implicitly uses role provided to slave container to get authorization
    // to use ECR for pulling and pushing our own Docker images.
    sh '(set +x; eval $(aws ecr get-login --no-include-email --region eu-central-1))'

    body()

    deleteDir()

    debugRunningStuff()
  }
}

/**
 * Give us some insight into what is currently happening on the slave,
 * helping us solve https://jira.capraconsulting.no/browse/CALS-318
 */
def debugRunningStuff() {
  echo "--- BEGIN dockerNode DEBUG ---"

  sh 'docker ps'

  // This will only show what runs in the slave container, not the
  // wrapper image. So processes running in other docker containers
  // will not be shown.
  sh 'ps aux'

  echo "--- END dockerNode DEBUG ---"
}
