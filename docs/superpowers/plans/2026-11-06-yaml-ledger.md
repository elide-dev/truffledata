# SDD ledger — plan: docs/superpowers/plans/2026-11-06-yaml.md

Decisions (user, 2026-11-06):
- Q1 Rename: the unreleased JSON owner/value/sink/typed types move to shared `Tape*` names in `//tape` (Task 4); there are no public consumers yet.
- Q2 `YAML.parse` defaults follow the `yaml` npm package: 1.2 core schema, unique keys, merge keys off, aliases preserve identity.
- Q3 Non-finite floats: Myna will add support (elide-dev/myna#52, M6, being implemented by the maintainer). Until it lands, YAML tapes write raw non-finite F64 bits as planned; switch to the Myna flag when available.
- Q4 `%YAML 1.1` directive: recorded and exposed through `directives()`; resolution switches to 1.1 only by explicit opt-in (`YamlOptions.yaml11()` or an option honouring the directive). A document comment must not silently change how values parse.
- Q5 `truffledata-tape` ships as its own BOM-managed artifact.
- Q6 Upstream filed: elide-dev/myna#52 (M6 non-finite F64), elide-dev/myna#53 (M7 REF nodes + per-node metadata), biojppm/rapidyaml#693 (conformance configuration docs), biojppm/rapidyaml#694 (assertions make long flow lines quadratic).
