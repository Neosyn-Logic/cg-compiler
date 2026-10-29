# Contributing to cg-compiler

Thanks for your interest in the open-source C⏚ Verilog compiler. Bug reports,
test cases, and patches to the compiler are all welcome.

## Scope

This repository is the **C⏚ language frontend + Verilog backend** only. The
fast (bytecode) simulator, the VHDL backend, and the production IP cores live
in the commercial Neosyn distribution and are out of scope here. See
[NOTICE](NOTICE) and https://neosyn.io/open for the boundary.

The standard-library Verilog (`fragments/com.neosyn.ide.libraries/lib/verilog`)
is © Neosyn and shipped in a non-readable form, so it cannot take patches here.
If a library module misbehaves, open an issue with a small C⏚ design that shows
it.

## Building

Requirements: **JDK 17+** and **Maven 3.9+** (Tycho 4 requires 3.9; 3.8 is too
old).

```bash
cd releng && mvn clean install                # build the plugins and run the unit tests
cd lsp-server && mvn clean package            # build the cg-language-server.jar
```

`mvn clean install -DskipTests` skips the unit tests when you only need the jar.

Then try it:

```bash
java -jar releng/lsp-server/target/cg-language-server.jar generate your/Design.cg
```

## Tests

Run all of these before opening a pull request; CI runs the same steps on every
push (see `.github/workflows/ci.yml`).

- **Unit tests** -- `plugins/com.neosyn.cg.tests`, run by `mvn clean install` in
  `releng/`: validation, IR generation, Verilog golden files, the language
  server. A few tests also run Icarus Verilog or the built jar; they are skipped
  when those are not available.
- **Examples** -- every design in `examples/` must generate and elaborate with
  Icarus Verilog.
- **Scripted checks** -- with the jar built:

  ```bash
  JAR=releng/lsp-server/target/cg-language-server.jar
  bash tests/error-reporting/check.sh "$JAR"   # errors come from the front end, never a crash
  bash tests/assert-checks/check.sh "$JAR"     # generated asserts fail on a wrong value, report a pass
  ```

A fix that changes the generated Verilog should come with a test that fails
without it.

## Project layout

```
plugins/com.neosyn.core    EMF models + IR infrastructure
plugins/com.neosyn.cg      C⏚ grammar, scoping, validation (Xtext)
plugins/com.neosyn.cg.ui   Language server (LSP) implementation
plugins/com.neosyn.ide     Verilog generator + shared IR transforms
plugins/com.neosyn.cg.tests Unit tests and their C⏚ fixtures
fragments/...libraries     Standard-library Verilog (© Neosyn, see NOTICE)
releng/                    Tycho reactor + the standalone language-server jar
examples/                  Designs CI generates and elaborates
tests/                     Scripted checks run against the built jar
```

## Versions

The version (`--version`, [CHANGELOG.md](CHANGELOG.md)) is the one of the Neosyn
C⏚ release the compiler comes from. Note your change under `[Unreleased]` in
CHANGELOG.md if it changes what a user sees.

## Pull requests

- Keep changes focused; one logical change per PR.
- If you touch generated Verilog, include a small C⏚ input + the expected
  output in the description so reviewers can reproduce.
- By submitting a contribution you certify the Developer Certificate of Origin
  (DCO) - i.e. you wrote it or have the right to submit it under MPL-2.0. Sign
  off your commits with `git commit -s`.

## License

Contributions are accepted under the **Mozilla Public License 2.0**. New source
files should carry the standard MPL-2.0 header (see existing files).
