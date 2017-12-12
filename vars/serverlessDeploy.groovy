#!/usr/bin/groovy

/**
 * Deploy a service using Serverless.
 */
def call(args) {
  // AWS_CONTAINER_CREDENTIALS_RELATIVE_URI contains a variable
  // used by awscli to pick up the ECS task role instead of using
  // instance role. This lets us use the task role of the jenkins slave.
  docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/serverless').inside("-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI=${env.AWS_CONTAINER_CREDENTIALS_RELATIVE_URI}") {
    def accessKey = sh(script: 'curl 169.254.170.2$AWS_CONTAINER_CREDENTIALS_RELATIVE_URI | jq .AccessKeyId', returnStdout: true)
    def secretKey = sh(script: 'curl 169.254.170.2$AWS_CONTAINER_CREDENTIALS_RELATIVE_URI | jq .SecretAccessKey', returnStdout: true)
    sh "serverless deploy $args --provider aws --key $accessKey --secret $secretKey"
  }
}
