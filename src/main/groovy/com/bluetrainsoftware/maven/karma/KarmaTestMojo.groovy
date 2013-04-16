package com.bluetrainsoftware.maven.karma

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope

/**

 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
@Mojo(name="test", requiresProject = false, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
class KarmaTestMojo extends KarmaBaseMojo {
  @Override
  protected List<String> getExtraArguments() {
    return ["--no-auth-watch", "--single-run"] as List<String>
  }
}
