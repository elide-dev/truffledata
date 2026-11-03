package dev.elide.truffledata.buffa;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

/** Explicit opt-in: --features=dev.elide.truffledata.buffa.BuffaFeature plus the static archive. */
@org.graalvm.nativeimage.Platforms(org.graalvm.nativeimage.Platform.HOSTED_ONLY.class)
public final class BuffaFeature implements Feature {
  @Override
  public void afterRegistration(AfterRegistrationAccess access) {
    ImageSingletons.add(BuffaWire.NativeFactory.class, new SvmBackend.Factory());
  }
}
