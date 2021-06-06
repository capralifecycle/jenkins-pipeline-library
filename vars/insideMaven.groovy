#!/usr/bin/groovy

/**
 * Run inside a Maven container
 *
 * See withMavenSettings for how to use Maven settings
 */
def call(Map args = [:], body) {
  // Prefer the use of providing the version, e.g.
  // [ version: '3-jdk-11-alpine' ]
  def tool = args.version != null
    ? "maven:${args.version}"
    : args.avoidAlpine == null || !args.avoidAlpine
    ? 'maven:3-jdk-8-alpine'
    : 'maven:3-jdk-8-debian'

  def insideArgs = args.insideArgs ?: ''

  def img = docker.image(toolImageDockerReference(tool))
  img.pull() // ensure we have latest version
  img.inside(insideArgs) {
    withMavenSettings {
      body()
    }
  }
}
