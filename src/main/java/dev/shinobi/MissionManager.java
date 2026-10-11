package dev.shinobi;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Daily mission board, mission progress and rewards. */
public final class MissionManager implements Listener {
    public record Template(String id, MissionType type, String target, int required,
                           String title, String difficulty, int reward) {}

    private static final List<Template> TEMPLATES = List.of(
            new Template("hunt_zombie", MissionType.HUNT, "ZOMBIE", 10, "Clear out the undead", "D", 12),
            new Template("hunt_skeleton", MissionType.HUNT, "SKELETON", 10, "Thin the skeletons", "D", 12),
            new Template("hunt_spider", MissionType.HUNT, "SPIDER", 8, "Spider infestation", "D", 12),
            new Template("hunt_bandits", MissionType.HUNT, "PILLAGER", 5, "Bandit raid", "C", 30),
            new Template("hunt_witch", MissionType.HUNT, "WITCH", 2, "Hunt the rogue medic-nin", "B", 70),
            new Template("gather_herbs", MissionType.GATHER, "SWEET_BERRIES", 16, "Herbs for the medic", "D", 10),
            new Template("gather_coal", MissionType.GATHER, "COAL", 24, "Coal for the forge", "D", 10),
            new Template("gather_iron", MissionType.GATHER, "IRON_INGOT", 12, "Iron for the smith", "C", 28),
            new Template("gather_crystal", MissionType.GATHER, "AMETHYST_SHARD", 6, "Chakra crystals", "C", 30),
            new Template("delivery_near", MissionType.DELIVERY, "SCROLL", 150, "Deliver a sealed scroll", "C", 35),
            new Template("delivery_far", MissionType.DELIVERY, "SCROLL", 320, "Long-range delivery", "B", 80),
            new Template("cast_8", MissionType.CAST, "ANY", 8, "Ninjutsu practice", "D", 10),
            new Template("cast_20", MissionType.CAST, "ANY", 20, "Ninjutsu drills", "C", 25),
            new Template("meditate_120", MissionType.MEDITATE, "ANY", 120, "Meditate to refine your chakra", "D", 10),
            new Template("meditate_300", MissionType.MEDITATE, "ANY", 300, "Deep meditation", "C", 28),
            new Template("tree_60", MissionType.TREE_CLIMB, "ANY", 60, "Tree climbing training", "D", 12),
            new Template("water_60", MissionType.WATER_WALK, "ANY", 60, "Water walking training", "C", 28),
            new Template("waterfall_90", MissionType.WATERFALL, "ANY", 90, "Waterfall meditation", "C", 30),
            new Template("dummy_30", MissionType.DUMMY, "ANY", 30, "Strike the training dummy", "D", 10),
            new Template("weights_120", MissionType.WEIGHTS, "ANY", 120, "Weighted sprint training", "C", 28)
    );

    private final ShinobiPlugin plugin;

    public MissionManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
    }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    /** Today's board: the same six missions for everyone, rotating each day. */
    public List<Template> board() {
        List<Template> list = new ArrayList<>(TEMPLATES);
        Collections.shuffle(list, new Random(today() * 7919L + 13L));
        return new ArrayList<>(list.subList(0, Math.min(6, list.size())));
    }

    public static String missionId(Template t) {
        return today() + ":" + t.id();
    }

    public static String describe(MissionType type, String target, int required) {
        String pretty = target.toLowerCase(Locale.ROOT).replace('_', ' ');
        return switch (type) {
            case HUNT -> "Defeat " + required + " " + pretty;
            case GATHER -> "Hand in " + required + " " + pretty + " (click the accepted mission)";
            case DELIVERY -> "Carry the sealed scroll about " + required + " blocks to the marked spot";
            case CAST -> "Cast " + required + " jutsu";
            case MEDITATE -> "Meditate for " + required + " seconds";
            case TREE_CLIMB -> "Cling to walls with Tree Climbing for " + required + " seconds";
            case WATER_WALK -> "Walk on water for " + required + " seconds";
            case WATERFALL -> "Meditate in flowing water for " + required + " seconds";
            case DUMMY -> "Hit a training dummy " + required + " times";
            case WEIGHTS -> "Sprint with weights on for " + required + " seconds";
        };
    }

    public static String diffColor(String d) {
        return switch (d) {
            case "A" -> "<red>";
            case "B" -> "<gold>";
            case "C" -> "<yellow>";
            default -> "<green>";
        };
    }

    // ------------------------------------------------------------------ accept / abandon

    /** Returns null on success, otherwise a reason. */
    public String accept(Player p, Profile pr, Template t, Location boardLoc) {
        String id = missionId(t);
        if (pr.completedMissions.contains(id)) return "You already finished that one today.";
        for (MissionState m : pr.missions) if (m.id.equals(id)) return "You already accepted that mission.";
        if (pr.missions.size() >= 3) return "You can hold at most 3 missions.";

        MissionState m = new MissionState();
        m.id = id;
        m.templateId = t.id();
        m.title = t.title();
        m.difficulty = t.difficulty();
        m.target = t.target();
        m.type = t.type();
        m.required = t.required();
        m.reward = t.reward();
        if (t.type() == MissionType.DELIVERY) {
            Random r = new Random(id.hashCode());
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = t.required() * (0.85 + 0.3 * r.nextDouble());
            m.world = boardLoc.getWorld().getName();
            m.destX = (int) (boardLoc.getX() + Math.cos(ang) * dist);
            m.destZ = (int) (boardLoc.getZ() + Math.sin(ang) * dist);
            ItemManager.give(p, plugin.items().missionScroll(m));
        }
        pr.missions.add(m);
        plugin.profiles().save(pr);
        return null;
    }

    public void abandon(Player p, Profile pr, int index) {
        if (index < 0 || index >= pr.missions.size()) {
            Msg.send(p, "<red>No such mission.");
            return;
        }
        MissionState m = pr.missions.remove(index);
        plugin.items().removeOne(p.getInventory(), it -> plugin.items().isTag(it, "mission_scroll:" + m.id));
        Msg.send(p, "<gray>Abandoned <white>" + m.title + "<gray>.");
        plugin.profiles().save(pr);
    }

    // ------------------------------------------------------------------ progress

    public void progress(Player p, Profile pr, MissionType type, String target, int amount) {
        if (pr.missions.isEmpty()) return;
        for (Iterator<MissionState> it = pr.missions.iterator(); it.hasNext(); ) {
            MissionState m = it.next();
            if (m.type != type || type == MissionType.GATHER || type == MissionType.DELIVERY) continue;
            if (!m.target.equals("ANY") && !m.target.equalsIgnoreCase(target)) continue;
            m.progress = Math.min(m.required, m.progress + amount);
            if (m.progress >= m.required) {
                it.remove();
                complete(p, pr, m);
            } else if (!type.timeBased() || m.progress % 10 == 0) {
                Msg.bar(p, "<gold>" + m.title + " <white>" + m.progress + "<gray>/<white>" + m.required);
            }
        }
    }

    /** Hand in gathered items for a GATHER mission. */
    public boolean turnIn(Player p, Profile pr, MissionState m) {
        if (m.type != MissionType.GATHER) return false;
        Material mat = Material.matchMaterial(m.target);
        if (mat == null) return false;
        if (!p.getInventory().containsAtLeast(new ItemStack(mat), m.required)) {
            Msg.send(p, "<red>You need " + m.required + " " + m.target.toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
            return false;
        }
        p.getInventory().removeItem(new ItemStack(mat, m.required));
        pr.missions.remove(m);
        complete(p, pr, m);
        return true;
    }

    /** Called once per second: checks delivery destinations. */
    public void tickDelivery(Player p, Profile pr) {
        if (pr.missions.isEmpty()) return;
        for (Iterator<MissionState> it = pr.missions.iterator(); it.hasNext(); ) {
            MissionState m = it.next();
            if (m.type != MissionType.DELIVERY || m.world == null) continue;
            if (!p.getWorld().getName().equals(m.world)) continue;
            double dx = p.getLocation().getX() - m.destX;
            double dz = p.getLocation().getZ() - m.destZ;
            if (dx * dx + dz * dz > 100.0) continue;
            boolean had = plugin.items().removeOne(p.getInventory(), s -> plugin.items().isTag(s, "mission_scroll:" + m.id));
            if (!had) {
                Msg.bar(p, "<red>You have arrived, but you no longer carry the scroll!");
                continue;
            }
            it.remove();
            complete(p, pr, m);
        }
    }

    private boolean teamNearby(Player p, Profile pr) {
        for (UUID u : pr.bonds) {
            Player b = Bukkit.getPlayer(u);
            if (b != null && b.getWorld().equals(p.getWorld()) && b.getLocation().distance(p.getLocation()) <= 30.0) return true;
        }
        return false;
    }

    private void complete(Player p, Profile pr, MissionState m) {
        pr.completedMissions.add(m.id);
        int reward = m.reward;
        boolean team = teamNearby(p, pr);
        if (team) reward = (int) Math.round(reward * 1.5);
        ItemManager.give(p, plugin.items().create(ItemManager.Type.RYO, reward));

        double growth = switch (m.difficulty) {
            case "A" -> 2.0;
            case "B" -> 1.0;
            case "C" -> 0.6;
            default -> 0.3;
        };
        double cap = plugin.getConfig().getDouble("chakra.max-cap", 400.0);
        pr.maxChakra = Math.min(cap, pr.maxChakra + growth);

        double scrollChance = switch (m.difficulty) {
            case "A" -> 0.5;
            case "B" -> 0.3;
            case "C" -> 0.15;
            default -> 0.0;
        };
        String extra = "";
        if (Math.random() < scrollChance) {
            List<Jutsu> pool = new ArrayList<>();
            for (Jutsu j : plugin.jutsus().all()) if (j.needsKnown && !pr.known.contains(j.id)) pool.add(j);
            if (!pool.isEmpty()) {
                Jutsu j = pool.get(new Random().nextInt(pool.size()));
                ItemManager.give(p, plugin.items().scroll(j));
                extra = " <gray>+ scroll: <white>" + j.name;
            }
        }
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        Msg.send(p, "<green>Mission complete: <white>" + m.title + " <gold>+" + reward + " ryo" + (team ? " <aqua>(team bonus)" : "") + extra);
        plugin.profiles().save(pr);
    }

    // ------------------------------------------------------------------ listener

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        progress(killer, plugin.profiles().get(killer), MissionType.HUNT, e.getEntityType().name(), 1);
    }
}
