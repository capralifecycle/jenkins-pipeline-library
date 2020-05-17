package no.capraconsulting.buildtools.cdk

// Not to be used outside the library.
static def _getFunctionRegion(String functionArn) {
  // Example value: arn:aws:lambda:eu-west-1:112233445566:function:my-function
  // Result: eu-west-1
  def result = (functionArn =~ "arn:aws:lambda:([^\\:]+):.+")
  if (!result.matches()) {
    throw new RuntimeException("Could not extract region from " + functionArn)
  }
  return result.group(1)
}
