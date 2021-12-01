#!/usr/bin/groovy

/**
 * Analyze a Maven project using SonarScanner and upload to SonarCloud.
 *
 * This is a high-level facade that adheres to our preferred setup.
 *
 * See analyzeSonarCloud for runtime requirements.
 *
 * Setup instructions:
 *   1) Create project in sonarcloud.io
 *   2) Add to project.build.plugins in pom.xml (ensure you check for latest version):
 *      <!-- Coverage for SonarCloud -->
 *      <plugin>
 *        <groupId>org.jacoco</groupId>
 *        <artifactId>jacoco-maven-plugin</artifactId>
 *        <version>0.8.7</version>
 *        <executions>
 *          <execution>
 *            <id>jacoco-initialize</id>
 *            <goals>
 *              <goal>prepare-agent</goal>
 *            </goals>
 *          </execution>
 *          <execution>
 *            <id>jacoco-site</id>
 *            <phase>package</phase> <!-- Kjør automatisk ved package. Du kan også kjøre site selv, eller bruke en annen phase som verify -->
 *            <goals>
 *              <goal>report</goal>
 *            </goals>
 *          </execution>
 *        </executions>
 *      </plugin>
 *   3) Ensure the image being used in the build includes sonar-scanner
 *      See e.g. https://github.com/capralifecycle/buildtools-snippets/tree/master/tools/sonar-scanner
 *      or use insideSonarScanner
 *   4) Add to Jenkinsfile (outside a stage)
 *      analyzeSonarCloudForMaven([
 *        // Modify next lines and remove this comment.
 *        'sonar.organization': 'capralifecycle',
 *        'sonar.projectKey': 'capralifecycle_my-project',
 *      ])
 */
def call(Map<String, String> params, Map<String, String> options = [:]) {
  analyzeSonarCloud(
    [
      'sonar.coverage.jacoco.xmlReportPaths': 'target/site/jacoco/jacoco.xml',
      'sonar.sources': 'src/main',
      'sonar.tests': 'src/test',
      'sonar.exclusions': 'src/main/resources/**',
      'sonar.test.exclusions': 'src/test/resources/**',
      'sonar.java.binaries': 'target/classes',
      'sonar.java.libraries': 'target/*.jar',
      'sonar.java.test.libraries': 'target/*.jar',
    ] + params,
    options,
  )
}
