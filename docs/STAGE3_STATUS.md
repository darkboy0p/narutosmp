# Stage 3 status: clan-based kekkei genkai

**Stage 3 is NOT complete. Not build-verified. Latest iteration (0.12.0-stage3d): see below.**

## Iteration 0.11.0-stage3c summary
| Item | State | Evidence |
|---|---|---|
| Worn headband that looks like a forehead protector (equipment asset) | source + 12 generated assets | static pack checks pass; schematic viewed; **not seen in a client** |
| Dojutsu eyes on the face while the headband is worn (12 styles) | source + assets | same; uses the pack eye art at 8x layer resolution; limits in `docs/VISUAL_VERIFICATION.md` |
| Admin permission hierarchy (10 nodes) | `plugin.yml` + per-subcommand gate | 109 admin checks pass (pure); live untested |
| Forced bloodlines / forced abilities, central access decision, schema v4 | source | pure tests pass (forced rules, migration); live untested |
| `admin bloodline/ability/access/audit` + audit log | source | pure tests for validation and audit JSON; commands themselves uncompiled |
| Hold-to-charge with a Fabric companion mod, 4-stage aura, hand-flow visuals | **NOT implemented** | the Fabric toolchain cannot be fetched in the authoring environment, so a mod could not be built or tested; existing charge visuals are server particles visible to nearby players |
| Hyuga, Senju, Uzumaki, Nara abilities; rare-bloodline abilities; admin GUI; most listed admin subcommands | **NOT implemented** | see `docs/ADMIN_COMMANDS.md` |

Logic tests now: charge 47, bloodline 167, migration 31, dodge 66, eye style 27, admin 109, route guard 2 (all pass; plain javac).
Still **no Gradle/Actions build, no Paper API compile, no live server, no in-game visual check.**

## Group 2, Phase A: foundation verification (this iteration)
| Item | Result |
|---|---|
| Run GitHub Actions with Java 21 / Paper 1.21.11 | **NOT POSSIBLE from the authoring environment; no result exists.** The project is NOT known to build. |
| Looked for another way to compile (npm/PyPI Paper API jars) | none usable (only 1.16/1.17 TypeScript typings and plugin runtime jars) |
| Schema v3 migration preserves data | **tested** with a map-backed document (26 checks): Hatake->Senju, v2->v3, Mangekyo seeding, existing grants kept, stray data kept, idempotent, future schemas untouched, empty profile safe. The YAML adapter in `ProfileManager` and the on-disk **backup copy** are reviewed by reading only, not run. |
| Every cast route enforces eligibility | **audited + guard test**: commands, GUI, seal combos, quick-cast and bound slots all end in `AbilityEngine.cast`; `CastRouteGuardTests` fails the build if any other file invokes an effect directly (mutation-checked: a planted bypass fails it). Live behaviour of each route is untested. |

## Group 2 progress
| Clan | Ability | State |
|---|---|---|
| Uchiha | Sharingan activation + tomoe evolution | existing behaviour kept; bloodline gate added in group 1; no new evolution mechanics |
| Uchiha | **Directional Sharingan dodge** (afterimage, capped avoidance window) | **written; rules unit-tested (66 checks); Bukkit part uncompiled/untested** |
| Uchiha | Amaterasu, Kamui (+recovery), Tsukuyomi, Susanoo stages | **not started in this iteration** (older, simpler versions exist from v0.6 and are unchanged) |
| Hyuga, Senju, Uzumaki, Nara | all listed abilities | not started |
| Phase C assets (icons, sounds, models) | not started |

## Sharingan dodge: what exists
- It is the `sharingan_dodge` jutsu, so chakra, cooldown, eligibility (Uchiha + awakened + Sharingan bloodline + active dojutsu) and
  duplicate-cast protection all come from `AbilityEngine`. Triggers: double-tap sneak (configurable), bound slot, or the book.
  Paper cannot read movement keys: direction = your recent movement, otherwise straight backwards (documented fallback).
- Dash: up to 3.0-4.5 blocks by stage in 0.5-block steps; each step needs passable feet/head, no liquid or hazard ground
  (lava, magma, fire, cactus...), solid ground (max 2-block step down), inside the world border, and no wall between steps.
  Under one block of room = the dodge fails and nothing is charged. `Player#teleport` can be vetoed by other plugins.
- Avoidance window: 3-5 ticks, only for melee/sweep/projectile damage (configurable); explosions, magic, lightning, fire,
  falling and void still hurt. Missed dodges refund nothing. Hard caps in code: window <= 10 ticks, cooldown >= 8x window
  (<= 12.5% uptime), distance <= 8. The window is an `EffectRegistry` effect, so quit/death/world change/shutdown clear it.
- Evolution: Tomoe 1/2/3 -> Mangekyo -> Eternal improve cooldown (100->60 ticks), window, distance; Eternal costs 30% less.
- Visuals: crimson silhouette + eye flash + trail from vanilla particles (Mangekyo adds a portal swirl), sounds from vanilla.
  No custom assets yet. The old passive random dodge chance (`sharingan.dodge-chance`) is unchanged.

## Verification actually performed (cumulative)
| Check | Result |
|---|---|
| Logic tests (`AllLogicTests`): charge 47, bloodlines 167, migration 26, dodge 66, cast-route guard 2 | **passed** (plain javac, authoring sandbox) |
| Parse-only syntax check, 57 Java files | passed (no symbol resolution) |
| `gradle build` / GitHub Actions | **NOT RUN; project not known to compile** |
| Live server | **none** |

## Groups
| # | Group | State |
|---|---|---|
| 1 | Bloodline registry, eligibility rules, persistence, engine/dojutsu gating, admin tools | **written; pure logic tested; Bukkit side uncompiled** |
| 2 | Five core clans (Uchiha, Hyuga, Senju, Uzumaki, Nara signature abilities) | **in progress** (Uchiha dodge only) |
| 3 | Rare bloodlines (ability sets for ice, lava, ... ) | not started (definitions exist, all disabled, no abilities) |
| 4 | Visual polish, resource-pack assets, sounds | not started |

## Group 1: what exists
- `BloodlineDef`, `BloodlineRegistry`, `BloodlineRules` (pure Java): 22 definitions. Core: Sharingan, Mangekyo, Eternal Mangekyo,
  Byakugan, Wood Release, Sealing Arts, Shadow Manipulation. Optional evolutions (disabled): Rinnegan, Tenseigan.
  Rare (all disabled, none open to all five clans): Ice, Lava, Boil, Magnet, Storm, Scorch, Explosion, Crystal, Dark, Swift,
  Steel, Shikotsumyaku (no clan by default), Dust.
- Rules: enabled -> clan eligible (or owner `any-clan` exception) -> awakened -> earlier evolution stage usable -> unlocked
  (innate bloodlines count as unlocked for their eligible awakened clan).
- Enforcement points: **every cast** in `AbilityEngine.prepare` (jutsu tagged with `.bloodline(id)`: Chakra Chains -> sealing_arts,
  Shadow Bind -> shadow_manipulation, Mangekyo ability -> mangekyo_sharingan) and **dojutsu activation** in `DojutsuManager`.
  Commands, GUI, quick-cast, saved binds and seal combos all reach `AbilityEngine.cast`, so there is one decision point.
  The older clan + awakened check is still in place as well.
- Persistence: `bloodlines` list in the profile; **schema v3** migration seeds it from legacy Mangekyo/Eternal data
  (backup goes to `players/backup/pre-schema-3/`). Legacy Mangekyo/Eternal data is also honoured at runtime.
- Config: `bloodlines:` section (generated to match the code defaults; a script check confirmed they agree). Invalid values log warnings.
- Admin: `/shinobi admin bloodline <player> <list|grant|revoke> [id]` (grant refuses wrong-clan or disabled bloodlines).

## Verification actually performed
| Check | Result |
|---|---|
| `AllLogicTests` (47 charge + 168 bloodline checks: clan lockouts for all 5 clans, evolution chains, disabled-by-default rares, owner any-clan exception, bad-config detection, loop safety) | **passed** (plain javac in authoring sandbox) |
| config.yml bloodline entries vs Java defaults | identical (22/22) |
| Parse-only syntax check, 52 Java files | passed (no symbol resolution) |
| `gradle build` / GitHub Actions | **NOT RUN by the assistant**; no result has been supplied for Stage 1, 2 or 3 |
| In-game / server testing | **none** |

## Requires live-server testing
Gating messages on each path (seal combo, GUI book, quick-cast, bound slot, dojutsu toggle), admin grant/revoke,
schema v3 migration on real profiles, config reload, tab completion.

## Not done in this group
No new abilities, no resource-pack assets (bloodline icons, textures, sounds are group 4), no rare-bloodline unlock requirements
beyond admin grant, no region-protection probes.

---
## Iteration 0.12.0-stage3d summary

| Item | State | Evidence |
|---|---|---|
| Headband as cosmetic gear over any helmet (ItemDisplay, element-geometry model) | source + 12 headgear models | geometry/UV static checks pass; orthographic render shows band, plate, knot, eyes; **not seen in a Minecraft client** |
| Headband no longer occupies the helmet slot (changed to `LEATHER_HORSE_ARMOR`) | source | parse-only check |
| Right-click to wear / `/shinobi headband` toggle / old-headband upgrade on join | source | not tested |
| 3D chakra orb while charging (ItemDisplay, element-geometry) | source + 4 orb models | geometry checks pass; **not in-game** |
| Orb cleanup on release, cancel, interrupt, shutdown | source | not tested |
| Susanoo: pure state machine (`SusanooState`), compatibility table (`AbilityCompat`) | source | 82 scenario checks pass (incl. collapse, forced cleanup, 7 scenarios with independent cooldowns and ledgers) |
| Susanoo: only `kamui` blocked by default; everything else castable inside Susanoo | config + source | independence guard (mutation-checked) confirms SusanooManager cannot touch `.cooldowns` |
| Mangekyo multi-power (`mangekyo-powers` list, schema v5) | source | 38 migration checks pass including v4->v5 seeding |
| Admin: per-ability / per-bloodline forced access, schema v4 | source | 109 admin checks pass |
| HeadgearRules | source | 11 checks pass |
| Logic-test total | | 555 checks, all pass |
| `gradle build` / Actions | | **NOT RUN** |
| Any Bukkit-facing code tested in a server | | **NONE** |
