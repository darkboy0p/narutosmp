package dev.shinobi;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/** All custom items, their crafting recipes and their behaviour. */
public final class ItemManager implements Listener {

    public enum Type {
        CHAKRA_PAPER("chakra_paper", Material.PAPER, "<white>Chakra Paper",
                "<gray>Right-click to channel chakra into it.", "<gray>It reveals your chakra nature."),
        KUNAI("kunai", Material.IRON_NUGGET, "<gray>Kunai",
                "<gray>Right-click to throw.", "<dark_gray>Usually recoverable after a throw."),
        SHURIKEN("shuriken", Material.FLINT, "<gray>Shuriken",
                "<gray>Right-click to throw.", "<dark_gray>Light and fast; sometimes recoverable."),
        EXPLOSIVE_TAG("explosive_tag", Material.PAPER, "<red>Explosive Tag",
                "<gray>Right-click to throw.", "<gray>Detonates on impact."),
        SMOKE_BOMB("smoke_bomb", Material.FIREWORK_STAR, "<gray>Smoke Bomb",
                "<gray>Right-click to throw.", "<gray>Blinds everyone in the cloud."),
        SOLDIER_PILL("soldier_pill", Material.PUMPKIN_SEEDS, "<gold>Soldier Pill",
                "<gray>Right-click: +60 chakra and power.", "<red>Brutal crash after 30 seconds."),
        RAMEN("ramen", Material.MUSHROOM_STEW, "<yellow>Ichiraku Ramen",
                "<gray>Right-click to eat.", "<gray>Restores chakra and hunger."),
        HEALING_SALVE("healing_salve", Material.SLIME_BALL, "<green>Healing Salve",
                "<gray>Right-click to heal."),
        BLANK_SCROLL("blank_scroll", Material.PAPER, "<white>Blank Scroll",
                "<gray>Used at a Scroll Desk to write a jutsu."),
        SEALING_SCROLL("sealing_scroll", Material.PAPER, "<aqua>Sealing Scroll",
                "<gray>Right-click to open and seal items inside."),
        HEADBAND("headband", Material.LEATHER_HELMET, "<blue>Shinobi Headband",
                "<gray>Wear it with pride."),
        RYO("ryo", Material.GOLD_NUGGET, "<gold>Ryo",
                "<gray>Currency of the shinobi world."),
        CHAKRA_BLADE("chakra_blade", Material.IRON_SWORD, "<aqua>Chakra Blade",
                "<gray>Conducts your nature into each strike.", "<dark_gray>Costs 3 chakra per hit."),
        TRAINING_DUMMY("training_dummy", Material.ARMOR_STAND, "<gold>Training Dummy",
                "<gray>Place it, then hit it to train.", "<dark_gray>Sneak + right-click to pick up."),
        SCROLL_DESK("scroll_desk", Material.LECTERN, "<gold>Scroll Desk",
                "<gray>Place it to write jutsu scrolls."),
        MISSION_BOARD("mission_board", Material.CARTOGRAPHY_TABLE, "<gold>Mission Board",
                "<gray>Place it to take daily missions.");

        public final String id;
        public final Material material;
        public final String name;
        public final List<String> lore;

        Type(String id, Material material, String name, String... lore) {
            this.id = id;
            this.material = material;
            this.name = name;
            this.lore = List.of(lore);
        }

        public static Type byId(String s) {
            for (Type t : values()) if (t.id.equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    private final ShinobiPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey projKey;
    private final NamespacedKey dummyKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final Map<UUID, Long> throwCd = new HashMap<>();
    private final Random random = new Random();

    public ItemManager(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "shinobi_item");
        this.projKey = new NamespacedKey(plugin, "shinobi_projectile");
        this.dummyKey = new NamespacedKey(plugin, "training_dummy");
        registerRecipes();
    }

    // ------------------------------------------------------------------ item creation

    private boolean packOn() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", false);
    }

    private void model(ItemMeta m, String name) {
        if (packOn()) m.setItemModel(new NamespacedKey("shinobi", name));
    }

    public ItemStack create(Type t, int amount) {
        ItemStack it = new ItemStack(t.material, amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item(t.name));
        List<Component> lore = new ArrayList<>();
        for (String l : t.lore) lore.add(Msg.item(l));
        m.lore(lore);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, t.id);
        if (t == Type.SEALING_SCROLL) m.setMaxStackSize(1);
        if (t == Type.HEADBAND && m instanceof LeatherArmorMeta lm) lm.setColor(Color.fromRGB(0x2A5DB0));
        model(m, t.id);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack scroll(Jutsu j) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item("<gold>Scroll: <white>" + j.name));
        m.lore(List.of(Msg.item("<gray>Seals: <white>" + j.sealText()),
                Msg.item(j.nature == null ? "<gray>Nature: <white>Any" : "<gray>Nature: " + j.nature.colored()),
                Msg.item("<dark_gray>Right-click to learn")));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "scroll:" + j.id);
        model(m, plugin.hud().scrollModel(j.id));
        it.setItemMeta(m);
        return it;
    }

    public ItemStack missionScroll(MissionState ms) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item("<gold>Sealed Delivery: <white>" + ms.title));
        m.lore(List.of(Msg.item("<gray>Deliver to X: <white>" + ms.destX + " <gray>Z: <white>" + ms.destZ),
                Msg.item("<gray>World: <white>" + ms.world),
                Msg.item("<red>Do not lose it.")));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "mission_scroll:" + ms.id);
        m.setMaxStackSize(1);
        model(m, "mission_scroll");
        it.setItemMeta(m);
        return it;
    }

    public ItemStack eternalEyes(UUID victim, String name) {
        ItemStack it = new ItemStack(Material.ENDER_EYE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item("<dark_red>Eyes of " + name));
        m.lore(List.of(Msg.item("<gray>A bonded Mangekyou holder can"),
                Msg.item("<gray>take these eyes. Right-click to transplant.")));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "eternal_eyes:" + victim);
        m.setMaxStackSize(1);
        model(m, "eternal_eyes");
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ item helpers

    public String tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean isTag(ItemStack it, String t) {
        return t.equals(tag(it));
    }

    /** Removes one matching item from the inventory. */
    public boolean removeOne(Inventory inv, Predicate<ItemStack> pred) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir() || !pred.test(it)) continue;
            if (it.getAmount() <= 1) inv.setItem(i, null);
            else it.setAmount(it.getAmount() - 1);
            return true;
        }
        return false;
    }

    public boolean has(Inventory inv, Predicate<ItemStack> pred) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir() && pred.test(it)) return true;
        }
        return false;
    }

    public static void give(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private static double maxHealth(Player p) {
        AttributeInstance ai = p.getAttribute(Attribute.MAX_HEALTH);
        return ai == null ? 20.0 : ai.getValue();
    }

    private void consume(Player p, ItemStack held) {
        if (p.getGameMode() != GameMode.CREATIVE) held.setAmount(held.getAmount() - 1);
    }

    // ------------------------------------------------------------------ recipes

    public List<NamespacedKey> recipeKeys() {
        return recipeKeys;
    }

    private void shapeless(String key, ItemStack result, Object... ingredients) {
        NamespacedKey k = new NamespacedKey(plugin, key);
        Bukkit.removeRecipe(k);
        ShapelessRecipe r = new ShapelessRecipe(k, result);
        for (Object o : ingredients) {
            if (o instanceof Material m) r.addIngredient(m);
            else if (o instanceof RecipeChoice rc) r.addIngredient(rc);
        }
        Bukkit.addRecipe(r);
        recipeKeys.add(k);
    }

    private void registerRecipes() {
        shapeless("chakra_paper", create(Type.CHAKRA_PAPER, 1), Material.PAPER, Material.AMETHYST_SHARD);
        shapeless("blank_scroll", create(Type.BLANK_SCROLL, 1), Material.PAPER, Material.PAPER, Material.STICK);
        shapeless("explosive_tag", create(Type.EXPLOSIVE_TAG, 2), Material.PAPER, Material.GUNPOWDER, Material.INK_SAC);
        shapeless("smoke_bomb", create(Type.SMOKE_BOMB, 2), Material.GUNPOWDER, Material.COAL, Material.PAPER);
        shapeless("soldier_pill", create(Type.SOLDIER_PILL, 1), Material.GLISTERING_MELON_SLICE, Material.BLAZE_POWDER, Material.SUGAR);
        shapeless("ramen", create(Type.RAMEN, 1), Material.BOWL, Material.WHEAT, Material.WHEAT, Material.COOKED_PORKCHOP, Material.EGG);
        shapeless("healing_salve", create(Type.HEALING_SALVE, 2), Material.SLIME_BALL, Material.SWEET_BERRIES, Material.SWEET_BERRIES, Material.BONE_MEAL);
        shapeless("sealing_scroll", create(Type.SEALING_SCROLL, 1),
                new RecipeChoice.ExactChoice(create(Type.BLANK_SCROLL, 1)), Material.ENDER_PEARL, Material.INK_SAC);
        shapeless("headband", create(Type.HEADBAND, 1), Material.LEATHER, Material.IRON_INGOT, Material.BLUE_DYE);
        shapeless("chakra_blade", create(Type.CHAKRA_BLADE, 1), Material.IRON_SWORD, Material.AMETHYST_SHARD, Material.AMETHYST_SHARD, Material.REDSTONE);
        shapeless("training_dummy", create(Type.TRAINING_DUMMY, 1), Material.HAY_BLOCK, Material.STICK, Material.STICK, Material.LEATHER);
        shapeless("scroll_desk", create(Type.SCROLL_DESK, 1), Material.LECTERN, Material.INK_SAC, Material.PAPER);
        shapeless("mission_board", create(Type.MISSION_BOARD, 1), Material.CARTOGRAPHY_TABLE, Material.PAPER, Material.PAPER, Material.IRON_NUGGET);

        NamespacedKey kk = new NamespacedKey(plugin, "kunai");
        Bukkit.removeRecipe(kk);
        ShapedRecipe kunai = new ShapedRecipe(kk, create(Type.KUNAI, 4));
        kunai.shape("I", "S");
        kunai.setIngredient('I', Material.IRON_INGOT);
        kunai.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(kunai);
        recipeKeys.add(kk);

        NamespacedKey sk = new NamespacedKey(plugin, "shuriken");
        Bukkit.removeRecipe(sk);
        ShapedRecipe shuriken = new ShapedRecipe(sk, create(Type.SHURIKEN, 4));
        shuriken.shape(" N ", "NIN", " N ");
        shuriken.setIngredient('N', Material.IRON_NUGGET);
        shuriken.setIngredient('I', Material.IRON_INGOT);
        Bukkit.addRecipe(shuriken);
        recipeKeys.add(sk);
    }

    public void unregister() {
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
    }

    // ------------------------------------------------------------------ use

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = e.getItem();
        String t = tag(held);
        if (t == null) return;
        // These keep their vanilla behaviour (placing a block, equipping, swinging).
        if (t.equals("scroll_desk") || t.equals("mission_board") || t.equals("headband")
                || t.equals("chakra_blade") || t.equals("ryo") || t.equals("blank_scroll")) return;

        // Let players still open chests, doors, etc. while holding one of these items.
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !e.getPlayer().isSneaking()) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        Profile pr = plugin.profiles().get(p);

        if (t.equals("chakra_paper")) {
            if (pr.nature == null) pr.nature = Nature.random(random);
            if (!pr.natureRevealed) {
                pr.natureRevealed = true;
                consume(p, held);
            }
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.5f);
            p.getWorld().spawnParticle(Particle.CRIT, p.getEyeLocation().add(p.getLocation().getDirection()), 15, 0.2, 0.2, 0.2, 0.05);
            Msg.send(p, "<gray>" + pr.nature.paperEffect + " Your nature is " + pr.nature.colored() + "<gray>.");
            plugin.profiles().save(pr);
        } else if (t.startsWith("scroll:")) {
            learnScroll(p, pr, held, t.substring("scroll:".length()));
        } else if (t.equals("kunai")) {
            throwItem(p, held, "kunai", 1.9);
        } else if (t.equals("shuriken")) {
            throwItem(p, held, "shuriken", 2.2);
        } else if (t.equals("explosive_tag")) {
            throwItem(p, held, "explosive_tag", 1.2);
        } else if (t.equals("smoke_bomb")) {
            throwItem(p, held, "smoke_bomb", 1.1);
        } else if (t.equals("soldier_pill")) {
            consume(p, held);
            soldierPill(p, pr);
        } else if (t.equals("ramen")) {
            consume(p, held);
            pr.chakra = Math.min(plugin.chakra().effectiveMax(pr), pr.chakra + 40.0);
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + 6));
            p.setSaturation(Math.min(20f, p.getSaturation() + 4f));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
            Msg.bar(p, "<yellow>Delicious! <aqua>+40 chakra");
        } else if (t.equals("healing_salve")) {
            consume(p, held);
            p.setHealth(Math.min(maxHealth(p), p.getHealth() + 6.0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 1.2f);
        } else if (t.equals("sealing_scroll")) {
            plugin.sealingGui().open(p, held);
        } else if (t.equals("training_dummy")) {
            placeDummy(e, p, held);
        } else if (t.startsWith("eternal_eyes:")) {
            useEyes(p, pr, held, t.substring("eternal_eyes:".length()));
        } else if (t.startsWith("mission_scroll:")) {
            Msg.send(p, "<gray>Deliver this scroll to the marked coordinates (see its lore).");
        }
    }

    private void learnScroll(Player p, Profile pr, ItemStack held, String id) {
        Jutsu j = plugin.jutsus().get(id);
        if (j == null) {
            Msg.send(p, "<red>This scroll is blank.");
            return;
        }
        if (pr.known.contains(j.id)) {
            Msg.send(p, "<gray>You already know <white>" + j.name + "<gray>.");
            return;
        }
        if (j.nature != null && pr.nature != j.nature) {
            Msg.send(p, "<red>Your chakra doesn't resonate with this scroll (" + j.nature.display + " nature required).");
            return;
        }
        pr.known.add(j.id);
        consume(p, held);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        Msg.send(p, "<green>You learned <white>" + j.name + "<green>. Seals: <white>" + j.sealText());
        plugin.profiles().save(pr);
    }

    private void useEyes(Player p, Profile pr, ItemStack held, String victimId) {
        UUID victim;
        try {
            victim = UUID.fromString(victimId);
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (pr.clan != Clan.UCHIHA || !pr.awakened || pr.mangekyo == null) {
            Msg.send(p, "<red>Only an awakened Uchiha with a Mangekyou can use these eyes.");
            return;
        }
        if (pr.eternal) {
            Msg.send(p, "<gray>Your Mangekyou is already eternal.");
            return;
        }
        if (!pr.bonds.contains(victim)) {
            Msg.send(p, "<red>These eyes belong to someone you were not bonded with.");
            return;
        }
        pr.eternal = true;
        pr.strain = 0;
        consume(p, held);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, true, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.6f);
        p.sendMessage(Msg.mm(Msg.PREFIX + "<dark_red>Your Mangekyou becomes eternal. The strain is gone."));
        plugin.profiles().save(pr);
    }

    private void soldierPill(Player p, Profile pr) {
        pr.chakra = Math.min(plugin.chakra().effectiveMax(pr), pr.chakra + 60.0);
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 0.8f);
        Msg.bar(p, "<gold>Power surges through you... but it will not last.");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            pr.chakra = 0.0;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 1));
            Msg.bar(p, "<red>The soldier pill wears off. Your body crashes.");
        }, 600L);
    }

    // ------------------------------------------------------------------ thrown items

    private void throwItem(Player p, ItemStack held, String kind, double speed) {
        long now = System.currentTimeMillis();
        Long last = throwCd.get(p.getUniqueId());
        if (last != null && now - last < 300L) return;
        throwCd.put(p.getUniqueId(), now);

        Snowball sb = p.launchProjectile(Snowball.class);
        sb.setVelocity(p.getEyeLocation().getDirection().multiply(speed));
        ItemStack shown = held.clone();
        shown.setAmount(1);
        sb.setItem(shown);
        sb.getPersistentDataContainer().set(projKey, PersistentDataType.STRING, kind);
        consume(p, held);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.7f, 1.5f);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball sb)) return;
        String kind = sb.getPersistentDataContainer().get(projKey, PersistentDataType.STRING);
        if (kind == null) return;
        Player shooter = sb.getShooter() instanceof Player sp ? sp : null;
        Location loc = sb.getLocation();
        World w = loc.getWorld();
        Entity hit = e.getHitEntity();

        switch (kind) {
            case "kunai", "shuriken" -> {
                double dmg = kind.equals("kunai") ? 6.0 : 4.0;
                if (hit instanceof LivingEntity le && shooter != null && Fx.canHurt(plugin, shooter, le)) {
                    le.damage(dmg, shooter);
                }
                double keep = kind.equals("kunai") ? 0.65 : 0.45;
                if (random.nextDouble() < keep) {
                    w.dropItemNaturally(loc, create(kind.equals("kunai") ? Type.KUNAI : Type.SHURIKEN, 1));
                }
                sb.remove();
            }
            case "explosive_tag" -> {
                boolean blocks = plugin.getConfig().getBoolean("block-damage", false);
                w.createExplosion(loc, 2.5f, false, blocks, shooter);
                sb.remove();
            }
            case "smoke_bomb" -> {
                sb.remove();
                smokeCloud(loc, shooter);
            }
            default -> {
            }
        }
    }

    private void smokeCloud(Location loc, Player shooter) {
        World w = loc.getWorld();
        w.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t += 5;
                if (t > 160) {
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 25, 2.0, 1.0, 2.0, 0.01);
                w.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 2.0, 1.0, 2.0, 0.01);
                for (Entity en : w.getNearbyEntities(loc, 4, 3, 4)) {
                    if (en instanceof LivingEntity le && !le.equals(shooter)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ------------------------------------------------------------------ training dummy

    private void placeDummy(PlayerInteractEvent e, Player p, ItemStack held) {
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        BlockFace face = e.getBlockFace();
        Location loc = clicked.getRelative(face).getLocation().add(0.5, 0, 0.5);
        if (!loc.getBlock().isPassable()) return;
        loc.setYaw(p.getLocation().getYaw() + 180f);
        loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setArms(true);
            a.setBasePlate(false);
            a.customName(Msg.mm("<gold>Training Dummy"));
            a.setCustomNameVisible(true);
            a.getEquipment().setHelmet(new ItemStack(Material.HAY_BLOCK));
            a.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            a.getPersistentDataContainer().set(dummyKey, PersistentDataType.STRING, "1");
        });
        consume(p, held);
    }

    private boolean isDummy(Entity en) {
        return en instanceof ArmorStand && en.getPersistentDataContainer().has(dummyKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onDummyClick(PlayerInteractAtEntityEvent e) {
        if (!isDummy(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getPlayer().isSneaking() && e.getHand() == EquipmentSlot.HAND) {
            e.getRightClicked().remove();
            give(e.getPlayer(), create(Type.TRAINING_DUMMY, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDummyDamage(EntityDamageEvent e) {
        if (!isDummy(e.getEntity())) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent be) || !(be.getDamager() instanceof Player p)) return;
        Profile pr = plugin.profiles().get(p);
        long now = System.currentTimeMillis();
        if (now - pr.lastDummyHit < 600L) return;
        pr.lastDummyHit = now;
        double cap = plugin.getConfig().getDouble("chakra.max-cap", 400.0);
        pr.maxChakra = Math.min(cap, pr.maxChakra + 0.1);
        e.getEntity().getWorld().spawnParticle(Particle.CRIT, e.getEntity().getLocation().add(0, 1.3, 0), 10, 0.2, 0.3, 0.2, 0.1);
        e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
        plugin.missions().progress(p, pr, MissionType.DUMMY, "ANY", 1);
    }

    // ------------------------------------------------------------------ chakra blade

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBladeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!isTag(p.getInventory().getItemInMainHand(), "chakra_blade")) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Profile pr = plugin.profiles().get(p);
        if (pr.nature == null || pr.chakra < 3.0) return;
        pr.chakra -= 3.0;
        e.setDamage(e.getDamage() + 3.0);
        Location c = victim.getLocation().add(0, 1, 0);
        switch (pr.nature) {
            case FIRE -> {
                victim.setFireTicks(80);
                c.getWorld().spawnParticle(Particle.FLAME, c, 15, 0.3, 0.4, 0.3, 0.05);
            }
            case WATER -> {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                c.getWorld().spawnParticle(Particle.SPLASH, c, 20, 0.3, 0.4, 0.3, 0.1);
            }
            case WIND -> {
                victim.setVelocity(p.getLocation().getDirection().setY(0.25).multiply(0.9));
                c.getWorld().spawnParticle(Particle.CLOUD, c, 12, 0.3, 0.4, 0.3, 0.1);
            }
            case LIGHTNING -> {
                e.setDamage(e.getDamage() + 2.0);
                c.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, c, 25, 0.4, 0.5, 0.4, 0.2);
            }
            case EARTH -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, false));
                c.getWorld().spawnParticle(Particle.POOF, c, 10, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    // ------------------------------------------------------------------ loot

    @EventHandler
    public void onLoot(LootGenerateEvent e) {
        double scrollChance = plugin.getConfig().getDouble("loot.scroll-chance", 0.10);
        if (random.nextDouble() < scrollChance) {
            List<Jutsu> pool = new ArrayList<>();
            for (Jutsu j : plugin.jutsus().all()) if (j.needsKnown) pool.add(j);
            if (!pool.isEmpty()) e.getLoot().add(scroll(pool.get(random.nextInt(pool.size()))));
        }
        double gearChance = plugin.getConfig().getDouble("loot.gear-chance", 0.15);
        if (random.nextDouble() < gearChance) {
            e.getLoot().add(create(random.nextBoolean() ? Type.KUNAI : Type.SHURIKEN, 3 + random.nextInt(4)));
        }
    }
}
