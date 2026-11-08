package dev.elide.truffledata.comparison;

import com.oracle.truffle.api.TruffleLanguage;
import org.jspecify.annotations.NullMarked;

/** Minimal language context; deliberately excludes JMH and CodSpeed native hooks. */
@NullMarked
@TruffleLanguage.Registration(id = "td-compare", name = "TruffleData Comparison", version = "1")
public final class ComparisonLanguage extends TruffleLanguage<ComparisonLanguage> {
  private TruffleLanguage.@org.jspecify.annotations.Nullable Env environment;

  static Object guest(Object value) {
    return java.util.Objects.requireNonNull(current().environment).asGuestValue(value);
  }

  private static final ContextReference<ComparisonLanguage> REFERENCE =
      ContextReference.create(ComparisonLanguage.class);

  static ComparisonLanguage current() {
    return REFERENCE.get(null);
  }

  @Override
  protected ComparisonLanguage createContext(Env env) {
    environment = env;
    return this;
  }
}
