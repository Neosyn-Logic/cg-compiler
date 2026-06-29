# cg-compiler - the open-source C⏚ Verilog compiler

[![build](https://github.com/Neosyn-Logic/cg-compiler/actions/workflows/ci.yml/badge.svg)](https://github.com/Neosyn-Logic/cg-compiler/actions/workflows/ci.yml)
[![License: MPL 2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg)](https://www.mozilla.org/MPL/2.0/)

**C⏚** ("C-Ground", or "Cg") is a hardware description language with C-like
syntax that compiles to clean, standard **Verilog**. This repository is the
open-source compiler: the language frontend (parser, type system, validation)
and the Verilog backend, under the **Mozilla Public License 2.0**.

```c
// A 4-tap moving-average filter in C⏚
task MovingAvg {
  in  stream u8 sample;     // samples in
  out stream u8 filtered;   // smoothed out

  u8 window[4];
  u2 head;

  void loop() {
    u8 x = sample.read;
    window[head] = x;
    head = (u2)(head + 1);
    u10 sum = (u10)(window[0] + window[1] + window[2] + window[3]);
    filtered.write((u8)(sum >> 2));
  }
}
```

```
$ java -jar cg-language-server.jar generate MovingAvg.cg
→ verilog-gen/MovingAvg.v   (clean, synthesizable Verilog)
```

## Why this is open source

The hardest question anyone asks before betting silicon on a tool is *"what
happens to my designs if you disappear?"* This repository is the answer: the
C⏚ Verilog compiler is yours as much as ours. Build it from source, fork it,
keep it alive - your designs compile to standard Verilog forever, with or
without us. No escrow clause, no lock-in.

## What's here, and what isn't

This repo is the **Verilog compiler**. The commercial Neosyn distribution adds
convenience and premium features on top - you pay for ease of use, never for
permission to compile.

| Open (this repo, MPL-2.0) | Commercial (neosyn.io) |
|---------------------------|------------------------|
| C⏚ language frontend (parse, type-check, validate) | The supported, prebuilt VS Code extension |
| Verilog code generator | The fast (bytecode) cycle-accurate simulator |
| The language server (LSP): diagnostics, hover, navigation | VHDL backend |
| The bundled Verilog runtime library | Production IP cores (Ethernet, AES, RISC-V, …) |
| | Priority support / SLA |

Read more about the open-core model at **https://neosyn.io/open**.

## Get it

**Prebuilt jar (no build needed):** download `cg-language-server.jar` from the
[latest release](https://github.com/Neosyn-Logic/cg-compiler/releases/latest)
and run it with JDK 17+ — see [Use it](#use-it). Or build from source below.

## Build from source

Requirements: **JDK 17+** and **Maven 3.9+** (Tycho 4 needs 3.9, not 3.8).

```bash
# 1. Build + install the language plugins (Tycho reactor)
cd releng
mvn clean install -DskipTests

# 2. Build the standalone language-server / CLI jar
cd lsp-server
mvn clean package
# → target/cg-language-server.jar
```

## Use it

```bash
# Generate Verilog from a C⏚ file or project
java -jar cg-language-server.jar generate path/to/Design.cg

# Generate the intermediate representation
java -jar cg-language-server.jar generate-ir path/to/Design.cg

# Inspect a task's compiled FSM, or a network's graph
java -jar cg-language-server.jar fsm   path/to/Design.cg
java -jar cg-language-server.jar graph path/to/Design.cg

# Run as a language server over stdio (for editor integration)
java -jar cg-language-server.jar --stdio
```

The generated Verilog is vendor-neutral - synthesize it on Intel, Xilinx,
Lattice or Microchip, or end-to-end with the open-source flow (Yosys +
nextpnr + Icarus Verilog).

## Examples and docs

- Example designs and tutorials: **https://github.com/Neosyn-Logic/cg-examples**
- Language documentation: **https://neosyn.io/docs**
- The language overview: **https://neosyn.io/language**

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Improvements to the compiler are
welcome; MPL-2.0 means changes to covered files are shared back.

## License

Mozilla Public License 2.0 - see [LICENSE](LICENSE) and [NOTICE](NOTICE).
C⏚ began as the Synflow Cx toolchain; this compiler is its open-source
continuation.
