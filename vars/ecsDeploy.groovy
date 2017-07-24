#!/usr/bin/groovy

def call(body) {
  // AWS_CONTAINER_CREDENTIALS_RELATIVE_URI contains a variable
  // used by awscli to pick up the ECS task role instead of using
  // instance role. This lets us use the task role of the jenkins slave.
  docker.image('buildtools/ecs-deploy').inside("-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI=${env.AWS_CONTAINER_CREDENTIALS_RELATIVE_URI}") {
    sh "/ecs-deploy $args"
  }
}
