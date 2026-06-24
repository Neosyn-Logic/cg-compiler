# Examples

Small, self-contained C⏚ designs used by CI to prove the compiler emits
valid Verilog across feature areas. Each is generated to Verilog and
elaborated with Icarus Verilog on every push.

| Design | Exercises |
|--------|-----------|
| `Counter.cg` | state register, push output |
| `Adder.cg` | multi-input stream reads, width-extending arithmetic |
| `EdgeDetect.cg` | boolean state, stream handshake |
| `MovingAvg.cg` | arrays, sliding window, casts, shift |

Generate Verilog from any of them:

```bash
java -jar releng/lsp-server/target/cg-language-server.jar generate examples/Counter.cg
```
