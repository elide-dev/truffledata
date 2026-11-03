package dev.elide.truffledata.probe;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/** Minimal language keeps expected-failure probes independent of benchmark infrastructure. */
@TruffleLanguage.Registration(id = "td-probe", name = "TruffleData negative control", version = "1")
public final class ProbeLanguage extends TruffleLanguage<Object> {
  private static final LanguageReference<ProbeLanguage> REFERENCE =
      LanguageReference.create(ProbeLanguage.class);

  static ProbeLanguage current() {
    return REFERENCE.get(null);
  }

  @Override
  protected Object createContext(Env env) {
    return new Object();
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    return new RootNode(this) {
      @Override
      public Object execute(VirtualFrame frame) {
        return 0L;
      }
    }.getCallTarget();
  }
}
