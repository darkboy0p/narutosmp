# ShinobiSMP 0.11.0-stage3c (private Paper plugin)

**Supported platform: Paper 1.21.11 only** (Java 21, `paper-api 1.21.11-R0.1-SNAPSHOT`, resource pack format 75).
No other Minecraft version is supported or tested. See `docs/COMPATIBILITY.md`.

## Latest (0.11.0-stage3c)
Worn headband now renders as a forehead protector and shows the active dojutsu using the pack's own eye art (equipment assets; needs the
resource pack and the headband worn); admin override system with a 10-node permission hierarchy, forced bloodlines/abilities and an
audit log. **Not build-verified, not seen in a client.** See `docs/VISUAL_VERIFICATION.md`, `docs/ADMIN_COMMANDS.md`, `ASSET_MANIFEST.md`.

## Stage 3 (in progress): clan bloodlines
Group 2 has started with the Uchiha Sharingan dodge (double-tap sneak). Group 1 (registry) is written: server-side bloodline eligibility for every cast and dojutsu activation, 22 definitions
(rare ones disabled by default), schema v3, `/shinobi admin bloodline`. Abilities, rare-bloodline content and new art come in
later groups. See `docs/STAGE3_STATUS.md`, `PROGRESS.md`, `KNOWN_ISSUES.md`.

## Stage 2 (in progress): charging system
Chakra charging with hand/mouth particles, stages, sounds, release/cancel/interrupt handling and configurable refunds.
Chidori, Rasengan, Rasenshuriken (new) and Great Fireball are chargeable. Amaterasu, Kamui, Susanoo and the other clan
techniques are **not done yet**. Not build-verified, not tested in-game: see `docs/STAGE2_STATUS.md` and `docs/CHARGING.md`.

## Status of this release (Stage 1 of the overhaul)
Stage 1 adds the foundation only: versioned player-data migration with backups and atomic saves, Hatake -> Senju,
a central ability engine and effect-cleanup registry, and crash-safe temporary blocks.
**Build status: NOT YET VERIFIED by a real compile.** See `docs/STAGE1_STATUS.md` for exactly what has and has not been checked.
Nothing has been tested in-game. New gameplay (Sharingan stages, Kamui, Amaterasu, etc.) is NOT part of Stage 1.

## What changed in Stage 1
- **Clans:** Hatake is replaced by **Senju** everywhere. Old Hatake saves are migrated automatically (see `docs/MIGRATION_AND_RECOVERY.md`).
  Senju currently keeps Hatake's White Light Chakra Sabre; Wood Release comes in a later stage.
- **Player data:** files carry a `schema-version`; a copy of every pre-migration file is stored in `players/backup/pre-schema-2/`;
  saves are written to a temp file and moved into place; unknown keys are preserved; corrupt files are quarantined, never deleted.
- **Ability engine:** all casts go through `AbilityEngine` (validation, cooldown reservation, rollback on failure, re-entrancy guard,
  world blocklist). Temporary effects register with `EffectRegistry`, which cleans them up on quit, death, world change and shutdown.
  Existing jutsu have not yet been ported to `EffectRegistry`; they keep their own cleanup (`SusanooManager`, `CloneManager`).
- **Temporary blocks:** journaled to `tempblocks.yml`, restored on startup after a crash, protected from explosions and pistons,
  and never overwrite a block a player has since replaced. Region-protection checks are not included yet.
- **Admin:** `/shinobi admin debug [player]` (metrics) and `/shinobi admin restore <uuid>` (restore a `.bak` profile).
- **Resource pack:** `clan_hatake` -> `clan_senju` (new crest), `pack.mcmeta` pinned to format 75 only, `tools/verify_pack.py` added.


Naruto-style chakra, hand seals, jutsu, clans, dojutsu, Eight Gates, unique Mangekyou, missions and custom crafting
for a private survival SMP.

NOTE: the v0.6 base was written without a compiler. If `gradle build` reports an error, send the error lines. Nothing has been playtested.

## Build (GitHub Actions, no PC setup needed)
Targets Paper 1.21.11 (Java 21). Nothing here has been compiled by me, so the first build is the real test.

1. Create a GitHub repo (private is fine for the plugin).
2. Upload the contents of this folder (Add file > Upload files, drag everything in). Make sure `.github/workflows/build.yml`
   uploads too; if the hidden `.github` folder is skipped, use Add file > Create new file, type `.github/workflows/build.yml`
   as the name and paste the file's contents.
3. Open the Actions tab > "Build ShinobiSMP" > it runs on every push (or press "Run workflow").
4. When it goes green, open the run and download the artifacts: `ShinobiSMP-plugin` (zip containing the jar) and `ShinobiPack`.
5. Put the jar in your server's `plugins/` folder and restart. Config appears in `plugins/ShinobiSMP/config.yml`.
6. If the build is red, open the failed step, copy the error lines and send them to me.

## Easy mode (new in v0.4, on by default; see `easy-mode:` in config.yml)
- Every technique is unlocked (no scrolls needed) and chakra nature no longer blocks anything. Set `unlock-all-jutsu` / `ignore-nature` to false for the old progression.
- All default combos are now **2 seals on keys 1-8 only** (no Shift needed): Substitution 1-1, Body Flicker 1-2, Rasengan 2-2, Chidori 2-4,
  Great Fireball 3-7, Phoenix Sage Fire 3-1, Adamantine Chains 3-5, Water Dragon 5-5, Water Wall 5-6, Great Breakthrough 7-5, Wind Blade 7-6,
  Shadow Possession 1-6, White Light Sabre 4-7, Earth Wall 8-2, Shadow Clone 8-8, Mangekyou 4-4. (Keys: 1 Rat, 2 Ox, 3 Tiger, 4 Hare, 5 Dragon, 6 Snake, 7 Horse, 8 Ram.)
- **`/jutsu`** (or /shinobi > Jutsu Book) opens a book: click any technique to cast it with no seals (`quick-cast`). `/jutsu cast <id>` works for all techniques.
- Clan techniques still need the clan to be awakened (`/bind <player> <clan>`).

## Keybinds, dojutsu, clans (v0.5)
- **Keybinds:** `/jutsu bind [slot 1-9] [jutsu id]`, then hold **Shift + that number key** to cast it (slots with no bind switch normally). `/jutsu unbind [slot]`, `/jutsu binds`.
- **Dojutsu:** **Shift+F** toggles it (Uchiha Sharingan / Hyuga Byakugan), costs chakra every second, night vision + highlights nearby mobs and players (Byakugan 24 blocks, Sharingan 16).
- **Clans:** Uchiha - 20-30% dodge (more tomoe = more dodge), copies jutsu it sees, cheaper fire, Mangekyou + Susanoo. Hyuga - Byakugan, Gentle Fist (bare-hand hit drains 25 chakra and seals jutsu 5s on players; weakens mobs).
  Uzumaki - x1.5 chakra and regen, Adamantine Chakra Chains (3-5). Senju - White Light Chakra Sabre (4-7, cheaper; carried over from the old Hatake clan until Wood Release is added). Nara - Shadow Possession (1-6).
- **Clan levels (all clans):** level 1-3 grows with training (25 / 75 total casts) and scales every clan's perks: Uchiha dodge (tomoe), Hyuga Byakugan range + Gentle Fist, Uzumaki chakra x1.5/1.75/2.0, Senju sabre discount, Nara shadow discount. `/clan` shows yours.
- **Scrolls:** found in loot chests, written at a Scroll Desk, or crafted: Blank Scroll + Ink Sac + 2x catalyst (Fire blaze powder, Water prismarine shard, Wind feather, Lightning lightning rod, Earth clay ball, no nature amethyst shard). `/jutsu info [id]` shows it. Toggle: `scrolls.craftable`.
- **User commands:** `/clan`, `/jutsu` (book), `/jutsu list|info|cast|bind|unbind|binds`, `/dojutsu toggle`, `/gate`, `/bond`, `/mission`.
- **More admin:** `/shinobi admin level|cooldown|refill|mastery|roll|info [player] ...`.
- **Admin overrides:** `/shinobi admin awaken [player] [clan|off]` (sets the clan if needed, no near-death required), `/shinobi admin tomoe [player] [1|2|3|auto]`, `/shinobi admin eternal [player] [on|off]`, `/shinobi admin mangekyo [player] assign [ability]`.

## Basics
- First join: hidden roll for nature and (rarely) a clan. Craft **Chakra Paper** (paper + amethyst shard) to reveal your nature.
- **F** = seal mode (keys 1-8 = seals 1-8, Shift+1-4 = seals 9-12, F again cancels). **Shift+F** = dojutsu. **/shinobi** = menu.
- Learn jutsu from scrolls (found in loot chests, written at a Scroll Desk, or `/shinobi admin scroll`).
- Meditate (sneak, stand still) to regen and grow max chakra. Flowing water doubles the effect (waterfall meditation).

## Clans: `/bind <player> <uchiha|hyuga|uzumaki|senju|nara|none>`
Binds the clan and awakens its abilities. Naturally, clan members awaken at 2 hearts or less.

## Jutsu (seals are editable in config.yml; only Fireball/Chidori are canon-checked)
Great Fireball, Phoenix Sage Fire (Fire) | Water Dragon, Water Wall (Water) | Wind Blade, Great Breakthrough (Wind) |
Chidori (Lightning) | Earth Wall (Earth) | Substitution (needs a log) | Shadow Clone | Rasengan, Body Flicker (no seals:
`/jutsu cast <id>`) | clan: Chakra Chains, White Light Sabre, Shadow Possession | Mangekyou technique.
Free training techniques: Tree Climbing (sneak = climb, release = cling) and Water Walking (`/jutsu cast wall_walk|water_walk`).

## New in v0.2
- **Missions**: place a Mission Board, take up to 3 of today's 6 missions (hunt, gather, delivery, cast, meditate, tree climb,
  water walk, waterfall, dummy, weights). Rewards: ryo coins, chakra growth, sometimes a scroll. Team bonus if a bonded friend is near. `/mission`.
- **Training**: Tree Climbing, Water Walking (frosted-ice trail), `/shinobi weights` (also lowers Eight Gates health drain), Training Dummy.
- **Custom items and recipes** (all unlocked in the recipe book): kunai, shuriken, explosive tag, smoke bomb, soldier pill, ramen,
  healing salve, blank scroll, sealing scroll (portable storage), shinobi headband, chakra blade, training dummy, scroll desk, mission board.
- **Scroll Desk**: write a scroll for a jutsu you know AND have cast 3+ times (blank scroll + ink sac + 15 chakra).
- **Sharingan copy**: an awakened Uchiha with Sharingan on, watching someone cast a scroll jutsu their nature allows, copies it
  for 10 minutes (up to 3 copies, 1.5x cost).
- **Susanoo** in 4 stages (ribcage, skeleton, armor, complete). Cast the Mangekyou technique again to advance; at stage 4 it dismisses.
- **Eternal Mangekyou**: when a bonded friend of a Mangekyou holder dies, they drop their eyes. The holder right-clicks them: no more strain, cheaper and faster techniques.
- **Shadow Clone**: 2-4 Husk clones that fight monsters and vanish when hurt.
- **Resource pack** (optional): item icons and HUD glyphs (seal icons, chakra bar). See below.

## Resource pack (ShinobiPack.zip, 76 item models, 40 animated)
Generated by `python3 tools/make_pack.py` (pure Python, no dependencies). Edit the drawing functions to restyle anything.
Pack format 75 = Minecraft 1.21.11 only (`min_format` = `max_format` = 75). No other version is supported.

| Group | Textures |
|---|---|
| Eyes (animated) | Sharingan 1/2/3 tomoe (spinning), Mangekyou: Amaterasu, Tsukuyomi, Kamui, Susanoo (each its own spinning pattern), Eternal Mangekyou (pulsing), Byakugan (pulsing veins), Rinnegan (spare, unused), plain eye |
| Natures (animated) | Fire, Water, Wind, Lightning, Earth |
| Jutsu scrolls (animated shimmer) | one per technique (18) |
| Clan crests | Uchiha, Hyuga, Uzumaki, Senju, Nara |
| Eight Gates | 8 gate icons, glowing brighter per gate |
| Missions | 10 mission-type icons |
| Items | all custom items; chakra paper, ryo, chakra blade, smoke bomb, explosive tag, sealing scroll and eternal eyes are animated |
| HUD glyphs | 12 seal icons, chakra bar, eye / nature / gate glyphs (static) |

Where they show up: the /shinobi menu (nature, dojutsu eye and clan crest icons), the Scroll Desk and Mission Board,
every scroll item, and the action-bar HUD (seal sequence, chakra bar, your nature, active dojutsu eye, open gate).
The Sharingan tomoe count (1-3) is cosmetic and comes from how much you have trained your jutsu.

Where the pack goes: NOT in `plugins/` and not on the server disk. The server only needs a download link and the SHA-1,
and players' clients download the pack automatically when they join.
1. Host `ShinobiPack.zip` at a direct link. Easiest free way: a GitHub Release. Push a tag (e.g. `git tag v0.3.0 && git push --tags`,
   or create a release with tag v0.3.0 in the web UI) and the workflow attaches the jar and the pack to it. The link is
   `https://github.com/<user>/<repo>/releases/download/v0.3.0/ShinobiPack.zip`. Players cannot download from a private repo,
   so either make the repo public, or keep the code private and put just the pack zip in a separate small public repo's release.
2. The SHA-1 is printed in the workflow run summary (and by `make_pack.py`). It only changes when the art changes.
3. Edit `plugins/ShinobiSMP/config.yml`:
```
resource-pack:
  enabled: true
  url: "https://github.com/<user>/<repo>/releases/download/v0.3.0/ShinobiPack.zip"
  sha1: "<sha1>"
  force: true      # players who decline are kicked, so nobody sees missing-model cubes
```
4. Restart. Use the plugin's setting OR `resource-pack=` in server.properties, not both.
Leave it disabled if players don't have the pack: custom items would show as missing models.
Not possible with a resource pack: brand-new particle types (jutsu effects still use vanilla particles) and animated HUD glyphs
(font textures cannot animate; the animated eyes are on items/menus only).

## Admin: /shinobi admin
`clan, nature, learn, forget, scroll, item, paper, chakra, awaken, reset, mangekyo, reload`
`/shinobi admin item <player> <item> [amount]` gives any custom item (kunai, ryo, scroll_desk, mission_board, ...).

## Known limits
- Earth/Water walls restore after a few seconds; if the server crashes mid-jutsu a wall can stay.
- Water Walking places short-lived frosted ice (it ignores claim plugins).
- Seal sequences other than Great Fireball and Chidori are my placeholders.
- Custom GUI background art is not included (needs in-game alignment testing); menus use vanilla styling plus item icons.
