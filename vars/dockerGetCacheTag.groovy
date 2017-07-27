#!/usr/bin/groovy

def call(body) {
  // Avoid possible injections/errors by whitelisting branch name contents
  // The return value is shell-safe
  return "ci-cache-$BRANCH_NAME".replaceAll(/[^a-zA-Z0-9\-_]/, '')
}
