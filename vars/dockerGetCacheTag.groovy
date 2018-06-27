#!/usr/bin/groovy

def call(suffix = null) {
  // Avoid possible injections/errors by whitelisting branch name contents
  // The return value is shell-safe
  def tag = "ci-cache-$BRANCH_NAME".replaceAll(/[^a-zA-Z0-9\-_]/, '')

  if (suffix != null) {
    tag += '-' + suffix
  }

  return tag
}
