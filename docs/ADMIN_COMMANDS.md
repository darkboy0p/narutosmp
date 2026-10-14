# Admin commands and permissions

All of this is **source-complete but unbuilt and untested in-game.** The decision logic (permission map, argument validation,
forced-access rules, audit entries, schema migration) is covered by passing pure-logic tests.

## Permissions
| Node | Opens |
|---|---|
| `shinobismp.admin` | everything below (declared as the parent of all nodes in `plugin.yml`); also `debug`, `clearfx` and unknown subcommands |
| `shinobismp.admin.clan` | `/bind`, `admin clan` |
| `shinobismp.admin.bloodline` | `admin bloodline ...` |
| `shinobismp.admin.abilities` | `admin ability`, `access`, `learn`, `forget`, `scroll`, `mastery`, `mangekyo` |
| `shinobismp.admin.progression` | `admin awaken`, `level`, `eternal`, `roll`, `nature` |
| `shinobismp.admin.chakra` | `admin chakra`, `refill` |
| `shinobismp.admin.cooldowns` | `admin cooldown` |
| `shinobismp.admin.data` | `admin info`, `reset`, `restore` |
| `shinobismp.admin.reload` | `admin reload` |
| `shinobismp.admin.audit` | `admin audit` |
The legacy `shinobi.admin` (op) is now the parent of `shinobismp.admin`. Before this change every admin command shared one node.
To split duties, grant individual nodes with a permissions plugin instead of `shinobismp.admin`.

## The four kinds of access
| Kind | How it is stored | Notes |
|---|---|---|
| Natural | clan + awakening + bloodline rules | enforced for everyone |
| Unlocked | `bloodlines` list, learned scrolls (`known`) | earned or granted normally |
| Admin-granted | `admin bloodline grant`, `admin ability grant` | refuses wrong-clan/disabled bloodlines |
| Admin-forced | `forced-bloodlines`, `forced-abilities` on the profile | explicit, persistent, never pretends to be natural |
Forced access is checked in **one place** (`AccessRules` via `AbilityEngine.prepare`), so commands, GUI, seal combos, quick-cast
and bound slots all honour it. A forced *ability* skips only the access checks (clan, bloodline, learned, nature, prerequisite jutsu)
for that ability. A forced *bloodline* skips clan and bloodline checks for abilities tagged with that bloodline only. Neither skips
chakra, cooldowns, sealing, active-dojutsu requirement, world rules, PvP or destination checks (two config switches can relax chakra
and cooldowns for forced abilities; defaults keep them). Forced grants survive relogs, restarts, reloads, migrations and clan changes
(there is no option to drop them on clan change). Caveat: some techniques have internal assumptions (e.g. `mangekyo_ability` needs a
chosen Mangekyo power) and may still refuse after access is forced.

## Implemented commands (`/shinobi admin ...`; targets: exact online name, or UUID; offline only if `admin.allow-offline-edits`)
```text
bloodline <player> list | eligibility <id> | grant <id> | force <id> | revoke <id> | clear [confirm]
ability   <player> list | grant <id> | force <id> | revoke <id> | clear [confirm] | cooldown <id> <seconds 0-86400> | resetcooldowns
access    <player> <ability-id>        explains exactly why an ability is allowed or denied
audit [1-50]                            recent audit entries
```
- `grant` (bloodline) = normal grant: allowed only for an enabled bloodline whose clan list includes the player's clan.
  `force` = stored override regardless of clan, unlock or the disabled-by-default flag.
- `revoke` removes the stored and forced grant for that one id. Innate bloodlines and legacy Mangekyo stages are not stored grants.
- `clear` needs the word `confirm` (config `admin.require-confirmation`). Ability `clear` removes forced abilities only; learned scrolls stay.
- `ability grant` = learn (only for abilities that need learning). `ability cooldown` is a one-off change of the current cooldown.
- Ids are validated (`[a-z0-9_]{1,48}`), numbers must be finite and in range, unknown abilities/bloodlines are rejected.

## Existing commands kept from earlier versions
`clan <player> <clan|none>` (assigns any clan with no selection requirements, so it behaves as a force-assign; `none` resets),
`learn`, `forget`, `scroll`, `item`, `chakra <player> <max>`, `refill`, `cooldown`, `awaken`, `level`, `eternal`, `mastery`, `mangekyo`,
`roll`, `nature`, `info`, `reset`, `reload`, `debug`, `clearfx`, `restore <uuid>` (restores the `.bak` profile copy; offline player).
**These legacy commands are permission-gated per subcommand now but are NOT written to the audit log yet.**

## Audit log
`plugins/ShinobiSMP/audit/audit.jsonl`, one JSON object per line: time, admin name/uuid, target name/uuid, action, previous, new,
forced, success, reason. Rotates at `admin.audit.max-bytes`, keeps `admin.audit.keep-files` old files; failures are reported without
blocking the command. Nothing but the listed fields is logged.

## NOT implemented from the admin specification
`admin clan list|info|force` as separate subcommands, `admin dojutsu ...`, `admin evolution ...`, `admin chakra get|set|add|remove`
(only the legacy max-chakra setter exists), `admin inspect|stats|progression`, named `admin backup`/`restore <backup-id>`
(only the `.bak` restore exists), the admin GUI, audit coverage of legacy commands, and the "overrides survive clan changes" option.
