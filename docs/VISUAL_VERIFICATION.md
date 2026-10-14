# Visual verification (headband and dojutsu eyes)

**Nothing in this document has been checked in a running Minecraft client.** There are no in-game screenshots.
`docs/previews/eyes_and_headband_schematic.png` is a flat composite of the layer PNGs over a plain 8x8 face, made by
`tools/make_preview.py` to check the pixel art. It is a schematic, not a screenshot, and it cannot show 3D placement.

## What was wrong (root causes found in the source)
| Symptom | Cause |
|---|---|
| Headband looked like a full helmet | `ItemManager.Type.HEADBAND` is a `LEATHER_HELMET`. Worn, the client draws the vanilla leather-helmet armor layer; the custom item model only changed the *inventory icon*. |
| Eyes never appeared on the face | The `eye_*` assets are inventory/GUI icons. Nothing in the plugin or pack ever drew them on a player. A PNG in a resource pack cannot replace eye pixels on a player's skin. |
| "Texture exists" was treated as "feature works" | No check linked an asset to an in-world renderer. |

## What was implemented (server-only, Minecraft 1.21.11)
Custom **equipment assets** (`assets/shinobi/equipment/headband*.json`) plus 64x32 humanoid layer textures
(`textures/entity/equipment/humanoid/headband*.png`). The headband item uses the `equippable` component (head slot) with
`model = shinobi:headband[_style]`. The layer texture is authored at **8x the vanilla 64x32 layout (512x256)** and is transparent except for:
- a narrow cloth band around the forehead (2 texels tall) with stitching, lit/shaded edges and a knot with tails at the back,
- a small bevelled metal forehead plate with rivets and an engraved swirl,
- when a dojutsu is active: **the same eye art as the pack's `eye_*` icons** (`eyes_static()` in `tools/make_pack.py`), pasted 1:1 into a
  16x16-pixel area over each eye (face columns 1-2 and 5-6). Tenseigan has no icon, so it is the Byakugan eye hue-shifted to icy blue (derived, not original art).
  `EYE_Y_NUDGE` / `EYE_TEXEL_Y` in `make_pack.py` fine-tune the vertical position.

`EyeCosmetics` swaps the worn headband's model to match the server-side profile (`EyeStyle` maps clan, active state, tomoe
stage, Mangekyo power, Eternal, Rinnegan, Tenseigan to one of 12 styles). The server stays authoritative; the model is cosmetic.
Because it is ordinary equipment, other players see it too (they need the resource pack).

## Limits (these are inherent to the approach)
- Works only **while the Shinobi Headband is worn** (the head slot is shared with helmets).
- Eyes are the icon art's first still frame (equipment layers cannot animate, so the icons' animation is not shown on the face).
  The 16x16 icon is wider than the vanilla eye, so it also covers the skin's own eye pixels (intended).
- **Unverified assumption:** the layer is 512x256 instead of 64x32. Armor models use normalised UVs, which is why high-resolution armor
  packs work, but this exact file has not been loaded in a client. If it renders wrongly, tell me what you see; the fallback is a 64x32 layer
  with coarse eyes.
- Armor layers render on a head cube about 1 px larger than the skull, so overlay pixels are slightly larger and offset
  relative to the skin's own eyes. Alignment with the unusual skins (slim, custom faces) is **unverified**.
- Without the pack nothing is drawn (the old vanilla helmet look is only used when `resource-pack.enabled` is false).
- Headbands made by older versions are upgraded on join (inventory and head slot).
- Exact custom eye rendering, shaders or animated geometry would need a client mod. **No companion mod exists in this project.**

## Geometry render (not an in-game screenshot)
`docs/previews/headgear_geometry_render.png` was produced by `tools/render_model.py`, an orthographic software rasteriser that
reads the element geometry and UV data from the model JSON and composites the texture pixels. It shows: the cloth band sits at
about the forehead row (y ≈ 9.4-11.0 out of 0-16), the plate is centred on the front face, the knot and two tails are at the
back, and the Sharingan / Byakugan eye quads are placed below the plate at the eye row. This is a shape check, not a rendering
guarantee. Actual in-game appearance depends on the engine, skin type, FOV, and whether `headgear.model-yaw-offset` is correct.

## Asset-to-feature table
| Asset | Loaded by | Used by | Statically checked | In-game |
|---|---|---|---|---|
| `equipment/headband.json` + `humanoid/headband.png` | item `equippable.model` | `ItemManager.create(HEADBAND)`, `EyeCosmetics.apply` | JSON, layer id, 512x256 size | **not checked** |
| `equipment/headband_<style>.json` + PNG (11 styles) | same | `EyeCosmetics.refresh` via `EyeStyle` | same + `EyeStyle.ALL` parity (verify_pack.py) | **not checked** |
| `items/headband.json`, `textures/item/headband.png` | `item_model` component | inventory/hand icon only | yes | **not checked** |

## Required in-game test plan (not performed)
1. Craft a headband; wear it; view from front, side and back (band must not cover the whole head).
2. Activate/deactivate each dojutsu stage; confirm the eye pixels change and clear.
3. Check a second player sees the same; test slim (Alex) and custom skins.
4. Die, relog, switch clan, change stage; confirm the style follows the server state.
5. Wear a different helmet and confirm no eyes are drawn (documented limit).
