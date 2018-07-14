#!/usr/bin/groovy

/**
 * Run inside a Maven container
 *
 * See withMavenSettings for how to use Maven settings
 */
def call(Map args = [:], body) {
  def tool = args.avoidAlpine == null || !args.avoidAlpine
    ? 'maven:3-alpine'
    : 'maven-debian:3.5.2-slim'

  def insideArgs = '-v cals-m2-cache:/home/jenkins/.m2'
  if (args.insideArgs != null) {
    insideArgs += ' ' + args.insideArgs
  }

  docker.image("923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/$tool").inside(insideArgs) {
    withMavenSettings {
      body()
    }
  }
}
