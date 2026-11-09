package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OwnedArrayTest {
  private record Array(Object[] values) implements OwnedArray {
    Array {
      values = values.clone();
    }

    @Override
    public int size() {
      return values.length;
    }

    @Override
    public Object get(int index) {
      return values[index];
    }
  }

  @Test
  void ownershipTransferRejectsScopedElementsAtAnyArrayDepth() {
    OwnedArray owned = new Array(new Object[] {1L, "durable"});
    assertSame(owned, Values.normalize(owned));
    assertSame(owned, new DataArray(new Object[] {owned}).get(0));
    BorrowedArray borrowed =
        new BorrowedArray() {
          public int size() {
            return 1;
          }

          public Object element(int index) {
            return 1L;
          }

          public DataArray snapshot() {
            return new DataArray(new Object[] {1L});
          }
        };
    OwnedArray invalid = new Array(new Object[] {new Array(new Object[] {borrowed})});
    assertThrows(ContractViolation.class, () -> Values.normalize(invalid));
    assertThrows(ContractViolation.class, () -> new DataArray(new Object[] {invalid}));
    assertThrows(ContractViolation.class, () -> owned.copyBytesTo(new byte[2], 0));
  }

  @Test
  void constraintsAndPayloadBudgetsTraverseOwnedArrays() {
    OwnedArray nested = new Array(new Object[] {new Array(new Object[] {"hello"})});
    assertThrows(
        ContractViolation.class,
        () -> new PayloadBudget(new Limits(100, 100, 100, 8)).accept(nested));
    assertThrows(
        ContractViolation.class,
        () -> new PayloadBudget(new Limits(100, 100, 1, 100)).accept(nested));
    var field =
        new Field(
            "items",
            Field.Kind.ARRAY,
            false,
            false,
            Constraints.NONE
                .withElement(Field.requiredLong("item"))
                .withLength(new Constraints.Length(1, 1, Constraints.LengthUnit.ELEMENTS)));
    assertThrows(ContractViolation.class, () -> Values.validate(field, nested));
    OwnedArray valid = new Array(new Object[] {42L});
    assertSame(valid, Values.validate(field, valid));
    new PayloadBudget(Limits.DEFAULT).accept(valid);
    OwnedArray cycle =
        new OwnedArray() {
          public int size() {
            return 1;
          }

          public Object get(int index) {
            return this;
          }
        };
    assertThrows(ContractViolation.class, () -> Values.normalize(cycle));
  }

  @Test
  void byteImplementationsHaveUnsignedElementsAndBoundedCopies() {
    OwnedArray bytes =
        new OwnedArray() {
          public int size() {
            return 2;
          }

          public Object get(int index) {
            return index == 0 ? 0L : 255L;
          }

          public boolean isBytes() {
            return true;
          }
        };
    assertSame(bytes, Values.normalize(bytes));
    byte[] copy = new byte[3];
    bytes.copyBytesTo(copy, 1);
    assertArrayEquals(new byte[] {0, 0, (byte) 255}, copy);
    assertThrows(IndexOutOfBoundsException.class, () -> bytes.copyBytesTo(copy, 2));
    OwnedArray invalid =
        new OwnedArray() {
          public int size() {
            return 1;
          }

          public Object get(int index) {
            return 256L;
          }

          public boolean isBytes() {
            return true;
          }
        };
    assertThrows(ContractViolation.class, () -> Values.normalize(invalid));
  }
}
