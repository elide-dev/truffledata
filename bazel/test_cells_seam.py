#!/usr/bin/env python3
"""Unit tests of bazel/cells_seam.py (derivation and overlay policy) and of the ownership delta
that bazel/myna_contract.py accepts between a production and a derived descriptor."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import cells_seam  # noqa: E402
import myna_contract  # noqa: E402

SEAM = """module m abi=1 target=x86_64-unknown-linux-gnu java_package=p.generated java_class=M c_library=m
struct R size=16 align=8
  field data type=ptr<void> offset=0
  field n type=u64 offset=8
end
import make symbol=m_make return=ptr<void> error=no_failure
  param limits type=ptr<void> nullable=false ownership=borrowed access=read
end
import free symbol=m_free return=void error=no_failure
  param handle type=ptr<void> nullable=false ownership=borrowed access=read_write
end
import scan symbol=m_scan return=i64 error=no_failure
  param data type=ptr<u8> nullable=false ownership=borrowed access=read
  param length type=u64
end
"""


def derive(overlay):
    return cells_seam.derive(SEAM, "p.cells.generated", "MCells", "truffle_cells=true", overlay)


class CellsSeamTest(unittest.TestCase):
    def test_module_options_only_without_overlay(self):
        out = derive(None)
        self.assertEqual(
            out.split("\n", 1)[0],
            "module m abi=1 target=x86_64-unknown-linux-gnu java_package=p.cells.generated "
            "java_class=MCells truffle_cells=true c_library=m")
        self.assertEqual(out.split("\n", 1)[1], SEAM.split("\n", 1)[1])

    def test_overlay_inserts_declarations_and_applies_edits(self):
        out = derive("""# comment
opaque H
resource H representation=native threading=confined create=make destroy=free
borrow scan data length=length lifetime=call
edit import scan +nounwind=true@explicit_contract
edit param scan.data +nocapture=true@explicit_contract +readonly=true@explicit_contract
edit import make return=ptr<H>
edit param free.handle type=ptr<H> ownership=transferred_to_callee
edit field R.data type=ptr<H>
""")
        lines = out.split("\n")
        self.assertEqual(lines[1:4], ["opaque H",
                                      "resource H representation=native threading=confined create=make destroy=free",
                                      "borrow scan data length=length lifetime=call"])
        self.assertIn("import scan symbol=m_scan return=i64 error=no_failure nounwind=true@explicit_contract", lines)
        self.assertIn("  param data type=ptr<u8> nullable=false ownership=borrowed access=read "
                      "nocapture=true@explicit_contract readonly=true@explicit_contract", lines)
        self.assertIn("import make symbol=m_make return=ptr<H> error=no_failure", lines)
        self.assertIn("  param handle type=ptr<H> nullable=false ownership=transferred_to_callee access=read_write", lines)
        self.assertIn("  field data type=ptr<H> offset=0", lines)
        # The edited param of `make` is untouched.
        self.assertIn("  param limits type=ptr<void> nullable=false ownership=borrowed access=read", lines)

    def rejects(self, overlay):
        with self.assertRaises(SystemExit):
            derive(overlay)

    def test_overlay_policy(self):
        self.rejects("edit import scan return=i32")                         # not a pointer narrowing
        self.rejects("edit param scan.length type=u32")                     # not a pointer narrowing
        self.rejects("edit param scan.data type=ptr<H>")                    # ptr<u8> is not ptr<void>
        self.rejects("edit param scan.data access=read_write")              # access is not editable
        self.rejects("edit param free.handle ownership=transferred_to_caller")
        self.rejects("edit import scan +native_leaf=true@explicit_contract")  # not an ownership fact
        self.rejects("edit import scan +nounwind=true")                     # not an explicit contract
        self.rejects("edit import scan nounwind=true@explicit_contract")    # replace needs it declared
        self.rejects("edit param scan.missing +nocapture=true@explicit_contract")
        self.rejects("edit import nothing +nounwind=true@explicit_contract")
        self.rejects("import other symbol=x return=void error=no_failure")  # no new imports
        self.rejects("struct S size=8 align=8")                              # no new structs


def function(name, params, ret="i32", facts=None):
    return {"logicalName": name, "returnType": ret, "facts": facts or {},
            "parameters": params, "abiParameters": params}


def param(type_, ownership="BORROWED", facts=None):
    return {"type": type_, "ownership": ownership, "facts": facts or {}}


class OwnershipDeltaTest(unittest.TestCase):
    FACT = {"value": "true", "provenance": "EXPLICIT_CONTRACT", "explanation": "DSL contract"}

    def base(self):
        return {"opaqueTypes": [], "functions": [function("make", [], "ptr<void>"),
                                                 function("free", [param("ptr<void>")], "void")]}

    def test_accepts_the_ownership_delta(self):
        cells = {"opaqueTypes": ["H"], "resources": [{"create": "make", "destroy": "free"}], "borrows": [],
                 "functions": [function("make", [], "ptr<H>", {"nounwind": self.FACT}),
                               function("free", [param("ptr<H>", "TRANSFERRED_TO_CALLEE")], "void")]}
        added = myna_contract.ownership_delta(self.base(), cells, "", {"1"})
        self.assertTrue(added)

    def test_rejects_other_changes(self):
        for cells, destroys in [
            ({"opaqueTypes": [], "functions": [function("make", [], "ptr<void>"),
                                               function("free", [param("ptr<void>", "TRANSFERRED_TO_CALLEE")], "void")]},
             set()),                                                       # transfer on a non-destroy
            ({"opaqueTypes": [], "functions": [function("make", [], "u64"),
                                               function("free", [param("ptr<void>")], "void")]}, set()),
            ({"opaqueTypes": [], "functions": [function("make", [], "ptr<void>", {"native_leaf": self.FACT}),
                                               function("free", [param("ptr<void>")], "void")]}, set()),
            ({"opaqueTypes": [], "functions": [function("make", [], "ptr<void>", {"nounwind": dict(self.FACT, provenance="INFERRED")}),
                                               function("free", [param("ptr<void>")], "void")]}, set()),
            ({"opaqueTypes": [], "callbacks": [], "functions": self.base()["functions"]}, set()),
            ({"opaqueTypes": [], "functions": self.base()["functions"][:1]}, set()),
        ]:
            with self.assertRaises(AssertionError):
                myna_contract.ownership_delta(self.base(), cells, "", destroys)


if __name__ == "__main__":
    unittest.main()
