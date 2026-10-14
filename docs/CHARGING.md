# Charging system (Stage 2, Paper 1.21.11)

## How a charge works
1. You cast with the normal seal combo (or Shift + number quick-cast). The **AbilityEngine** validates everything
   (learned, nature, clan, progression, cooldown, chakra) exactly as for any jutsu.
2. If the jutsu is chargeable, the engine **reserves** its full chakra cost and hands the session to the **ChargeManager**.
   The cooldown does not start yet.
3. While charging: particles gather and spiral toward your hand (mouth for Fire), the action bar shows the stage and state,
   sounds rise per stage, and optional Slowness restricts movement.
4. A **release trigger** fires the technique. At or above the minimum charge the technique resolves once;
   the reserved chakra is committed and the cooldown starts. Damage/range scale between the minimum and full charge.
5. Anything else ends the charge without firing and returns a configured fraction of the reserved chakra
   (`charging.refund.*`). A short anti-spam lockout (`cancel-cooldown-ms`) applies to player-caused endings.

## Controls (server-side only; Paper cannot read arbitrary keys)
| Trigger | Meaning |
|---|---|
| `SWING` | left-click / arm swing |
| `SWAP_HAND` | the swap-hand key (F by default) |
| `SNEAK` | pressing sneak |
Defaults: release = SWING, cancel = SNEAK. Change `charging.release-triggers` / `charging.cancel-triggers`.
Releases in the first `grace-ticks` are ignored so a stray click does not waste the charge.
Not implemented: a held "ability item" activation mode, and any claim of custom keybinds.

## What ends a charge (no player is ever left charging)
| Cause | Refund key |
|---|---|
| Cancel trigger | `cancelled` |
| Release before minimum (`early-release: FIZZLE`) | `fizzled` |
| Release with no valid target / failed effect | `failed-release` |
| Damage >= `interrupt-damage-threshold`, inventory opened, teleport, game-mode change | `interrupted` |
| Death, quit, world change, shutdown, admin `clearfx`, effect-limit | `forced` |
| Full charge held longer than `hold-limit-ticks` | auto-release (or cancel via `overcharge-action`) |
Each session is removed from the manager before it is settled and its chakra lives in a `ChargeLedger` that can settle only
once, so a charge cannot resolve twice or refund/spend twice. Slowness is a short self-expiring effect refreshed each tick.

## Techniques in this release
| Technique | Seals (default) | Charge (ticks) | Release |
|---|---|---|---|
| Chidori | Ox, Hare | 30-70, Slowness II | forward dash, one hit per cast, wall check before dashing |
| Rasengan | Ox, Ox | 20-50, Slowness I | close-range hit on a target within reach (miss = fizzle + refund) |
| Rasenshuriken | Snake, Ox | 50-100, Slowness III; needs Wind nature and 30 Rasengan casts | bounded projectile, capped burst radius, no block damage |
| Great Fireball | Tiger, Horse | 15-45, no slowness | fireball scaled by charge; sets entities alight only, no block fire |

## Not in this release (Stage 2 continues)
Amaterasu, Kamui, Susanoo charge sequences, and the Hyuga / Senju / Uzumaki / Nara charge behaviours are **not implemented**.
Dedicated charge HUD art, boss bar, and an ability-item activation mode are not implemented either.

## Protection plugins
No WorldGuard dependency exists. Damage uses normal damage events, so a protection plugin that cancels PvP damage will stop it
(never tested). The four techniques here do not change blocks, so there is no block-protection probe yet.
