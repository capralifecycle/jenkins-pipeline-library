#!/usr/bin/groovy

/**
 * Deploy a service using Serverless.
 */
def call(deployIamRole, serverlessArgs) {
  def img = docker.image(toolImageDockerReference('serverless'))
  img.pull() // ensure we have latest version

  withAwsRole(deployIamRole) {
    img.inside {
      // TODO: Not sure why we need the 'serverless config credentials' command.
      // Might investigate later.

      sh '''
        #!/bin/bash
        set +x

        # Must set HOME as it is not set and thus serverless will default to root dir which the user does not have read/write access to
        export HOME=$(pwd)

        serverless config credentials --provider aws --key $AWS_ACCESS_KEY_ID --secret $AWS_SECRET_ACCESS_KEY ''' + serverlessArgs + '''
        serverless deploy ''' + serverlessArgs + ''' '''
    }
  }
}
