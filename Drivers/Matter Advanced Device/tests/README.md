# Local checks

Nothing here touches the hub. Requires Groovy 4 and a JDK on PATH (4.0.32 / JDK 17 used), and
Python for the extractor.

```powershell
.\tests\run-tests.ps1
```

Three checks, in order:

1. **Compile** — `groovyc` against four stubbed Hubitat platform classes in `stubs/`
   (`hubitat.matter.DataType`, `hubitat.helper.HexUtils`, `hubitat.device.HubAction`,
   `hubitat.device.Protocol`). This works because the driver is monolithic; a V3 `#include` source
   cannot be compiled this way, since `#include` is not valid Groovy.
   It catches syntax and resolution errors. It does **not** catch Hubitat sandbox behaviour —
   blocked reflection, the ~64 KB method limit, the ~20 s method budget.
2. **Helper assertions** — `extract_helpers.py` pulls the pure helper methods out of the current
   driver source and glues them to `helpers_assertions.groovy`, so the assertions can never drift
   from the code. Add new cases to `helpers_assertions.groovy` only.
3. **Collector simulation** — `collector_sim.groovy` drives the `collectTick` state machine with a
   mocked clock and a mocked device. It proves the walk terminates and visits every entry for:
   discovery stage 1 and 2, a wildcard `getInfo`, one silent cluster among answering ones, a
   cluster large enough to need four read chunks, and a device that answers nothing at all.

**Caveat worth knowing:** the tick body in `collector_sim.groovy` is a *copy* of the one in the
driver. If you change `collectTick`, re-copy it, or the simulation will keep testing the old logic.
The helper assertions do not have this problem — they re-extract every run.
