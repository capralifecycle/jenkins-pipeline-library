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
    sh '(set +x; eval $(aws ecr get-login --region eu-central-1))'

    docker.withRegistry('https://923402097046.dkr.ecr.eu-central-1.amazonaws.com/') {
      body()
    }

    deleteDir()
  }
}
