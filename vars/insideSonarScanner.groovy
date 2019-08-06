#!/usr/bin/groovy

/**
 * Run inside a container having sonar-scanner installed.
 *
 * See withMavenSettings for how to use Maven settings
 */
def call(body) {
  def img = docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/sonar-scanner')
  img.pull() // ensure we have latest version
  img.inside {
    body()
  }
}
