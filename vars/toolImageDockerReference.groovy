#!/usr/bin/groovy

/**
 * Return a full reference to the docker iamge for a specific tool image
 * from https://github.com/capralifecycle/buildtools-images
 *
 * The provided name can include a tag, e.g. node:12-alpine
 */
def call(name) {
  "923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/$name"
}
