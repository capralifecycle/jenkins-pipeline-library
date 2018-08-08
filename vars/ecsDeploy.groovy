#!/usr/bin/groovy

def call(args) {
  // AWS_CONTAINER_CREDENTIALS_RELATIVE_URI contains a variable
  // used by awscli to pick up the ECS task role instead of using
  // instance role. This lets us use the task role of the jenkins slave.
  def img = docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/ecs-deploy')
  img.pull() // ensure we have latest version
  img.inside("-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI=${env.AWS_CONTAINER_CREDENTIALS_RELATIVE_URI}") {
    sh "/ecs-deploy $args"
  }
}
