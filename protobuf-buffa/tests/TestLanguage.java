package dev.elide.truffledata.buffa.tests;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.RootNode;

@TruffleLanguage.Registration(id = "buffa-test", name = "Buffa acceptance", version = "0.2.0")
public final class TestLanguage extends TruffleLanguage<Object> {
  @Override
  protected Object createContext(Env env) {
    return new Object();
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    return RootNode.createConstantNode(true).getCallTarget();
  }
}
