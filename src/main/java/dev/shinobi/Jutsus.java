package dev.shinobi;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** All built-in techniques. Costs/cooldowns live here; hand seals can be overridden in config.yml. */
public final class Jutsus {
    private Jutsus() {}

    private static Seal[] seals(ShinobiPlugin plugin, String id, Seal... defaults) {
        List<String> override = plugin.getConfig().getStringList("jutsu-seals." + id);
        if (override.isEmpty()) return defaults;
        Seal[] out = new Seal[override.size()];
        for (int i = 0; i < out.length; i++) {
            Seal s = Seal.parse(override.get(i));
            if (s == null) return defaults;
            out[i] = s;
        }
        return out;
    }

    public static void registerAll(ShinobiPlugin plugin, JutsuRegistry r) {
        // ---------------- general ninjutsu ----------------
        r.register(Jutsu.builder("great_fireball", "Fire Release: Great Fireball")
                .seals(seals(plugin, "great_fireball", Seal.TIGER, Seal.HORSE))
                .nature(Nature.FIRE).cost(35).cooldown(8000)
                .effect((pl, p, pr) -> ChargeJutsus.fireball(pl, p, 1.0, 1.0))
                .charge(ChargeSpec.builder("fire", 15, 45).slowness(-1).damageScale(0.8, 1.6).rangeScale(0.9, 1.4).build(),
                        ChargeJutsus::fireball)
                .build());

        r.register(Jutsu.builder("chidori", "Chidori")
                .seals(seals(plugin, "chidori", Seal.OX, Seal.HARE))
                .nature(Nature.LIGHTNING).cost(30).cooldown(10000)
                .effect(Jutsus::chidori)
                .charge(ChargeSpec.builder("lightning", 30, 70).slowness(1).damageScale(0.7, 1.5).rangeScale(0.8, 1.3).build(),
                        ChargeJutsus::chidori)
                .build());

        r.register(Jutsu.builder("substitution", "Substitution Technique")
                .seals(seals(plugin, "substitution", Seal.RAT, Seal.RAT))
                .cost(20).cooldown(12000)
                .effect(Jutsus::substitution).build());

        r.register(Jutsu.builder("rasengan", "Rasengan")
                .seals(seals(plugin, "rasengan", Seal.OX, Seal.OX))
                .cost(40).cooldown(10000)
                .effect(Jutsus::rasengan)
                .charge(ChargeSpec.builder("rasengan", 20, 50).slowness(0).damageScale(0.7, 1.4).rangeScale(1.0, 1.2).build(),
                        ChargeJutsus::rasengan)
                .build());

        r.register(Jutsu.builder("rasenshuriken", "Wind Release: Rasenshuriken")
                .seals(seals(plugin, "rasenshuriken", Seal.SNAKE, Seal.OX))
                .nature(Nature.WIND).cost(55).cooldown(25000)
                .requires("rasengan", Math.max(0, plugin.getConfig().getInt("charging.jutsu.rasenshuriken.requires-rasengan-casts", 30)))
                .effect((pl, p, pr) -> {
                    Msg.bar(p, "<red>Rasenshuriken needs charging to be enabled.");
                    return false;
                })
                .charge(ChargeSpec.builder("wind", 50, 100).slowness(2).damageScale(0.8, 1.5).rangeScale(0.9, 1.3).build(),
                        ChargeJutsus::rasenshuriken)
                .build());

        // ---------------- clan techniques (no scroll needed, clan must be awakened) ----------------
        r.register(Jutsu.builder("chakra_chains", "Adamantine Chakra Chains")
                .seals(seals(plugin, "chakra_chains", Seal.TIGER, Seal.DRAGON))
                .clan(Clan.UZUMAKI).bloodline("sealing_arts").known(false).cost(30).cooldown(15000)
                .effect(Jutsus::chakraChains).build());

        r.register(Jutsu.builder("white_light_sabre", "White Light Chakra Sabre")
                .seals(seals(plugin, "white_light_sabre", Seal.HARE, Seal.HORSE))
                .clan(Clan.SENJU).known(false).cost(20).cooldown(6000)
                .effect(Jutsus::whiteLightSabre).build());

        r.register(Jutsu.builder("shadow_bind", "Shadow Possession")
                .seals(seals(plugin, "shadow_bind", Seal.RAT, Seal.SNAKE))
                .clan(Clan.NARA).bloodline("shadow_manipulation").known(false).cost(25).cooldown(14000)
                .effect(Jutsus::shadowBind).build());

        // ---------------- more ninjutsu (easy 2-seal combos on keys 1-8; edit in config.yml) ----------------
        r.register(Jutsu.builder("water_dragon", "Water Release: Water Dragon Bullet")
                .seals(seals(plugin, "water_dragon", Seal.DRAGON, Seal.DRAGON))
                .nature(Nature.WATER).cost(40).cooldown(10000)
                .effect(Jutsus::waterDragon).build());

        r.register(Jutsu.builder("water_wall", "Water Release: Water Wall")
                .seals(seals(plugin, "water_wall", Seal.DRAGON, Seal.SNAKE))
                .nature(Nature.WATER).cost(25).cooldown(9000)
                .effect(Jutsus::waterWall).build());

        r.register(Jutsu.builder("earth_wall", "Earth Release: Earth Wall")
                .seals(seals(plugin, "earth_wall", Seal.RAM, Seal.OX))
                .nature(Nature.EARTH).cost(25).cooldown(9000)
                .effect(Jutsus::earthWall).build());

        r.register(Jutsu.builder("wind_blade", "Wind Release: Wind Blade")
                .seals(seals(plugin, "wind_blade", Seal.HORSE, Seal.SNAKE))
                .nature(Nature.WIND).cost(25).cooldown(6000)
                .effect(Jutsus::windBlade).build());

        r.register(Jutsu.builder("great_breakthrough", "Wind Release: Great Breakthrough")
                .seals(seals(plugin, "great_breakthrough", Seal.HORSE, Seal.DRAGON))
                .nature(Nature.WIND).cost(30).cooldown(10000)
                .effect(Jutsus::greatBreakthrough).build());

        r.register(Jutsu.builder("phoenix_flower", "Fire Release: Phoenix Sage Fire")
                .seals(seals(plugin, "phoenix_flower", Seal.TIGER, Seal.RAT))
                .nature(Nature.FIRE).cost(30).cooldown(9000)
                .effect(Jutsus::phoenixFlower).build());

        r.register(Jutsu.builder("shadow_clone", "Shadow Clone Technique")
                .seals(seals(plugin, "shadow_clone", Seal.RAM, Seal.RAM))
                .cost(40).cooldown(20000)
                .effect((pl, p, pr) -> pl.clones().spawn(p, pr)).build());

        r.register(Jutsu.builder("body_flicker", "Body Flicker Technique")
                .seals(seals(plugin, "body_flicker", Seal.RAT, Seal.OX))
                .cost(15).cooldown(4000)
                .effect(Jutsus::bodyFlicker).build());

        // Uchiha: directional Sharingan dodge (double-tap sneak, bound slot or the book). Costs/cooldowns come from the dodge tier.
        r.register(Jutsu.builder("sharingan_dodge", "Sharingan Dodge")
                .clan(Clan.UCHIHA).bloodline("sharingan").known(false).dojutsu(true)
                .cost(plugin.getConfig().getDouble("sharingan.dodge.base-chakra-cost", 8.0)).cooldown(5000)
                .cooldownFor(pr -> plugin.dodges().tierFor(pr).cooldownTicks() * 50L)
                .effect((pl, p, pr) -> pl.dodges().dodge(p, pr)).build());

        // ---------------- chakra-control training (free for everyone, no seals) ----------------
        r.register(Jutsu.builder("wall_walk", "Tree Climbing Practice")
                .known(false).cost(0).cooldown(1000)
                .effect((pl, p, pr) -> pl.training().toggleWall(p, pr)).build());

        r.register(Jutsu.builder("water_walk", "Water Walking Practice")
                .known(false).cost(0).cooldown(1000)
                .effect((pl, p, pr) -> pl.training().toggleWater(p, pr)).build());

        // ---------------- Mangekyou (unique ability is decided by MangekyoManager) ----------------
        r.register(Jutsu.builder("mangekyo_ability", "Mangekyou Technique")
                .seals(seals(plugin, "mangekyo_ability", Seal.HARE, Seal.HARE))
                .clan(Clan.UCHIHA).bloodline("mangekyo_sharingan").known(false).dojutsu(true).cost(50).cooldown(12000)
                .redirect(pr -> pr.mangekyo)   // alias: becomes the caster's primary power, so saved binds and seal combos keep working
                .effect((pl, p, pr) -> false).build());
        // Each Mangekyou power is its own jutsu: own cooldown, own cost, own unlock. Holding Susanoo never blocks the others.
        for (String power : MangekyoManager.ABILITIES) {
            r.register(Jutsu.builder(power, MangekyoManager.display(power))
                    .clan(Clan.UCHIHA).bloodline("mangekyo_sharingan").mangekyoPower(power).known(false).dojutsu(true)
                    .cost(50).cooldown(12000)
                    .effect((pl, p, pr) -> pl.mangekyo().castPower(p, pr, power)).build());
        }
    }

    // =====================================================================================

    private static boolean chidori(ShinobiPlugin plugin, Player p, Profile pr) {
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().normalize();
        Vector vel = dir.clone().multiply(1.6);
        vel.setY(Math.max(0.15, dir.getY() * 0.6));
        p.setVelocity(vel);
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t++;
                if (t > 12 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Location c = p.getLocation().add(0, 1, 0);
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, 25, 0.4, 0.5, 0.4, 0.15);
                for (Entity en : p.getNearbyEntities(1.7, 1.7, 1.7)) {
                    if (Fx.canHurt(plugin, p, en)) {
                        ((LivingEntity) en).damage(14.0, p);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, en.getLocation().add(0, 1, 0), 50, 0.5, 0.7, 0.5, 0.3);
                        w.playSound(en.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.8f);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static boolean substitution(ShinobiPlugin plugin, Player p, Profile pr) {
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && Tag.LOGS.isTagged(it.getType())) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            Msg.bar(p, "<red>You need a log to substitute with.");
            return false;
        }
        Location from = p.getLocation();
        Vector back = from.getDirection().setY(0);
        if (back.lengthSquared() < 0.01) back = new Vector(0, 0, 1);
        back.normalize().multiply(-5);
        Location base = from.clone().add(back);
        Location dest = null;
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            Location cand = base.clone().add(0, dy, 0);
            if (Fx.isSafe(cand)) {
                dest = cand;
                break;
            }
        }
        if (dest == null) {
            Msg.bar(p, "<red>No room to substitute.");
            return false;
        }
        ItemStack stack = inv.getItem(slot);
        Material logType = stack.getType();
        stack.setAmount(stack.getAmount() - 1);
        inv.setItem(slot, stack.getAmount() <= 0 ? null : stack);

        World w = from.getWorld();
        w.spawnParticle(Particle.POOF, from.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.05);
        w.playSound(from, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        Location logLoc = from.getBlock().getLocation().add(0.2, 0, 0.2);
        BlockDisplay log = w.spawn(logLoc, BlockDisplay.class, d -> d.setBlock(logType.createBlockData()));
        runLater(plugin, () -> {
            if (log.isValid()) {
                log.getWorld().spawnParticle(Particle.POOF, log.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.02);
                log.remove();
            }
        }, 40L);

        dest.setYaw(from.getYaw());
        dest.setPitch(from.getPitch());
        p.teleport(dest);
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 4, true, false, false));
        return true;
    }

    private static void runLater(ShinobiPlugin plugin, Runnable r, long ticks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks);
    }

    private static boolean rasengan(ShinobiPlugin plugin, Player p, Profile pr) {
        pr.rasenganUntil = System.currentTimeMillis() + 8000L;
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 2.0f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || System.currentTimeMillis() > pr.rasenganUntil) {
                    cancel();
                    return;
                }
                Location hand = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.9)).add(0, -0.55, 0);
                p.getWorld().spawnParticle(Particle.CLOUD, hand, 6, 0.08, 0.08, 0.08, 0.02);
                p.getWorld().spawnParticle(Particle.END_ROD, hand, 3, 0.1, 0.1, 0.1, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 3L);
        Msg.bar(p, "<aqua>Rasengan charged. Strike within 8 seconds!");
        return true;
    }

    private static boolean chakraChains(ShinobiPlugin plugin, Player p, Profile pr) {
        LivingEntity t = Fx.target(p, 20);
        if (t == null || !Fx.canHurt(plugin, p, t)) {
            Msg.bar(p, "<red>No target in sight.");
            return false;
        }
        World w = p.getWorld();
        Fx.line(w, Particle.END_ROD, p.getEyeLocation().add(0, -0.4, 0), t.getEyeLocation(), 24);
        Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
        if (pull.lengthSquared() > 0.01) {
            pull.normalize().multiply(1.1);
            pull.setY(0.3);
            t.setVelocity(pull);
        }
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 6, true, true, true));
        t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, true, true, true));
        w.playSound(t.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
        return true;
    }

    private static boolean whiteLightSabre(ShinobiPlugin plugin, Player p, Profile pr) {
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        for (int i = -2; i <= 2; i++) {
            Vector d = dir.clone().rotateAroundY(Math.toRadians(i * 25)).multiply(2.4);
            w.spawnParticle(Particle.SWEEP_ATTACK, p.getEyeLocation().add(d), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.END_ROD, p.getEyeLocation().add(d), 6, 0.1, 0.1, 0.1, 0.02);
        }
        for (Entity en : p.getNearbyEntities(4.5, 3, 4.5)) {
            if (!Fx.canHurt(plugin, p, en)) continue;
            Vector to = en.getLocation().toVector().subtract(p.getLocation().toVector());
            if (to.lengthSquared() < 0.01) continue;
            if (dir.angle(to.normalize()) < Math.toRadians(60)) {
                ((LivingEntity) en).damage(10.0, p);
                w.spawnParticle(Particle.CRIT, en.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
            }
        }
        return true;
    }

    private static boolean shadowBind(ShinobiPlugin plugin, Player p, Profile pr) {
        LivingEntity t = Fx.target(p, 12);
        if (t == null || !Fx.canHurt(plugin, p, t)) {
            Msg.bar(p, "<red>No target in sight.");
            return false;
        }
        World w = p.getWorld();
        Fx.line(w, Particle.SMOKE, p.getLocation().add(0, 0.1, 0), t.getLocation().add(0, 0.1, 0), 30);
        t.setVelocity(new Vector(0, 0, 0));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 10, true, true, true));
        w.playSound(t.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.6f, 0.6f);
        return true;
    }

    // =====================================================================================
    // more techniques

    private static boolean waterDragon(ShinobiPlugin plugin, Player p, Profile pr) {
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 1f, 0.6f);
        Fx.beam(plugin, p, p.getEyeLocation().getDirection(), 26, 1.6, 7.0, Particle.SPLASH, 40, 0, 1.2);
        return true;
    }

    private static boolean phoenixFlower(ShinobiPlugin plugin, Player p, Profile pr) {
        Vector base = p.getEyeLocation().getDirection();
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.3f);
        for (int i = -2; i <= 2; i++) {
            Fx.beam(plugin, p, base.clone().rotateAroundY(Math.toRadians(i * 14)), 16, 1.0, 5.0, Particle.FLAME, 12, 80, 0.0);
        }
        return true;
    }

    private static boolean windBlade(ShinobiPlugin plugin, Player p, Profile pr) {
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        Fx.beam(plugin, p, p.getEyeLocation().getDirection(), 16, 1.2, 9.0, Particle.SWEEP_ATTACK, 1, 0, 0.0);
        return true;
    }

    private static boolean greatBreakthrough(ShinobiPlugin plugin, Player p, Profile pr) {
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.7f);
        for (int step = 1; step <= 10; step++) {
            Location loc = p.getEyeLocation().add(dir.clone().multiply(step));
            w.spawnParticle(Particle.CLOUD, loc, 8, 0.15 * step, 0.15 * step, 0.15 * step, 0.1);
        }
        for (Entity en : p.getNearbyEntities(10, 5, 10)) {
            if (!Fx.canHurt(plugin, p, en)) continue;
            Vector to = en.getLocation().toVector().subtract(p.getLocation().toVector());
            if (to.lengthSquared() < 0.01) continue;
            if (dir.angle(to.normalize()) < Math.toRadians(50)) {
                LivingEntity le = (LivingEntity) en;
                le.damage(3.0, p);
                le.setVelocity(dir.clone().multiply(2.0).setY(0.45));
            }
        }
        return true;
    }

    private static boolean earthWall(ShinobiPlugin plugin, Player p, Profile pr) {
        int n = Fx.tempWall(plugin, p, Material.STONE, 2, 3, 160L);
        if (n == 0) {
            Msg.bar(p, "<red>No room for a wall here.");
            return false;
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.6f);
        return true;
    }

    private static boolean waterWall(ShinobiPlugin plugin, Player p, Profile pr) {
        int n = Fx.tempWall(plugin, p, Material.LIGHT_BLUE_STAINED_GLASS, 2, 3, 120L);
        if (n == 0) {
            Msg.bar(p, "<red>No room for a wall here.");
            return false;
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f);
        return true;
    }

    private static boolean bodyFlicker(ShinobiPlugin plugin, Player p, Profile pr) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult br = w.rayTraceBlocks(eye, dir, 8.0, FluidCollisionMode.NEVER, true);
        double max = br == null ? 8.0 : Math.max(0.0, br.getHitPosition().distance(eye.toVector()) - 1.0);
        Location dest = null;
        for (double d = max; d >= 2.0; d -= 1.0) {
            Location cand = p.getLocation().add(dir.clone().multiply(d));
            if (Fx.isClear(cand)) {
                dest = cand;
                break;
            }
        }
        if (dest == null) {
            Msg.bar(p, "<red>No room to flicker.");
            return false;
        }
        w.spawnParticle(Particle.POOF, p.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());
        p.teleport(dest);
        p.setFallDistance(0f);
        w.spawnParticle(Particle.POOF, dest.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.8f);
        return true;
    }
}
