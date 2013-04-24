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
import org.codehaus.plexus.util.StringUtils

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

  Map<String, String> karmaDirectories = new HashMap<>()

  protected void karmaDirectory(Artifact artifact, String directory) {
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
      String expectedProperty = "karma." + artifact.artifactId

      String override = System.getProperty(expectedProperty)

      if (override != null) {
        File overrideFile = new File(override)

        if (!overrideFile.exists())
          throw new MojoExecutionException("Cannot find override directory for ${artifact.groupId}:${artifact.artifactId} system property -D${expectedProperty}")

        karmaDirectory(artifact, override)
      } else if (artifact.type == 'war') {
        extractWar(artifact)
      } else if (artifact.type == 'jar') { // could be a Servlet 3.0 jar with /META-INF/resources
        extractJar(artifact)
      }
    }

    def karmaEngine = new SimpleTemplateEngine()
    def binding = [karma:karmaDirectories]

    File finalKarmaConfigurationFile = new File(project.build.directory, karmaFile)

    FileWriter writer = new FileWriter(finalKarmaConfigurationFile)
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

    runKarma(finalKarmaConfigurationFile)
  }

  // some of this code taken from maven-karma-plugin, StartMojo.java in
  // https://github.com/karma-runner/maven-karma-plugin

  protected boolean runKarma(File finalKarmaConfigurationFile) {
    Process karma = createKarmaProcess(finalKarmaConfigurationFile)

    BufferedReader karmaOutputReader = null;
    try {
      karmaOutputReader = new BufferedReader(new InputStreamReader(karma.inputStream))

      for (String line = karmaOutputReader.readLine(); line != null; line = karmaOutputReader.readLine()) {
        getLog().info("karma: " + line);
      }

      return (karma.waitFor() == 0);

    } catch (IOException e) {
      throw new MojoExecutionException("There was an error reading the output from Karma.", e);

    } catch (InterruptedException e) {
      throw new MojoExecutionException("The Karma process was interrupted.", e);

    } finally {
      karmaOutputReader.close()
    }
  }

  void extractWar(Artifact artifact) {
    File exportDirectory = createExportDirectory(artifact)

    JarFile war = new JarFile(artifact.file)

    getLog().info("karma: extracting ${artifact.groupId}:${artifact.artifactId}")

    war.entries().each { JarEntry next ->
      if (!next.name.endsWith(".jar") && !next.name.startsWith("META-INF") && !next.name.startsWith("WEB-INF/classes") && !next.name.startsWith("EXT-INF") ) {

        exportFile(war, next, exportDirectory, next.name)

      }
    }

    war.close()

    karmaDirectory(artifact, exportDirectory.absolutePath.substring(project.build.directory.length() + 1))
  }

  protected void exportFile(JarFile jar, JarEntry next, File exportDirectory, String name) {
    File kFile = new File(exportDirectory, name)

    if (next.isDirectory())
      kFile.mkdirs()
    else {
      // ensure directories exist
      if (kFile.parentFile) {
        kFile.parentFile.mkdirs()
      }

      IOUtils.copy(jar.getInputStream(next), new FileOutputStream(kFile))
    }
  }

  protected File createExportDirectory(Artifact artifact) {
    File exportDirectory = new File(project.build.directory, "karma" + File.separator + artifact.artifactId)

    // check if it exists, delete it if so
    if (exportDirectory.exists()) {
      FileUtils.deleteDirectory(exportDirectory)
    }

    exportDirectory.mkdirs()

    return exportDirectory
  }

  public static final SERVLET3_OFFSET = "META-INF/resources"

  void extractJar(Artifact artifact) {
    JarFile jar = new JarFile(artifact.file)

    if (jar.getEntry(SERVLET3_OFFSET)) {
      File exportDirectory = createExportDirectory(artifact)

      jar.entries().each { JarEntry file ->
        if (file.name.startsWith(SERVLET3_OFFSET) && !file.name.startsWith("META-INF/resources/WEB-INF/classes")) {
          exportFile(jar, file, exportDirectory, file.name.substring(SERVLET3_OFFSET.length()))
        }
      }

      karmaDirectory(artifact, exportDirectory.absolutePath.substring(project.build.directory.length() + 1))
    }

    jar.close()
  }

  protected Process createKarmaProcess(File configFile) throws MojoExecutionException {

    ProcessBuilder builder

    if (File.separator == '\\') // Windows
      builder = new ProcessBuilder("cmd", "/C", "karma", "start", configFile.absolutePath)
    else
      builder = new ProcessBuilder("karma", "start", configFile.absolutePath);

    List<String> command = builder.command();

    command.addAll(getExtraArguments());

    builder.redirectErrorStream(true);

    try {
      getLog().info("karma : ${command.join(' ')}")

      return builder.start();

    } catch (IOException e) {
      throw new MojoExecutionException("There was an error executing Karma.", e);
    }
  }

  protected List<String> getExtraArguments() {
    return [] as List<String>
  }
}
