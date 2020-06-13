#!/usr/bin/groovy

/**
 * Run inside a image from https://github.com/capralifecycle/buildtools-images
 *
 * The provided name can include a tag, e.g. node:12-alpine
 */
def call(name, Map args = [:], body) {
  // Passing AWS_CONTAINER_CREDENTIALS_RELATIVE_URI allows the processes
  // to access the IAM role for the Jenkins slave, similar as processes
  // running outside the tool image.
  def insideArgs = "-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"
  if (args.insideArgs) {
    insideArgs += " " + args.insideArgs
  }

  def img = docker.image(toolImageDockerReference(name))
  img.pull() // ensure we have latest version
  img.inside(insideArgs) {
    body()
  }
}
