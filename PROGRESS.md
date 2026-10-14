# Progress

| Stage | Scope | Source written | Pure logic tests | Gradle/Actions build | In-game tested |
|---|---|---|---|---|---|
| 1 | Schema migration, backups, atomic saves, Hatake->Senju, AbilityEngine, EffectRegistry, crash-safe TempBlocks | yes | none | owner reported complete; no log seen | no |
| 2 (part) | Charging framework; Chidori, Rasengan, Rasenshuriken, Great Fireball | yes | 47 pass | not run | no |
| 3a | Bloodline registry, rules, persistence (schema v3), gating, admin tools | yes | 168 pass | not run | no |

Details: `docs/STAGE1_STATUS.md`, `docs/STAGE2_STATUS.md`, `docs/STAGE3_STATUS.md`.
Dates/versions: plugin 0.9.0-stage3a, Paper 1.21.11 only, Java 21.
| 3b | Phase A checks (migration tests, cast-route guard); Uchiha Sharingan dodge | yes | 66 dodge + 26 migration + 2 route | not run | no |

Logic-test total: 47 charge, 167 bloodline, 26 migration, 66 dodge, 2 route-guard (all pass locally). Plugin version 0.10.0-stage3b.
| 3c | Headband/eye equipment-asset fix; admin override core (permissions, forced access, audit, schema v4) | yes | +27 eye, +109 admin, migration 31 | not run | no |

Plugin version 0.11.0-stage3c.
| 3d | Susanoo compatibility: pure state machine, compat table, v5 migration (mangekyo-powers); headband as cosmetic display over any helmet; real 3D element-geometry models (16: 12 headgear, 4 orbs); model geometry verifier + orthographic renderer; orb cleanup in ChargeManager; HeadgearRules+tests, Susanoo logic 82, headgear logic 11 | yes | 82 susuanoo + 11 headgear = 93 new | not run | no |

Logic-test total: 47 + 167 + 38 + 66 + 27 + 109 + 82 + 11 + 8 = 555 checks. Plugin version 0.12.0-stage3d.
