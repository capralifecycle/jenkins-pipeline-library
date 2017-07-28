#!/usr/bin/groovy

def call(body) {
  sh """
    git config user.name jenkins
    git config user.email it@capraconsulting.no
    """
  body()
}
