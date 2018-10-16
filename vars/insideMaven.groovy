#!/usr/bin/groovy

/**
 * Run inside a Maven container
 *
 * See withMavenSettings for how to use Maven settings
 */
def call(Map args = [:], body) {
  def tool = args.avoidAlpine == null || !args.avoidAlpine
    ? 'maven:3-jdk-8-alpine'
    : 'maven:3-jdk-8-debian'

  def insideArgs = args.insideArgs ?: ''

  def img = docker.image("923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/$tool")
  img.pull() // ensure we have latest version
  img.inside(insideArgs) {
    withMavenSettings {
      body()
    }
  }
}
