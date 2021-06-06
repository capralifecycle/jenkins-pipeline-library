#!/usr/bin/groovy

/**
 * Run inside a container having sonar-scanner installed.
 *
 * See withMavenSettings for how to use Maven settings
 */
def call(body) {
  def img = docker.image(toolImageDockerReference('sonar-scanner'))
  img.pull() // ensure we have latest version
  img.inside {
    body()
  }
}
