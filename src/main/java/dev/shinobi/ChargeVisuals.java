package dev.shinobi;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared chakra-gathering visuals and sounds. Everything is server-side particles and sounds: it converges on an
 * approximate hand (or mouth) position computed from the player's view, it is NOT a model attached to the player's hand.
 * Particle counts are bounded and scaled by {@code charging.visuals.particle-density} and a per-server session budget.
 */
public final class ChargeVisuals {
    public enum Cue { START, STAGE, FULL, LOOP, RELEASE, CANCEL, FIZZLE }

    private record Style(Particle accent, int low, int high, int baseCount, boolean mouth,
                         Sound start, Sound loop, Sound release, float loopVolume) {}

    private static final Style DEFAULT = new Style(Particle.END_ROD, 0x9fd0ff, 0xffffff, 3, false,
            Sound.BLOCK_BEACON_ACTIVATE, Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.ENTITY_GENERIC_EXPLODE, 0.3f);

    private static final Map<String, Style> STYLES = Map.of(
            "lightning", new Style(Particle.ELECTRIC_SPARK, 0x5f9bff, 0xffffff, 4, false,
                    Sound.BLOCK_BEACON_ACTIVATE, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.12f),
            "rasengan", new Style(Particle.END_ROD, 0x3fa9e8, 0xd8f6ff, 3, false,
                    Sound.BLOCK_BEACON_ACTIVATE, Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.ENTITY_GENERIC_EXPLODE, 0.35f),
            "wind", new Style(Particle.CLOUD, 0x7fd8ff, 0xffffff, 4, false,
                    Sound.ITEM_ELYTRA_FLYING, Sound.ITEM_ELYTRA_FLYING, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.25f),
            "fire", new Style(Particle.SMALL_FLAME, 0xff5a00, 0xffe08a, 4, true,
                    Sound.ITEM_FIRECHARGE_USE, Sound.BLOCK_FIRE_AMBIENT, Sound.ENTITY_BLAZE_SHOOT, 0.5f));

    private final ShinobiPlugin plugin;
    private final Map<UUID, ItemDisplay> orbs = new HashMap<>(); // at most one spinning orb per charging player

    public ChargeVisuals(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    private Style style(String key) {
        return STYLES.getOrDefault(key, DEFAULT);
    }

    /** Position of the hand (or mouth) in front of the player, from their view direction. */
    public Location anchor(Player p, boolean mouth) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) right = new Vector(1, 0, 0);
        right.normalize();
        if (mouth) return eye.clone().add(dir.multiply(0.45)).add(0, -0.15, 0);
        return eye.clone().add(dir.multiply(0.75)).add(right.multiply(0.4)).add(0, -0.55, 0);
    }

    /** Called every charge tick for one session. {@code budget} (0..1) shrinks the effect when many players are charging. */
    public void tick(Player p, ChargeSession s, double budget) {
        if (!plugin.getConfig().getBoolean("charging.visuals.enabled", true)) return;
        Style st = style(s.spec.style);
        double density = Math.max(0.0, Math.min(2.0, plugin.getConfig().getDouble("charging.visuals.particle-density", 1.0)));
        double f = s.spec.fraction(s.elapsed);
        int stage = s.spec.stageOf(s.elapsed);
        int n = (int) Math.round(st.baseCount * density * budget * (0.5 + 1.5 * f));
        n = Math.max(density * budget > 0.05 ? 1 : 0, Math.min(n, 14)); // hard cap per session per tick
        if (n == 0) return;

        World w = p.getWorld();
        Location hand = anchor(p, st.mouth);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        Color color = lerp(st.low, st.high, f);
        float size = (float) (0.6 + 0.9 * f);
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        double spin = s.elapsed * 0.55;

        for (int i = 0; i < n; i++) {
            double phase = ((s.elapsed * 0.1) + i / (double) n) % 1.0;      // 0 = far, 1 = at the hand
            double radius = 1.25 * (1.0 - phase) + 0.08;
            double a = spin + i * (Math.PI * 2.0 / n) + phase * 2.0;        // spiral inward
            Location at = hand.clone()
                    .add(right.clone().multiply(Math.cos(a) * radius))
                    .add(up.clone().multiply(Math.sin(a) * radius));
            w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
        }
        // Core: grows with charge; accent particles get denser by stage.
        w.spawnParticle(Particle.DUST, hand, 1 + stage, 0.04 + 0.05 * f, 0.04 + 0.05 * f, 0.04 + 0.05 * f, 0, dust);
        if (s.elapsed % 2 == 0) {
            w.spawnParticle(st.accent, hand, Math.max(1, (int) Math.round((1 + stage) * density * budget)), 0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * Spinning 3D chakra orb (custom model {@code orb_<style>}) at the hand/mouth while charging: grows with the charge and
     * spins faster. One ItemDisplay per charging player, removed on release, cancel, interruption and shutdown.
     * Needs the resource pack; without it nothing is spawned (particles still play).
     */
    public void orb(Player p, ChargeSession s) {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", false) || !plugin.getConfig().getBoolean("charging.visuals.orb", true)) return;
        String kind = s.spec.style;
        if (!STYLES.containsKey(kind)) return;
        UUID id = s.owner;
        ItemDisplay d = orbs.get(id);
        Location at = anchor(p, STYLES.get(kind).mouth);
        if (d == null || !d.isValid()) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(new NamespacedKey("shinobi", "orb_" + kind));
            it.setItemMeta(m);
            d = p.getWorld().spawn(at, ItemDisplay.class, e -> {
                e.setItemStack(it);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setBillboard(Display.Billboard.FIXED);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTeleportDuration(2);
                e.setViewRange(0.5f);
                e.setPersistent(false);
            });
            orbs.put(id, d);
        }
        double f = s.spec.fraction(s.elapsed);
        float scale = (float) (0.15 + 0.55 * f);
        float spin = (float) (s.elapsed * (0.35 + 0.5 * f));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(spin, 0.3f, 0.9f, 0.3f), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        d.teleport(at);
    }

    public void removeOrb(UUID id) {
        ItemDisplay d = orbs.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    public void removeAllOrbs() {
        for (UUID id : new ArrayList<>(orbs.keySet())) removeOrb(id);
    }

    public int orbCount() {
        return orbs.size();
    }

    public void sound(Player p, String styleKey, Cue cue, int stage) {
        if (!plugin.getConfig().getBoolean("charging.visuals.sounds", true)) return;
        float vol = (float) Math.max(0.0, Math.min(2.0, plugin.getConfig().getDouble("charging.visuals.sound-volume", 1.0)));
        if (vol <= 0f) return;
        Style st = style(styleKey);
        Location l = p.getLocation();
        switch (cue) {
            case START -> p.getWorld().playSound(l, st.start, 0.6f * vol, 1.6f);
            case STAGE -> p.getWorld().playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f * vol, 0.9f + 0.2f * stage);
            case FULL -> p.getWorld().playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f * vol, 1.9f);
            case LOOP -> p.getWorld().playSound(l, st.loop, st.loopVolume * vol, 1.2f + 0.15f * stage);
            case RELEASE -> p.getWorld().playSound(l, st.release, 0.9f * vol, 1.0f + 0.1f * stage);
            case CANCEL -> p.playSound(l, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f * vol, 0.7f);
            case FIZZLE -> p.playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f * vol, 1.4f);
        }
    }

    private static Color lerp(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return Color.fromRGB(r, g, bl);
    }
}
