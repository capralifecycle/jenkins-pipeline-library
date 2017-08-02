#!/usr/bin/groovy

class GlobalStore {
  static Map data = [:]
}

/**
 * Generic configuration holder for jobs
 *
 * Can be used to retrieve configuration set in buildConfig
 */
def call(Map parameters = null) {
  // As setter
  if (parameters != null) {
    GlobalStore.data = parameters
  }

  // Always return current value
  return GlobalStore.data
}
