#!/usr/bin/groovy

import no.capraconsulting.buildtools.sonarcloud.SonarCloud

def call(Map<String, String> params, Map<String, String> options = [:]) {
  def sonarCloud = new SonarCloud()
  sonarCloud.runSonarScanner(params, options)
}
