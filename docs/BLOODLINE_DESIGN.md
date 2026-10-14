# Bloodline design

- **Registry** (`BloodlineDef`, `BloodlineRegistry`): 22 definitions. Core (innate to their clan): Sharingan (Uchiha), Byakugan (Hyuga),
  Wood Release (Senju), Sealing Arts (Uzumaki), Shadow Manipulation (Nara). Evolutions: Mangekyo, Eternal Mangekyo (need unlock + parent),
  optional Rinnegan and Tenseigan (disabled by default). 13 rare bloodlines ship **disabled** with a narrow, owner-editable clan list
  (suggestions, not canon; Shikotsumyaku has no clan until configured). None is open to all five clans.
- **Natural rules** (`BloodlineRules`): enabled -> clan eligible (or owner `any-clan` exception) -> awakened -> parent stage usable -> unlocked.
- **Overrides** (`AccessRules`, `AccessService`): forced bloodline / forced ability, stored explicitly on the profile; see `docs/ADMIN_COMMANDS.md`.
- **Enforcement points**: `AbilityEngine.prepare` (every cast route) and `DojutsuManager.toggle` (dojutsu activation). `CastRouteGuardTests`
  fails the build if any other source file calls a technique effect directly.
- **Persistence**: `bloodlines`, `forced-bloodlines`, `forced-abilities` in the profile; schema v4; backups in `players/backup/pre-schema-N/`.
- **Config**: `bloodlines:` section overrides the code defaults (a script check confirmed they match); invalid values log warnings.
- **Not built yet**: abilities for Hyuga/Senju/Uzumaki/Nara and for the rare bloodlines; unlock requirements beyond admin grant.
