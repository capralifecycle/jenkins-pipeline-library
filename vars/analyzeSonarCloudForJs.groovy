#!/usr/bin/groovy

/**
 * Analyze a Node.js project using SonarScanner and upload to SonarCloud.
 *
 * This is a high-level facade that adheres to our preferred setup.
 *
 * See analyzeSonarCloud for runtime requirements.
 *
 * Setup instructions:
 *   1) Create project in sonarcloud.io
 *   2) Add to .gitignore:
 *      /coverage/
 *      /test-report.xml
 *   3) Modify package.json and ensure jest is called with `--coverage`
 *      E.g. "jest --coverage src"
 *   4) Ensure the image being used in the build includes sonar-scanner
 *      See e.g https://github.com/capralifecycle/buildtools-snippets/tree/master/tools/sonar-scanner
 *      (at least for TypeScript the sonar-scanner needs the node executable)
 *   5) Add to Jenkinsfile (outside a stage)
 *      analyzeSonarCloudForJs([
 *        // Modify next lines and remove this comment.
 *        'sonar.organization': 'capralifecycle',
 *        'sonar.projectKey': 'capralifecycle_my-project',
 *      ])
 */
def call(Map<String, String> params, Map<String, String> options = [:]) {
  p = [
    'sonar.typescript.lcov.reportPaths': 'coverage/lcov.info',
    'sonar.javascript.lcov.reportPaths': 'coverage/lcov.info',
    'sonar.sources': 'src',
    'sonar.tests': 'src',
    'sonar.test.inclusions': [
      'src/**/*.spec.js',
      'src/**/*.spec.jsx',
      'src/**/*.spec.ts',
      'src/**/*.spec.tsx',
      'src/**/*.test.js',
      'src/**/*.test.jsx',
      'src/**/*.test.ts',
      'src/**/*.test.tsx',
    ].join(','),
  ] + params

  if (fileExists('test-report.xml')) {
    p['sonar.testExecutionReportPaths'] = 'test-report.xml'
  } else {
    echo 'WARN: test-report.xml does not exist. Test output not correctly set up?'
  }

  analyzeSonarCloud(p, options)
}
