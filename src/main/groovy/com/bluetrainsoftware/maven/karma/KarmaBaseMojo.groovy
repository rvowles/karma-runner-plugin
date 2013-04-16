package com.bluetrainsoftware.maven.karma

import groovy.text.SimpleTemplateEngine
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.factory.ArtifactFactory
import org.apache.maven.artifact.metadata.ArtifactMetadataSource
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**

 * author: Richard Vowles - http://gplus.to/RichardVowles
 */

class KarmaBaseMojo extends AbstractMojo {
  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  /**
   * expected in ${project.baseDir}
   */
  @Parameter(property = "run.templateFile")
  protected String templateFile = "karma-template.cfg.js";

  /**
   * this goes in ${project.build.directory}/
   */
  @Parameter(property = "run.karmaFile")
  protected String karmaFile = "karma-runner.cfg.js";

  @Parameter(property = "run.karmaLocalisation", defaultValue = "\${user.home}/.karma/karma.local.js")
  protected String localisationFileName

  Map<String, File> karmaDirectories = new HashMap<>()

  private void karmaDirectory(Artifact artifact, File directory) {
    karmaDirectories[artifact.artifactId.replace('-', '_')] = directory
  }

  @Override
  void execute() throws MojoExecutionException, MojoFailureException {
    File karmaTemplate = new File(templateFile)

    if (!karmaTemplate.exists()) {
      getLog().warn("karma: no ${templateFile} to use, ignoring.")

      return
    }

    project.getArtifacts().each { Artifact artifact ->

      if (artifact.type == 'war') {
        String expectedProperty = "karma." + artifact.artifactId

        String override = System.getProperty(expectedProperty)

        if (override != null) {
          File overrideFile = new File(override)

          if (!overrideFile.exists())
            throw new MojoExecutionException("Cannot find override directory for ${artifact.groupId}:${artifact.artifactId} system property -D${expectedProperty}")

          karmaDirectory(artifact, overrideFile)
        } else {
          extractWar(artifact)
        }
      }
    }

    def karmaEngine = new SimpleTemplateEngine()
    def binding = [karma:[:]]

    karmaDirectories.each { key, file -> binding.karma[key] = file.absolutePath.substring(project.basedir.absolutePath.length() + 1)}

    FileWriter writer = new FileWriter(new File(project.build.directory, karmaFile))
    karmaEngine.createTemplate(karmaTemplate).make(binding).writeTo(writer)

    // copy any local extensions file in. Because the config file, this allows overriding of settings from the main file as well

    // spelt this way to annoy Americans
    if (localisationFileName != null) {
      File localalisationFile = new File(localisationFileName)

      if (localalisationFile.exists()) {
        IOUtils.copy(new FileReader(localalisationFile), writer)
      } else {
        getLog().warn("karma: localisation file specified ${localisationFileName} but does not exist")
      }
    }

    writer.close()

    getLog().info("karma: ${karmaFile} generated, starting Karma")
  }

  void extractWar(Artifact artifact) {
    File exportDirectory = new File(project.build.directory, "karma" + File.separator + artifact.artifactId)

    // check if it exists, delete it if so
    if (exportDirectory.exists()) {
      FileUtils.deleteDirectory(exportDirectory)
    }

    exportDirectory.mkdirs()

    JarFile war = new JarFile(artifact.file)

    getLog().info("karma: extracting ${artifact.groupId}:${artifact.artifactId}")

    war.entries().each { JarEntry next ->
      if (!next.name.endsWith(".jar") && !next.name.startsWith("META-INF") && !next.name.startsWith("WEB-INF/classes") && !next.name.startsWith("EXT-INF") ) {

        File kFile = new File(exportDirectory, next.name)

        if (next.isDirectory())
          kFile.mkdirs()
        else {
          // ensure directories exist
          if (kFile.parentFile) {
            kFile.parentFile.mkdirs()
          }

          IOUtils.copy(war.getInputStream(next), new FileOutputStream(kFile))
        }
      }
    }

    war.close()

    karmaDirectory(artifact, exportDirectory)
  }
}
