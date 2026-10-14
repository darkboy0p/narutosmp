# Data migration, backups and recovery (Stage 1)

## Where data lives
`plugins/ShinobiSMP/players/<uuid>.yml` (one file per player; storage format unchanged, no database).
Other files: `tempblocks.yml` (temporary-block journal), plus the existing Mangekyou/station data files.

## Schema versions
| Version | Meaning |
|---|---|
| 1 (no `schema-version` key) | original v0.6 files |
| 2 | Hatake replaced by Senju; `legacy-clan` and `clan-history` added |
| 3 | `bloodlines` list added, seeded from legacy Mangekyo / Eternal fields (nothing removed); backup in `players/backup/pre-schema-3/` |

## Hatake -> Senju policy
When a profile with `clan: HATAKE` loads:
1. The untouched file is copied to `players/backup/pre-schema-2/<uuid>.yml` (an existing backup is never overwritten).
2. `clan` becomes `SENJU`; `legacy-clan: HATAKE` and one `clan-history` entry are recorded.
3. `awakened`, mastery, known scrolls, missions, bonds, Mangekyou etc. are untouched.
4. The migrated file is then written. Re-running the migration changes nothing.
Admins who type `hatake` in `/bind` or `/shinobi admin clan` get Senju.
If the pre-migration backup cannot be written, the profile loads **read-only** (nothing is saved) and an error is logged.
A file with a newer `schema-version` than the plugin understands is also loaded read-only.

## Saves
Each save is written to `<uuid>.yml.tmp` and moved over `<uuid>.yml` (atomic where the filesystem allows).
The first save of each session keeps the previous file as `<uuid>.yml.bak`.
Unknown keys in a profile are preserved when saving.

## Recovery
- **Corrupt profile:** it is renamed `<uuid>.yml.corrupt-<time>` (never deleted) and `<uuid>.yml.bak` is tried automatically.
- **Roll back a bad migration (manual):** stop the server, copy `players/backup/pre-schema-N/<uuid>.yml` (N = the schema you are migrating to; the Hatake backups are in `pre-schema-2`) over `players/<uuid>.yml`,
  and run the **old** plugin version. The new plugin would simply migrate it again.
- **Restore the `.bak` copy:** with the player offline, `/shinobi admin restore <uuid>`. The current file is kept as `*.before-restore-*`.
- **Temporary blocks after a crash:** restored automatically on next startup from `tempblocks.yml`. If that file was unreadable it is
  renamed `tempblocks.yml.corrupt-*` and leftover walls must be removed by hand.
- **Stuck temporary effects:** none persist across restarts in Stage 1; effects tracked by `EffectRegistry` are cleaned on quit, death,
  world change and shutdown. `/shinobi admin debug <player>` lists them.

## Not covered in Stage 1
Kamui/phase return state, Rebirth Seal logs, and region-protection checks do not exist yet.
