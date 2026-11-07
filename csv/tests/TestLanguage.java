package dev.elide.truffledata.csv.tests;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.RootNode;

@TruffleLanguage.Registration(id = "csv-test", name = "CSV acceptance", version = "0.2.0")
public final class TestLanguage extends TruffleLanguage<Object> {
  private static final LanguageReference<TestLanguage> REFERENCE =
      LanguageReference.create(TestLanguage.class);

  static TestLanguage current() {
    return REFERENCE.get(null);
  }

  @Override
  protected Object createContext(Env env) {
    return new Object();
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    return RootNode.createConstantNode(true).getCallTarget();
  }
}
