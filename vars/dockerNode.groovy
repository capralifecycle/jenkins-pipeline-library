#!/usr/bin/groovy

def call(Map args = [:], body) {
  def label = args.label == null
    ? 'docker'
    : args.label

  node(label) {
    // We wipe the directory both before and after using the node. The reason
    // for wiping is to enforce consistency between builds and because we
    // might build on different slaves randomly. It also avoids filling up
    // disk capacity. Clean before in case a previous job failed and is not
    // cleaned.
    deleteDir()

    // Cleanup any previous Docker credentials to avoid expired tokens
    // for public repos. We do this before and after the body to catch
    // all scenarios.
    sh 'rm -f ~/.docker/config.json'

    // Implicitly uses role provided to slave container to get authorization
    // to use ECR for pulling and pushing our own Docker images.
    sh '(set +x; eval $(aws ecr get-login --no-include-email --region eu-central-1))'

    // Authenticate to ECR Public to increase rate limit
    sh '(set +x; aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws)'

    withCredentials([
      usernamePassword(
        credentialsId: 'dockerhub-token-with-user',
        passwordVariable: 'DOCKERHUB_TOKEN',
        usernameVariable: 'DOCKERHUB_USERNAME'
      ),
    ]) {
      sh '(set +x; echo "$DOCKERHUB_TOKEN" | docker login --username "$DOCKERHUB_USERNAME" --password-stdin)'
    }

    try {
      body()
    } finally {
      deleteDir()
      sh 'rm -f ~/.docker/config.json'
    }
  }
}
