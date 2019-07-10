#!/usr/bin/groovy

/**
 * Run inside a image from https://github.com/capralifecycle/buildtools-images
 *
 * The provided name can include a tag, e.g. node:12-alpine
 */
def call(name, Map args = [:], body) {
  def insideArgs = args.insideArgs ?: ''

  def img = docker.image("923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/$name")
  img.pull() // ensure we have latest version
  img.inside(insideArgs) {
    body()
  }
}
