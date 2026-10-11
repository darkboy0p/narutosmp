package dev.shinobi;

import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Unique-per-server Mangekyou abilities: each ability has at most one owner. */
public final class MangekyoManager {
    public static final List<String> ABILITIES = List.of("amaterasu", "tsukuyomi", "kamui", "susanoo");

    private final ShinobiPlugin plugin;
    private final File file;
    private final Map<String, UUID> owners = new HashMap<>();
    private final Random random = new Random();

    public MangekyoManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mangekyo.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        for (String a : ABILITIES) {
            String s = c.getString("owners." + a);
            if (s == null) continue;
            try {
                owners.put(a, UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        for (Map.Entry<String, UUID> e : owners.entrySet()) c.set("owners." + e.getKey(), e.getValue().toString());
        try {
            c.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save mangekyo.yml: " + e.getMessage());
        }
    }

    public UUID ownerOf(String ability) {
        return owners.get(ability);
    }

    public List<String> freeAbilities() {
        List<String> free = new ArrayList<>();
        for (String a : ABILITIES) if (!owners.containsKey(a)) free.add(a);
        return free;
    }

    public static String display(String ability) {
        if (ability == null) return "None";
        return switch (ability) {
            case "amaterasu" -> "Amaterasu";
            case "tsukuyomi" -> "Tsukuyomi";
            case "kamui" -> "Kamui";
            case "susanoo" -> "Susanoo";
            default -> ability;
        };
    }

    /** Assigns an ability if it is free and the player has none. Returns false if refused. */
    public boolean assign(Player p, Profile pr, String ability) {
        if (!ABILITIES.contains(ability) || owners.containsKey(ability) || pr.mangekyo != null) return false;
        pr.mangekyo = ability;
        owners.put(ability, pr.id);
        save();
        return true;
    }

    public void revoke(Profile pr) {
        if (pr.mangekyo != null) {
            owners.remove(pr.mangekyo);
            pr.mangekyo = null;
            save();
        }
    }

    /** A bonded friend died near this holder: awaken the Mangekyou if eligible. */
    public void tryAwaken(Player holder, String fallenName) {
        Profile pr = plugin.profiles().get(holder);
        if (pr.clan != Clan.UCHIHA || !pr.awakened || pr.mangekyo != null) return;
        List<String> free = freeAbilities();
        if (free.isEmpty()) {
            Msg.send(holder, "<gray>Your grief runs deep, but every Mangekyou power is already claimed.");
            return;
        }
        String ability = free.get(random.nextInt(free.size()));
        if (!assign(holder, pr, ability)) return;
        holder.showTitle(Title.title(Msg.mm("<dark_red>Mangekyou Sharingan"),
                Msg.mm("<gray>" + fallenName + "'s loss awakens <red>" + display(ability))));
        holder.getWorld().playSound(holder.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);
        holder.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, holder.getEyeLocation(), 60, 0.4, 0.4, 0.4, 0.1);
        holder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false, false));
    }

    /** Owner died: apply the configured rule (return | transfer | keep). */
    public void onOwnerDeath(Player victim, Player killer) {
        Profile pr = plugin.profiles().get(victim);
        if (pr.mangekyo == null) return;
        String rule = plugin.getConfig().getString("mangekyo.owner-death", "return");
        if (rule.equalsIgnoreCase("keep")) return;
        String ability = pr.mangekyo;
        revoke(pr);
        pr.dojutsuActive = false;
        Msg.send(victim, "<dark_red>Your Mangekyou fades with your death.");
        if (rule.equalsIgnoreCase("transfer") && killer != null && !killer.equals(victim)) {
            Profile kp = plugin.profiles().get(killer);
            if (kp.clan == Clan.UCHIHA && kp.awakened && assign(killer, kp, ability)) {
                Msg.send(killer, "<dark_red>You claim the power of <red>" + display(ability) + "<dark_red>.");
            }
        }
    }

    /** Casts the holder's unique ability. Returns true if it fired. */
    public boolean cast(Player p, Profile pr) {
        if (pr.mangekyo == null) {
            Msg.bar(p, "<red>You have not awakened a Mangekyou.");
            return false;
        }
        World w = p.getWorld();
        switch (pr.mangekyo) {
            case "amaterasu" -> {
                LivingEntity t = Fx.target(p, 25);
                if (t == null || !Fx.canHurt(plugin, p, t)) {
                    Msg.bar(p, "<red>No target in sight.");
                    return false;
                }
                t.setFireTicks(400);
                w.playSound(t.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
                new BukkitRunnable() {
                    int n = 0;

                    @Override
                    public void run() {
                        n++;
                        if (n > 12 || !t.isValid()) {
                            cancel();
                            return;
                        }
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, t.getLocation().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 0.03);
                        t.damage(2.0, p); // black flames ignore water: keeps burning
                    }
                }.runTaskTimer(plugin, 10L, 10L);
                addStrain(pr, 25);
            }
            case "tsukuyomi" -> {
                LivingEntity t = Fx.target(p, 25);
                if (t == null || !Fx.canHurt(plugin, p, t)) {
                    Msg.bar(p, "<red>No target in sight.");
                    return false;
                }
                t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 120, 0));
                t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0));
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 3));
                t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 1));
                w.spawnParticle(Particle.SMOKE, t.getEyeLocation(), 40, 0.4, 0.4, 0.4, 0.02);
                w.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.7f);
                addStrain(pr, 20);
            }
            case "kamui" -> {
                Vector dir = p.getEyeLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = p.getEyeLocation().getDirection();
                dir.normalize();
                Location dest = null;
                for (int d = 12; d >= 3; d--) {
                    Location cand = p.getLocation().clone().add(dir.clone().multiply(d));
                    if (Fx.isSafe(cand)) {
                        dest = cand;
                        break;
                    }
                }
                if (dest == null) {
                    Msg.bar(p, "<red>Nowhere to phase to.");
                    return false;
                }
                w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.6);
                dest.setYaw(p.getLocation().getYaw());
                dest.setPitch(p.getLocation().getPitch());
                p.teleport(dest);
                w.spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 80, 0.4, 0.8, 0.4, 0.6);
                w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, true, false, false));
                addStrain(pr, 15);
            }
            case "susanoo" -> {
                return plugin.susanoo().advance(p, pr);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void addStrain(Profile pr, double amount) {
        if (!pr.eternal) pr.strain += amount;
    }
}
