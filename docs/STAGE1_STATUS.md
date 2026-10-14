# Stage 1 status

**Update (Stage 2 request):** the project owner stated that Stage 1 is complete. No GitHub Actions log was supplied to the
assistant, so this repository records that as the owner's report, not as a verified build result. The original status follows.

**Original status: Stage 1 was not declared complete by the assistant.** The code was written, but no real build had been run in the authoring environment.

## Scope (done in source)
| Item | State |
|---|---|
| Paper 1.21.11-only baseline (README, compatibility doc, pack metadata) | written |
| Versioned profile migration + backup (`SchemaMigrator`, `ProfileManager`) | written, unverified |
| Atomic async saves, `.bak`, corrupt-file quarantine, unknown-key preservation | written, unverified |
| Hatake -> Senju (enum, jutsu gating, commands, config, pack crest, docs) | written, unverified |
| `AbilityEngine` (all casts routed through it) | written, unverified |
| `EffectRegistry` + quit/death/world-change/shutdown hooks | written, unverified; existing jutsu not yet ported to it |
| Crash-safe `TempBlocks` (journal, startup restore, explosion/piston guards) | written, unverified; no region-protection check |
| `/shinobi admin debug`, `/shinobi admin restore` | written, unverified |

## Verification actually performed
| Check | Result |
|---|---|
| Parse-only syntax check of all Java files (`javac` parser, no symbol resolution) | passed, 0 syntax errors (done in the authoring sandbox) |
| `python3 tools/make_pack.py` regenerates the pack | passed; differs from the uploaded pack only by `clan_hatake` -> `clan_senju` |
| `python3 tools/verify_pack.py` (76 item definitions -> models -> textures, font grids, pack.mcmeta) | passed |
| `gradle build` against Paper 1.21.11 API | **NOT RUN** (the authoring sandbox cannot reach the Paper/Maven/Gradle hosts) |
| Unit or integration tests | none exist |
| Running on a Paper server / in-game testing | **NOT DONE** |

## To finish Stage 1
1. Push this folder to GitHub and run the "Build ShinobiSMP" workflow.
2. If it fails, send the failing step's error lines; they will be fixed (the most likely sources are API signature mistakes in the new classes).
3. Once green, copy the real result into this file and `COMPATIBILITY.md`. Only then is Stage 1 complete.
4. Recommended manual checks on a test server (not yet done): migrate a copy of real Hatake player files; kill the server mid-wall
   and confirm the wall is gone after restart; cast with insufficient chakra and on cooldown.

## Known limitations
- Profiles are still saved on the main-thread serialisation path (file I/O is async).
- Journal write for temporary blocks happens up to one tick after placement.
- `EffectRegistry` is new; the 35 existing scheduler call sites have not been migrated onto it.
