# Stage 2 status (charging framework + first four techniques)

**Stage 2 is NOT complete and NOT build-verified.**

## Implemented in source
| Item | State |
|---|---|
| Pure charge rules (`ChargeSpec`, `ChargeLedger`) | written; **compiled and unit-tested locally (47 checks pass)** |
| Engine integration (`AbilityEngine`: prepare / reserve / settleRelease / settleCancel, charge-in-progress guard) | written, uncompiled |
| `ChargeManager` (sessions, triggers, tick loop, feedback, interrupts, EffectRegistry hookup) | written, uncompiled |
| `ChargeVisuals` (converging spiral particles, per-style colours/sounds, density + session budget) | written, uncompiled |
| Chidori, Rasengan, Rasenshuriken (new), Great Fireball charge + release (`ChargeJutsus`) | written, uncompiled |
| Config (`charging:` section), `/shinobi admin clearfx`, charging line in `/shinobi admin debug` | written, uncompiled |
| Resource pack: Rasenshuriken scroll icon with a new shuriken symbol | built, `verify_pack.py` passes (77 items) |

## Not implemented
Amaterasu, Kamui, Susanoo, Hyuga/Senju/Uzumaki/Nara charge behaviour, charge HUD assets / boss bar, ability-item activation,
region-protection probes for block-changing abilities, custom hand-attached models (server-side particles only).

## Verification actually performed
| Check | Result |
|---|---|
| Compile + run `ChargeLogicTests` (pure logic: stages, scaling, release decisions, overcharge, ledger single-settlement, accounting simulation) | **passed**, 47 checks (authoring sandbox, plain javac) |
| Parse-only syntax check, all 46 Java files | passed (no symbol resolution) |
| `make_pack.py` + `verify_pack.py` | passed |
| `gradle build` / GitHub Actions | **NOT RUN by the assistant** |
| Any test of the Bukkit-facing code (manager, visuals, jutsu effects) | **none exist**; not run on a server; not played in-game |

## Why "complete" is withheld
The requirement is that charge mechanics, release logic, costs, cooldowns and cleanup are verified *together*. Only the pure
accounting/decision rules have been executed. The integration (events, scheduler, particles, damage) has never run.

## To finish
1. Run the GitHub Actions workflow (it runs `gradle build`, which now includes `logicTests`). Send any compile errors.
2. On a Paper 1.21.11 test server: charge/release each technique; cancel; release early; die, quit, change world and
   teleport mid-charge; run out of chakra; spam the swing key; check `/shinobi admin debug` shows 0 active charges afterwards.
3. Record real results here, then continue with Amaterasu / Kamui / Susanoo / clan charges.
