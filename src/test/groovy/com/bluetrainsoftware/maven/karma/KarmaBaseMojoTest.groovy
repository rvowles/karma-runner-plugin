package com.bluetrainsoftware.maven.karma

import org.apache.maven.artifact.Artifact
import org.apache.maven.model.Build
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.project.MavenProject
import org.junit.Before
import org.junit.Test

import static org.mockito.Matchers.anyString
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

/**

 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
class KarmaBaseMojoTest {

  private Log log
  private KarmaBaseMojo karma

  class DummyKarmaRunMojo extends KarmaBaseMojo {
    @Override
    protected boolean runKarma(File finalKarmaConfigurationFile) {
      return true
    }
  }

  @Before
  public void before() {
    log = mock(Log)

    karma = new DummyKarmaRunMojo()

    karma.setLog(log)
  }

  @Test
  public void noConfigExecutionProducesSimpleWarningAndGracefullyExists() {
    karma.execute()

    verify(log).warn(anyString())
  }

  @Test(expected = MojoExecutionException)
  public void overrideDirectoryDoesNotExist() {
    MavenProject project = mock(MavenProject)

    Set<Artifact> artifacts = [
      [getArtifactId: { "eggs" }, getGroupId: { "overrideTest" }, getType: { "war" }] as Artifact,
    ] as Set<Artifact>

    System.setProperty("karma.eggs", "monkeymonkeymonkey")

    when(project.getArtifacts()).thenReturn(artifacts)

    karma.project = project
    karma.templateFile = "src/test/resources/karma.test.js"

    karma.execute()

  }

  private File setBaseDir(MavenProject project) {
    File thisDir = new File(".")

    thisDir = new File(thisDir.absolutePath.substring(0, thisDir.absolutePath.length() - 2))
    when(project.getBasedir()).thenReturn(thisDir)

    return thisDir
  }

  @Test
  public void whopper() {
    Build build = mock(Build)
    MavenProject project = mock(MavenProject)

    Set<Artifact> artifacts = [
      [getArtifactId: { return "sausage" }, getGroupId: { return "breakfast" }, getType: { return "war" }, getFile: { return new File("src/test/resources/sausages.war") }] as Artifact,
      [getArtifactId: { return "fried-tomato" }, getGroupId: { return "breakfast" }, getType: { return "war" }, getFile: { return new File("src/test/resources/toms.war") }] as Artifact,
      [getArtifactId: { "eggs" }, getGroupId: { "overrideTest" }, getType: { "war" }] as Artifact,
      [getArtifactId: { return "bacon" }, getGroupId: { return "breakfast" }, getType: { return "jar" }] as Artifact
    ] as Set<Artifact>

    System.setProperty("karma.eggs", "src/test/resources")


    when(project.getBuild()).thenReturn(build)

    File baseDir = setBaseDir(project)
    when(build.getDirectory()).thenReturn(new File(baseDir, "target").absolutePath)


    when(project.getArtifacts()).thenReturn(artifacts)

    karma.project = project
    karma.templateFile = "src/test/resources/karma.test.js"
    karma.localisationFileName = "src/test/resources/karma.extra.js"
    karma.execute()

    assert new File("target/karma-runner.cfg.js").text.trim() == """var files = [
  'karma/sausage/angular/uoa/**/*.js',
  'karma/fried-tomato/angular/**/*.js',
  'src/test/resources/angular/**/*.js',
  'src/main/webapp/angular/**'
];

var somenonsense = 1;"""

    assert new File("target/karma/sausage/subdir/subdir.js").exists()
    assert new File("target/karma/sausage/x.js").exists()
    assert new File("target/karma/sausage/y.js").exists()
    assert new File("target/karma/fried-tomato/toms.js").exists()

    karma.execute() // should execute twice ok
  }
}
