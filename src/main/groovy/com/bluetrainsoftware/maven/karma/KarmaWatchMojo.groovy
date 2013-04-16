package com.bluetrainsoftware.maven.karma

import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.ResolutionScope

/**

 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
@Mojo(name="voyeur", requiresProject = false, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
class KarmaWatchMojo extends KarmaBaseMojo {
}
