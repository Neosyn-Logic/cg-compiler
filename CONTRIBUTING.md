# Contributing to cg-compiler

Thanks for your interest in the open-source C⏚ Verilog compiler. Bug reports,
test cases, and patches to the compiler are all welcome.

## Scope

This repository is the **C⏚ language frontend + Verilog backend** only. The
fast (bytecode) simulator, the VHDL backend, and the production IP cores live
in the commercial Neosyn distribution and are out of scope here. See
[NOTICE](NOTICE) and https://neosyn.io/open for the boundary.

## Building

Requirements: **JDK 17+** and **Maven 3.9+** (Tycho 4 requires 3.9; 3.8 is too
old).

```bash
cd releng && mvn clean install -DskipTests   # build + install the plugins
cd lsp-server && mvn clean package            # build the cg-language-server.jar
```

Then try it:

```bash
java -jar releng/lsp-server/target/cg-language-server.jar generate your/Design.cg
```

## Project layout

```
plugins/com.neosyn.core    EMF models + IR infrastructure
plugins/com.neosyn.cg      C⏚ grammar, scoping, validation (Xtext)
plugins/com.neosyn.cg.ui   Language server (LSP) implementation
plugins/com.neosyn.ide     Verilog generator + shared IR transforms
fragments/...libraries     Bundled Verilog runtime library
releng/                    Tycho reactor + the standalone language-server jar
```

## Pull requests

- Keep changes focused; one logical change per PR.
- If you touch generated Verilog, include a small C⏚ input + the expected
  output in the description so reviewers can reproduce.
- By submitting a contribution you certify the Developer Certificate of Origin
  (DCO) — i.e. you wrote it or have the right to submit it under MPL-2.0. Sign
  off your commits with `git commit -s`.

## License

Contributions are accepted under the **Mozilla Public License 2.0**. New source
files should carry the standard MPL-2.0 header (see existing files).
