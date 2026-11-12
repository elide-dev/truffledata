#!/usr/bin/env python3
"""Derive a Truffle cells seam from a production seam.

    cells_seam.py PRODUCTION OUT --package PKG --class CLASS --options "OPTIONS" [--ownership OVERLAY]

The module line gets the cells package, class and options; nothing else on it changes. An optional
ownership overlay (`<format>/cells/ownership.seam`) then adds Myna's ownership declarations, which
only the cells projection carries: the production seam, its fingerprint, bindings and natives stay
exactly as they are. The overlay holds two kinds of lines:

- `opaque`, `resource` and `borrow` declarations, inserted verbatim after the module line;
- edits of production declarations, each of which must match exactly one line:
      edit import NAME KEY=VALUE...          an attribute on the `import NAME` line
      edit param NAME.PARAM KEY=VALUE...     an attribute on a `param` line of `import NAME`
      edit field STRUCT.FIELD KEY=VALUE...   an attribute on a `field` line of `struct STRUCT`
  `KEY=VALUE` replaces the attribute the line already has; `+KEY=VALUE` adds one it does not.

Only the edits an ownership declaration needs are accepted, so a cells seam cannot drift from the
production ABI by accident: the explicit contracts `nounwind`, `nocapture` and `readonly`; narrowing
a `ptr<void>` to a pointer to an opaque type or struct (the same C type); and
`ownership=transferred_to_callee` on a resource's destroy parameter. //bazel:myna_contract checks
the resulting descriptor against the production one under the same rules.
"""
import argparse
import re
import sys

FACTS = {"nounwind", "nocapture", "readonly"}
DECLARATIONS = ("opaque ", "resource ", "borrow ")


def fail(message):
    sys.exit("cells_seam: " + message)


def attributes(line):
    return dict(token.split("=", 1) for token in line.split() if "=" in token)


def apply(line, changes, where):
    for change in changes:
        add = change.startswith("+")
        key, _, value = change.lstrip("+").partition("=")
        if not value:
            fail("%s: %r is not KEY=VALUE" % (where, change))
        present = attributes(line)
        if add:
            if key not in FACTS or value != "true@explicit_contract":
                fail("%s: only %s=true@explicit_contract may be added, not %r" % (where, "/".join(sorted(FACTS)), change))
            if key in present:
                fail("%s: %s is already declared" % (where, key))
            line = line + " " + key + "=" + value
            continue
        if key not in present:
            fail("%s: %s is not declared, use +%s" % (where, key, key))
        old = present[key]
        if key in ("type", "return"):
            if old != "ptr<void>" or not re.fullmatch(r"ptr<[A-Z][A-Za-z0-9_]*>", value):
                fail("%s: only ptr<void> may be narrowed to a named pointer, not %s -> %s" % (where, old, value))
        elif key == "ownership":
            if (old, value) != ("borrowed", "transferred_to_callee"):
                fail("%s: ownership may only become transferred_to_callee, not %s -> %s" % (where, old, value))
        else:
            fail("%s: %s cannot be changed in a cells seam" % (where, key))
        line = re.sub(r"(?<= )%s=\S+" % re.escape(key), key + "=" + value, line, count=1)
    return line


def derive(source, package, java_class, options, overlay):
    lines = source.split("\n")
    module = [i for i, line in enumerate(lines) if line.startswith("module ")]
    if len(module) != 1:
        fail("expected one module line")
    head = lines[module[0]]
    head, count = re.subn(r" java_package=\S+ java_class=\S+ ",
                          " java_package=%s java_class=%s %s " % (package, java_class, options), head)
    if count != 1:
        fail("module line has no java_package/java_class pair")
    lines[module[0]] = head

    inserts = []
    for number, raw in enumerate(overlay.split("\n") if overlay else [], 1):
        text = raw.strip()
        where = "ownership line %d" % number
        if not text or text.startswith("#"):
            continue
        if text.startswith(DECLARATIONS):
            inserts.append(text)
            continue
        parts = text.split()
        if parts[0] != "edit" or len(parts) < 4 or parts[1] not in ("import", "param", "field"):
            fail("%s: expected a declaration or `edit import|param|field TARGET KEY=VALUE...`: %r" % (where, raw))
        kind, target, changes = parts[1], parts[2], parts[3:]
        if kind == "import":
            matches = [i for i, line in enumerate(lines) if line.startswith("import %s " % target)]
        else:
            owner, _, member = target.partition(".")
            block = "import" if kind == "param" else "struct"
            matches, inside = [], False
            for i, line in enumerate(lines):
                if line.startswith(block + " "):
                    inside = line.split()[1] == owner
                elif line.strip() == "end":
                    inside = False
                elif inside and line.strip().startswith("%s %s " % (kind, member)):
                    matches.append(i)
        if len(matches) != 1:
            fail("%s: %s %s matches %d lines" % (where, kind, target, len(matches)))
        lines[matches[0]] = apply(lines[matches[0]], changes, where)

    if inserts:
        lines[module[0] + 1:module[0] + 1] = inserts
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("production")
    parser.add_argument("out")
    parser.add_argument("--package", required=True)
    parser.add_argument("--class", dest="java_class", required=True)
    parser.add_argument("--options", required=True)
    parser.add_argument("--ownership")
    args = parser.parse_args()
    with open(args.production, encoding="utf-8") as source:
        production = source.read()
    overlay = None
    if args.ownership:
        with open(args.ownership, encoding="utf-8") as source:
            overlay = source.read()
    with open(args.out, "w", encoding="utf-8") as out:
        out.write(derive(production, args.package, args.java_class, args.options, overlay))


if __name__ == "__main__":
    main()
