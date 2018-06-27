#!/usr/bin/groovy

def call(suffix = null) {
  def tag = "ci-cache"

  if (env.BRANCH_NAME != null) {
    // Avoid possible injections/errors by whitelisting branch name contents
    // The return value is shell-safe
    tag += "-" + env.BRANCH_NAME.replaceAll(/[^a-zA-Z0-9\-_]/, '')
  }

  if (suffix != null) {
    tag += '-' + suffix
  }

  return tag
}
