#!/usr/bin/groovy

def call(body) {
  node('docker') {
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
  }
}
