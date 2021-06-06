#!/usr/bin/groovy

/**
 * Return a full reference to the docker iamge for a specific tool image
 * from https://github.com/capralifecycle/buildtools-images
 *
 * The provided name can include a tag, e.g. node:12-alpine
 */
def call(name) {
  "public.ecr.aws/z8l5l4v4/buildtools/tool/$name"
}
