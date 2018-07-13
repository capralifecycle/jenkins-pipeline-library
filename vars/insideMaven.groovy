#!/usr/bin/groovy

/**
 * Run inside a Maven container
 *
 * See withMavenSettings for how to use Maven settings
 */
def call(body) {
  docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/maven:3-alpine').inside('-v cals-m2-cache:/home/jenkins/.m2') {
    withMavenSettings {
      body()
    }
  }
}
