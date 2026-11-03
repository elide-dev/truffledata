package dev.elide.truffledata.comparison;

import com.oracle.truffle.api.TruffleLanguage;
import org.jspecify.annotations.NullMarked;

/** Minimal language context; deliberately excludes JMH and CodSpeed native hooks. */
@NullMarked
@TruffleLanguage.Registration(id = "td-compare", name = "TruffleData Comparison", version = "1")
public final class ComparisonLanguage extends TruffleLanguage<ComparisonLanguage> {
  private static final ContextReference<ComparisonLanguage> REFERENCE =
      ContextReference.create(ComparisonLanguage.class);

  static ComparisonLanguage current() {
    return REFERENCE.get(null);
  }

  @Override
  protected ComparisonLanguage createContext(Env env) {
    return this;
  }
}
