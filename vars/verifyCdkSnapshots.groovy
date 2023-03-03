#!/usr/bin/groovy

/**
 * Verify CDK snapshots.
 *
 * Example usage:
 *
 *  stage("Verify CDK snapshots") {
 *    verifyCdkSnapshots()
 *  }
 *
 */
def call(Map args = [:]) {
  def snapshotCommand = args.snapshotCommand ?: "npm run snapshots"
  def snapshotDir = args.snapshotDir ?: "__snapshots__"
  echo "Verifying CDK snapshots"
  sh """
    snapshot_dir="${snapshotDir}"
    if [ ! -d "\$snapshot_dir" ]; then
      echo "Missing expected snapshot folder: \$snapshot_dir"
      exit 1
    fi
    ${snapshotCommand}
    git status
    git add -N "\$snapshot_dir"
    git diff --exit-code
  """
}
