# Compatibility

**ShinobiSMP supports Paper 1.21.11 only.**

| Item | Value |
|---|---|
| Server | Paper 1.21.11 |
| API compiled against | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` |
| Java | 21 (Gradle toolchain + `options.release = 21`) |
| `plugin.yml` `api-version` | `1.21` (loads on 1.21.11; no legacy-material conversion) |
| Resource pack format | 75 (`min_format` = `max_format` = 75), item definitions in `assets/shinobi/items/` + `setItemModel` |
| NMS / reflection | none |
| Other Minecraft versions | **not supported**; no compatibility layer exists |

## Tested versions
| Minecraft / Paper | Compiled | Run on a server | Played in-game |
|---|---|---|---|
| 1.21.11 | **not yet** (waiting on GitHub Actions) | no | no |
| anything else | n/a | n/a | n/a |

This table is updated only with results someone actually obtained.
