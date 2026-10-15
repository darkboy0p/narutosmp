package dev.shinobi;

import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ShinobiListener implements Listener {
    private final ShinobiPlugin plugin;
    private final Random random = new Random();

    public ShinobiListener(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- join / quit

    private static byte[] hex(String s) {
        String h = s.trim();
        if (h.length() % 2 != 0) return null;
        byte[] out = new byte[h.length() / 2];
        try {
            for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().get(p);
        pr.chakra = plugin.chakra().effectiveMax(pr);
        p.discoverRecipes(plugin.items().recipeKeys());

        if (plugin.hud().on()) {
            String url = plugin.getConfig().getString("resource-pack.url", "");
            if (url != null && !url.isBlank()) {
                String sha = plugin.getConfig().getString("resource-pack.sha1", "");
                byte[] hash = (sha == null || sha.isBlank()) ? null : hex(sha);
                p.setResourcePack(url, hash, plugin.getConfig().getBoolean("resource-pack.force", false));
            }
        }

        if (pr.rolled) return;
        pr.rolled = true;
        pr.nature = Nature.random(random);
        pr.known.addAll(plugin.getConfig().getStringList("starter-jutsu"));

        List<Clan> order = new ArrayList<>(List.of(Clan.values()));
        Collections.shuffle(order, random);
        for (Clan c : order) {
            double chance = plugin.getConfig().getDouble("clans.roll-chance." + c.name().toLowerCase(), c.defaultChance);
            if (random.nextDouble() < chance) {
                pr.clan = c;
                break;
            }
        }
        plugin.profiles().save(pr);
        Msg.send(p, "<gray>You feel chakra stirring within you. Craft <white>Chakra Paper<gray> (paper + amethyst shard) to find your nature.");
        Msg.send(p, "<gray>Press <white>F<gray> for hand seals, <white>/shinobi<gray> for your menu.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().get(p);
        pr.gate = 0;
        pr.dojutsuActive = false;
        pr.rasenganUntil = 0;
        plugin.training().stopAll(pr);
        plugin.susanoo().end(p);
        plugin.effects().cleanup(p.getUniqueId(), EffectRegistry.Reason.QUIT);
        plugin.profiles().unload(p.getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.effects().cleanup(e.getPlayer().getUniqueId(), EffectRegistry.Reason.WORLD_CHANGE);
    }

    // ---------------------------------------------------------------- combat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        // Sharingan: chance to dodge incoming hits.
        if (e.getEntity() instanceof Player victim) {
            Profile vp = plugin.profiles().get(victim);
            double dodge = plugin.getConfig().getDouble("sharingan.dodge-chance", 0.20) + 0.05 * (plugin.hud().sharinganStage(vp) - 1);
            if (vp.dojutsuActive && vp.clan == Clan.UCHIHA && ThreadLocalRandom.current().nextDouble() < dodge) {
                e.setCancelled(true);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1.5f);
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.1);
                Msg.bar(victim, "<red>Dodged!");
                return;
            }
        }

        if (!(e.getDamager() instanceof Player atk)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Profile ap = plugin.profiles().get(atk);
        long now = System.currentTimeMillis();
        Entity victimEntity = e.getEntity();

        // Susanoo: heavier melee per stage.
        int stage = plugin.susanoo().stage(atk);
        if (stage > 0) e.setDamage(e.getDamage() + 2.0 * stage);

        // Rasengan: next melee hit.
        if (ap.rasenganUntil > now) {
            ap.rasenganUntil = 0;
            e.setDamage(e.getDamage() + 10.0);
            Vector kb = atk.getLocation().getDirection().setY(0.35).normalize().multiply(1.5);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> victimEntity.setVelocity(kb), 1L);
            victimEntity.getWorld().spawnParticle(Particle.CLOUD, victimEntity.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.2);
            victimEntity.getWorld().playSound(victimEntity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.8f);
            return;
        }

        // Gentle Fist: bare-handed hit with Byakugan active.
        if (ap.dojutsuActive && ap.clan == Clan.HYUGA && atk.getInventory().getItemInMainHand().getType().isAir()) {
            victimEntity.getWorld().playSound(victimEntity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
            victimEntity.getWorld().spawnParticle(Particle.END_ROD, victimEntity.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
            if (victimEntity instanceof Player vic) {
                Profile vp = plugin.profiles().get(vic);
                int lv = plugin.casting().clanLevel(ap);
                vp.chakra = Math.max(0.0, vp.chakra - (25.0 + 10.0 * (lv - 1)));
                vp.sealedUntil = now + 5000L + 2000L * (lv - 1);
                Msg.bar(vic, "<red>Your chakra points have been struck!");
            } else if (victimEntity instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
        }
    }

    /** Clan members awaken their bloodline when pushed to the brink. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Profile pr = plugin.profiles().get(p);
        if (pr.clan == null || pr.awakened) return;
        double threshold = plugin.getConfig().getDouble("awakening.near-death-health", 4.0);
        double after = p.getHealth() - e.getFinalDamage();
        if (after > 0.0 && after <= threshold) {
            awaken(p, pr);
        }
    }

    public void awaken(Player p, Profile pr) {
        pr.awakened = true;
        p.showTitle(Title.title(Msg.mm(pr.clan.color + "Bloodline Awakened"),
                Msg.mm("<gray>" + pr.clan.display + ": " + pr.clan.abilities)));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true));
        plugin.profiles().save(pr);
    }

    // ---------------------------------------------------------------- death

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Profile pr = plugin.profiles().get(victim);

        // Eternal Mangekyou relic: dropped if a bonded friend holds a (non-eternal) Mangekyou.
        for (UUID u : pr.bonds) {
            Profile hp = plugin.profiles().get(u);
            if (hp.mangekyo != null && !hp.eternal) {
                e.getDrops().add(plugin.items().eternalEyes(victim.getUniqueId(), victim.getName()));
                break;
            }
        }

        plugin.susanoo().end(victim);
        plugin.effects().cleanup(victim.getUniqueId(), EffectRegistry.Reason.DEATH);
        plugin.training().stopAll(pr);
        pr.gate = 0;
        pr.dojutsuActive = false;
        pr.rasenganUntil = 0;
        pr.session = null;
        pr.sealedUntil = 0;
        pr.strain = 0;
        pr.cooldowns.clear();
        pr.copied.clear();

        plugin.bonds().onDeath(victim);
        plugin.mangekyo().onOwnerDeath(victim, victim.getKiller());
        pr.chakra = plugin.chakra().effectiveMax(pr) * 0.5;
    }
}
