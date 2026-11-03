package dev.elide.truffledata;

/** A source contradicted a result contract. Values are deliberately omitted from diagnostics. */
public final class ContractViolation extends IllegalArgumentException {
  public ContractViolation(String message) {
    super(message);
  }
}
