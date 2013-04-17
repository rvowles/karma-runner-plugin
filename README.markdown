karma-maven-plugin
==================

When running Karma against an artifact that is being used in a team development environment in Java-land, there are typically a few problems you encounter:

* You have people on a variety of operating systems, so what browsers, ports and so forth are available to you are different between people even if the javascript tests are the same
* War overlays - war overlays allow you to split your projects up into pieces and the final war process will overlay them on top of each other. This allows you to have common javascript in one war overlay,
common libraries in another, etc. But Karma needs access to all of these files to run, and where they will be checked out for each person will differ (if they have them at all) and this won't work on a build server.

What this plugin lets you do is specify a common configuration file for your Karma settings, let the plugin specify where the files needed for each overlay come from (if any) and also include personal
overrides as necessary.

Requirements
------------
* Java 7 - no mucking around with obsolete versions of Java
* Maven 3.0.4 - its been out forever, really, you should be using it
* Karma (I'm using 0.8.5) and all its requirements (nodejs, npm, etc)

How it works
------------

To support overlays, the plugin looks for war dependencies and extracts them into

    target/karma/overlay-artifact-id

(it always deletes the directory first), so for example, these dependencies:

    <dependency>
      <groupId>nz.ac.auckland.common</groupId>
      <artifactId>common-javascript</artifactId>
      <version>[1,2)</version>
      <classifier>underlay</classifier>
      <type>war</type>
    </dependency>
    <dependency>
      <groupId>nz.ac.auckland.common</groupId>
      <artifactId>common-angular</artifactId>
      <version>[1,2)</version>
      <classifier>underlay</classifier>
      <type>war</type>
    </dependency>

will be extracted into

    target/karma/common-javascript
    target/karma/common-angular

It will make a variable

    karma.artifactid

available with the directory the files were extracted to, but if there is a - in the artifact, it will be replaced with _ (to make a valid Java variable name), so you would get

   karma.common_javascript=target/karma/common-javascript
   karma.common_angular=target/karma/common-angular

added to the binding given to your template file.

A look in the tests show a sample karma file:

    var files = [
     '${karma.sausage}/angular/uoa/**/*.js',
     '${karma.fried_tomato}/angular/**/*.js',
     '${karma.eggs}/angular/**/*.js',
     'src/main/webapp/angular/**'
   ];

Overriding Artifacts
--------------------

You can provide a system property with the name:

    karma.common.javascript=/my/projects

and this will override the extraction (which will not happen) if you are working with that project directly on disk and want Karma to have live updates from it.

Providing extra configuration
-----------------------------

You can provide an extra configuration file that gets appended on the end. This means you can replace any property that is already defined (as the file is javascript), and typically you would do this
for browser support which tends to vary.

The default file is

    ${user.home}/.karma/karma.local.js

Maven Configuration
-------------------


     <plugin>
       <groupId>com.bluetrainsoftware.maven</groupId>
       <artifactId>karma-maven-plugin</artifactId>
       <version>1.1</version>
       <configuration>
          <templateFile>karma-template.cfg.js</templateFile> <!-- the file the plugin picks up and replaces stuff in -->
          <karmaFile>karma-runner.cfg.js</karmaFile> <!-- this is the file that gets generated, that Karma runs -->
          <karmaLocalisation>${user.home}/.karma/karma.local.js</karmaLocalisation> <!-- where to find the local karma file -->
       </configuration>
     </plugin>

None of the configuration options are required, those listed are the defaults. The minimum configuration is:

     <plugin>
       <groupId>com.bluetrainsoftware.maven</groupId>
       <artifactId>karma-maven-plugin</artifactId>
       <version>1.1</version>
     </plugin>

I recommend you put this in your war or war-overlay functional parent. It will not fail if it cannot find the karma template, if it doesn't find it, it just issues a warning and exits.

Running the plugin
------------------

The plugin is run from the command line with:

    karma-runner:test

This puts Karma into a single run, no auto watch mode.

or

    karma-runner:voyeur

This puts Karma into its mode of auto watching for file updates and re-running tests.
