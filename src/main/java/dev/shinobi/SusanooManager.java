package dev.shinobi;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Susanoo in four stages: ribcage, skeleton, armor, complete.
 * The body is built from BlockDisplay entities that follow the owner.
 */
public final class SusanooManager {
    private record Part(double right, double up, double fwd, float sx, float sy, float sz, Material mat, int stage) {}

    private static final Material GLASS = Material.PURPLE_STAINED_GLASS;
    private static final Material ARMOR = Material.MAGENTA_STAINED_GLASS;
    private static final Material BLADE = Material.LIGHT_BLUE_STAINED_GLASS;

    private static final List<Part> PARTS = buildParts();

    private static List<Part> buildParts() {
        List<Part> l = new ArrayList<>();
        // Stage 1: ribcage and spine
        for (int i = 0; i < 4; i++) {
            double up = 0.8 + 0.28 * i;
            l.add(new Part(-0.5, up, 0.0, 0.10f, 0.12f, 0.90f, GLASS, 1));
            l.add(new Part(0.5, up, 0.0, 0.10f, 0.12f, 0.90f, GLASS, 1));
            l.add(new Part(0.0, up, -0.42, 1.10f, 0.12f, 0.10f, GLASS, 1));
        }
        l.add(new Part(0.0, 1.35, -0.45, 0.14f, 1.30f, 0.14f, GLASS, 1));
        // Stage 2: skull, arms, shoulders
        l.add(new Part(0.0, 2.45, 0.0, 0.80f, 0.80f, 0.80f, GLASS, 2));
        l.add(new Part(-1.0, 1.3, 0.1, 0.25f, 1.40f, 0.25f, GLASS, 2));
        l.add(new Part(1.0, 1.3, 0.1, 0.25f, 1.40f, 0.25f, GLASS, 2));
        l.add(new Part(-0.75, 1.95, 0.0, 0.50f, 0.25f, 0.50f, GLASS, 2));
        l.add(new Part(0.75, 1.95, 0.0, 0.50f, 0.25f, 0.50f, GLASS, 2));
        // Stage 3: armor plates and helmet
        l.add(new Part(-1.0, 2.05, 0.0, 0.80f, 0.45f, 0.80f, ARMOR, 3));
        l.add(new Part(1.0, 2.05, 0.0, 0.80f, 0.45f, 0.80f, ARMOR, 3));
        l.add(new Part(0.0, 1.35, 0.5, 1.20f, 1.00f, 0.14f, ARMOR, 3));
        l.add(new Part(0.0, 2.45, 0.0, 1.00f, 1.00f, 1.00f, ARMOR, 3));
        // Stage 4: wings, crest, sword
        l.add(new Part(-1.3, 1.8, -0.7, 0.18f, 2.00f, 1.10f, ARMOR, 4));
        l.add(new Part(1.3, 1.8, -0.7, 0.18f, 2.00f, 1.10f, ARMOR, 4));
        l.add(new Part(0.0, 3.1, 0.0, 0.20f, 0.80f, 0.20f, ARMOR, 4));
        l.add(new Part(1.4, 1.3, 0.9, 0.14f, 2.40f, 0.14f, BLADE, 4));
        return List.copyOf(l);
    }

    private static final class State {
        final SusanooState model = new SusanooState();
        EffectRegistry.Handle handle;      // Susanoo's OWN cleanup handle: ending it never touches charges or other effects
        final List<BlockDisplay> displays = new ArrayList<>();
        final List<Part> parts = new ArrayList<>();
    }

    private final ShinobiPlugin plugin;
    private final Map<UUID, State> active = new HashMap<>();

    public SusanooManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, State> en : new ArrayList<>(active.entrySet())) {
                    Player p = plugin.getServer().getPlayer(en.getKey());
                    if (p == null || !p.isOnline()) continue;
                    State s = en.getValue();
                    for (int i = 0; i < s.displays.size(); i++) {
                        BlockDisplay d = s.displays.get(i);
                        if (d.isValid()) d.teleport(place(p, s.parts.get(i)));
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public boolean isActive(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public int stage(Player p) {
        State s = active.get(p.getUniqueId());
        return s == null ? 0 : s.model.stage();
    }

    /** Ability ids refused while Susanoo is active (config susanoo.incompatible-abilities). Everything else stays castable. */
    public java.util.Set<String> incompatible() {
        return AbilityCompat.normalise(plugin.getConfig().isList("susanoo.incompatible-abilities")
                ? plugin.getConfig().getStringList("susanoo.incompatible-abilities") : AbilityCompat.SUSANOO_DEFAULT);
    }

    private static boolean holdsSusanoo(Profile pr) {
        return pr.mangekyoPowers.contains("susanoo") || pr.forcedAbilities.contains("susanoo");
    }

    /** First cast starts Susanoo; each further cast adds a stage; a cast at the last stage dismisses it. */
    public boolean advance(Player p, Profile pr) {
        UUID id = p.getUniqueId();
        State s = active.get(id);
        if (s == null) {
            State fresh = new State();
            EffectRegistry.Handle h = plugin.effects().begin(id, "susanoo", () -> teardown(id)); // quit/death/world change/shutdown
            if (h == null) {
                Msg.bar(p, "<red>Too many active effects.");
                return false;
            }
            fresh.handle = h;
            active.put(id, fresh);
            s = fresh;
        }
        SusanooState.Advance result = s.model.advance();
        if (result == SusanooState.Advance.DISMISSED) {
            end(p);
            return true;
        }
        int stage = s.model.stage();
        for (Part part : PARTS) {
            if (part.stage() == stage) spawn(p, s, part);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 0.5f + 0.1f * stage);
        Msg.bar(p, "<dark_purple>Susanoo: " + SusanooState.STAGE_NAMES[stage]);
        applyEffects(p, s);
        return true;
    }

    private void spawn(Player p, State s, Part part) {
        BlockDisplay d = p.getWorld().spawn(place(p, part), BlockDisplay.class, e -> {
            e.setBlock(part.mat().createBlockData());
            e.setTransformation(new Transformation(
                    new Vector3f(-part.sx() / 2f, -part.sy() / 2f, -part.sz() / 2f),
                    new AxisAngle4f(),
                    new Vector3f(part.sx(), part.sy(), part.sz()),
                    new AxisAngle4f()));
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTeleportDuration(2);
            e.setPersistent(false);
        });
        s.displays.add(d);
        s.parts.add(part);
    }

    private static Location place(Player p, Part d) {
        Location base = p.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Location loc = base.clone().add(rx * d.right() + fx * d.fwd(), d.up(), rz * d.right() + fz * d.fwd());
        loc.setYaw(base.getYaw());
        loc.setPitch(0f);
        return loc;
    }

    private void applyEffects(Player p, State s) {
        int amp = SusanooState.resistanceAmplifier(s.model.stage());
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, amp, true, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, amp, true, false, false));
    }

    /** Once per second: upkeep, strain, and keeping the buffs up. Never touches other abilities' chakra reservations or cooldowns. */
    public void secondTick(Player p, Profile pr) {
        State s = active.get(p.getUniqueId());
        if (s == null) return;
        if (!pr.dojutsuActive || !holdsSusanoo(pr)) {
            end(p);
            return;
        }
        SusanooState.Upkeep up = s.model.upkeep(pr.chakra, pr.eternal, plugin.getConfig().getDouble("susanoo.drain-per-stage", 4.0));
        pr.chakra = up.chakra();
        pr.strain += up.strainGain();
        if (up.collapsed()) {
            Msg.bar(p, "<red>Susanoo collapses. You are out of chakra.");
            end(p);
            return;
        }
        applyEffects(p, s);
    }

    /** Removes the displays and forgets the state. Idempotent; called by the registry handle and by end(). */
    private void teardown(UUID id) {
        State s = active.remove(id);
        if (s == null) return;
        s.model.end();
        for (BlockDisplay d : s.displays) if (d.isValid()) d.remove();
    }

    public void end(Player p) {
        State s = active.get(p.getUniqueId());
        if (s == null) return;
        s.handle.end(); // runs teardown() through the registry; only THIS effect is ended
        teardown(p.getUniqueId());
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        Msg.bar(p, "<gray>Susanoo dissipates.");
    }

    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) teardown(id);
    }
}
