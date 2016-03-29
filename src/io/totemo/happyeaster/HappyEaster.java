package io.totemo.happyeaster;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Rabbit.Type;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.darkblade12.particleeffect.ParticleEffect;

// ----------------------------------------------------------------------------
/**
 * Easter plugin, command handling and event handler.
 *
 * The main features are:
 * <ul>
 * <li>The plugin affects a single configured world (by default, the overworld)
 * only.</li>
 * <li>A configurable fraction of hostile mobs in the configured world are
 * replaced by killer rabbits. Spawner mobs are not modified.</li>
 * <li></li>
 * </ul>
 */
public class HappyEaster extends JavaPlugin implements Listener {
    /**
     * Configuration wrapper instance.
     */
    public static Configuration CONFIG = new Configuration();

    /**
     * This plugin, accessible as, effectively, a singleton.
     */
    public static HappyEaster PLUGIN;

    /**
     * Display name of the killer rabbit.
     */
    public static String KILLER_RABBIT_NAME = ChatColor.RED + "The Killer Rabbit of Caerbannog";

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        PLUGIN = this;

        saveDefaultConfig();
        CONFIG.reload();
        ConfigurationSection section = getConfig().getConfigurationSection("holy_hand_grenade");
        getServer().addRecipe(CONFIG.loadShapedRecipe(section));

        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new RabbitDespawnTask(), 1, 1);
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onCommand(org.bukkit.command.CommandSender,
     *      org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase(getName())) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                CONFIG.reload();
                sender.sendMessage(ChatColor.GOLD + getName() + " configuration reloaded.");
                return true;
            }
        }

        sender.sendMessage(ChatColor.RED + "Invalid command syntax.");
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * In the configured World, replace a fraction of hostile natural spawns
     * with killer rabbits.
     *
     * Spawner-spawned mobs are not affected in any way.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Tag natural spawns, even in other worlds, so that grenades work as
        // expected for head drops.
        if (event.getSpawnReason() == SpawnReason.NATURAL) {
            event.getEntity().setMetadata(NATURAL_KEY, new FixedMetadataValue(this, Boolean.TRUE));
        }

        if (!CONFIG.isAffectedWorld(event)) {
            return;
        }

        if (event.getSpawnReason() == SpawnReason.NATURAL && isEligibleHostileMob(event.getEntityType()) &&
            Math.random() < CONFIG.KILLER_RABBIT_CHANCE) {

            Entity originalMob = event.getEntity();
            Location loc = originalMob.getLocation();
            originalMob.remove();
            spawnKillerRabbit(loc);

            if (CONFIG.DEBUG_NATURAL_SPAWN) {
                getLogger().info("Spawned killer rabbit at " + Util.formatLocation(loc));
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Tag mobs hurt by players.
     *
     * Only those mobs hurt recently by players will have special drops.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Creature) {
            ParticleEffect.REDSTONE.display(0.3f, 0.3f, 0.3f, 0, 20, entity.getLocation(), 32);
        }

        if (isKillerRabbit(entity) || isEligibleHostileMob(entity.getType())) {
            int lootingLevel = 0;
            boolean isPlayerAttack = false;
            if (event.getDamager() instanceof Player) {
                isPlayerAttack = true;
                Player player = (Player) event.getDamager();
                lootingLevel = player.getItemInHand().getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);
            } else if (event.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) event.getDamager();
                if (projectile.getShooter() instanceof Player) {
                    isPlayerAttack = true;
                }
            }

            // Tag mobs hurt by players with the damage time stamp.
            if (isPlayerAttack) {
                entity.setMetadata(PLAYER_DAMAGE_TIME_KEY, new FixedMetadataValue(this, new Long(entity.getWorld().getFullTime())));
                entity.setMetadata(PLAYER_LOOTING_LEVEL_KEY, new FixedMetadataValue(this, lootingLevel));
            }
        }
    } // onEntityDamageByEntity

    // ------------------------------------------------------------------------
    /**
     * On killer rabbit or hostile mob death, do special drops if a player hurt
     * the mob recently.
     *
     * Don't do special drops in worlds other than the overworld, because the
     * end grinder is too cheap.
     */
    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!CONFIG.isAffectedWorld(event)) {
            return;
        }

        Entity entity = event.getEntity();
        if (isKillerRabbit(entity) || (isEligibleHostileMob(entity.getType()) && isNaturalSpawn(entity))) {
            int lootingLevel = getLootingLevelMeta(entity);
            boolean specialDrops = false;
            Long damageTime = getPlayerDamageTime(entity);
            if (damageTime != null) {
                Location loc = entity.getLocation();
                if (loc.getWorld().getFullTime() - damageTime < PLAYER_DAMAGE_TICKS) {
                    specialDrops = true;
                }
            }

            doCustomDrops(event.getEntity().getLocation(), specialDrops, lootingLevel);
        }
    } // onEntityDeath

    // ------------------------------------------------------------------------
    /**
     * When the player lobs the Holy Hand Grenade, tag the projectile entity
     * with metadata.
     */
    @EventHandler()
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof Player) {
            Player player = (Player) shooter;
            if (isHolyHandGrenade(player.getItemInHand())) {
                projectile.setMetadata(GRENADE_KEY, new FixedMetadataValue(this, Boolean.TRUE));
            }
        }
        // Harder to determine whether an egg is a Holy Hand Grenade when
        // dispensed. Not essential.
    }

    // ------------------------------------------------------------------------
    /**
     * When the Holy Hand Grenade hits, cancel any egg spawns and make it
     * explode.
     */
    @EventHandler()
    public void onPlayerEggThrow(PlayerEggThrowEvent event) {
        Egg egg = event.getEgg();
        if (egg.getMetadata(GRENADE_KEY).size() != 0) {
            event.setHatching(false);
            Location loc = egg.getLocation();
            doExplosionParticles(loc);
            doExplosionDamage(loc);
            doExplosiveFishing(loc);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Block players from changing spawner types with spawn eggs.
     */
    @EventHandler()
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
            event.getClickedBlock().getType() == Material.MOB_SPAWNER &&
            event.getItem() != null &&
            event.getItem().getType() == Material.MONSTER_EGG) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified ItemStack is a Holy Hand Grenade.
     *
     * @param item the item.
     * @return true if the specified ItemStack is a Holy Hand Grenade.
     */
    protected boolean isHolyHandGrenade(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().equals(CONFIG.GRENADE_NAME);
    }

    // ------------------------------------------------------------------------
    /**
     * Spawn a killer rabbit at the specified location.
     *
     * @param loc the location.
     * @return a killer rabbit.
     */
    protected Rabbit spawnKillerRabbit(Location loc) {
        Rabbit rabbit = (Rabbit) loc.getWorld().spawnEntity(loc, EntityType.RABBIT);
        rabbit.setRabbitType(Type.THE_KILLER_BUNNY);
        rabbit.setMaxHealth(CONFIG.KILLER_RABBIT_HEALTH);
        rabbit.setHealth(CONFIG.KILLER_RABBIT_HEALTH);
        rabbit.addPotionEffects(CONFIG.KILLER_RABBIT_EFFECTS);
        rabbit.setCustomNameVisible(true);
        rabbit.setCustomName(KILLER_RABBIT_NAME);
        return rabbit;
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified entity is a killer rabbit.
     *
     * @param entity the entity.
     * @return true if the specified entity is a killer rabbit.
     */
    protected static boolean isKillerRabbit(Entity entity) {
        return entity instanceof Rabbit && ((Rabbit) entity).getRabbitType() == Type.THE_KILLER_BUNNY;
    }

    // ------------------------------------------------------------------------
    /**
     * Damage hostile mobs and killer rabbits within the grenade area of effect.
     *
     * Accordingly, players are excluded from damage to rule out PvP, as are
     * tamed animals and villagers. To prevent grief of Doppelgangers and other
     * pet hostile mobs, if a mob is named, it will not be affected by the
     * explosion (with the exception of killer rabbits).
     *
     * Damage falls off in proportion to the square of the distance, based on
     * the idea that the explosion is a spherical pressure wave and the area of
     * a sphere is proportional to the radius squared.
     *
     * Explosions can drop at most one hostile mob head.
     *
     * @param loc the centre of the explosion.
     */
    protected void doExplosionDamage(Location loc) {
        boolean droppedHead = false;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, CONFIG.GRENADE_RADIUS, CONFIG.GRENADE_RADIUS, CONFIG.GRENADE_RADIUS)) {
            if (isKillerRabbit(entity) ||
                (entity instanceof Monster && entity.getCustomName() == null)) {
                double distance = Math.max(1.0, entity.getLocation().distance(loc) - 1.0);
                double distSq = distance * distance;
                double damage = CONFIG.GRENADE_DAMAGE / distSq;

                Creature creature = (Creature) entity;
                if (!droppedHead) {
                    if (creature.isValid() && creature.getHealth() <= damage &&
                        isNaturalSpawn(creature) && Math.random() < CONFIG.DROPS_HEAD_CHANCE) {
                        ItemStack head = getCreatureHead(creature);
                        if (head != null) {
                            Location creatureLoc = creature.getLocation();
                            creatureLoc.getWorld().dropItemNaturally(creatureLoc, head);
                            droppedHead = true;
                        }
                    }
                }
                creature.damage(damage);
            }
        }
    } // doExplosionDamage

    // ------------------------------------------------------------------------
    /**
     * Do custom particle effects at the specified location for enhanced
     * explosion visuals.
     *
     * @param loc the origin of the explosion.
     */
    protected void doExplosionParticles(final Location loc) {
        FireworkEffect.Builder builder = FireworkEffect.builder();
        builder.with(FIREWORK_TYPES[_random.nextInt(FIREWORK_TYPES.length)]);
        builder.withColor(Color.fromRGB(255, 255, 255));
        builder.withColor(Color.fromRGB(255, 255, 0));
        builder.withColor(Color.fromRGB(255, 128, 64));
        builder.withColor(Color.fromRGB(0, 0, 0));
        final int primaryColors = 1 + _random.nextInt(3);
        for (int i = 0; i < primaryColors; ++i) {
            int red = 255 - _random.nextInt(64);
            int green = red - _random.nextInt(64);
            int blue = 64 + _random.nextInt(64);
            builder.withColor(Color.fromRGB(red, green, blue));
        }

        builder.withFade(Color.fromRGB(64, 64, 64));
        builder.withFade(Color.fromRGB(0, 0, 0));

        CustomEntityFirework.spawn(loc, builder.build());
        loc.getWorld().playEffect(loc, Effect.MOBSPAWNER_FLAMES, 0, 48);

        for (int i = 0; i < 15; ++i) {
            double angle = 2 * Math.PI * Math.random();
            final double x = Math.cos(angle);
            final double z = Math.sin(angle);
            ParticleEffect.SMOKE_NORMAL.display(new Vector(x, 0.5, z).normalize(), 0.2f, loc, 32);
            if (!CONFIG.LESS_PARTICLES) {
                final double scale = i / 15.0;
                Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                    @Override
                    public void run() {
                        final Location flameLoc = loc.clone().add(2.0 * scale * x, 1.5 * scale, 2.0 * scale * z);
                        loc.getWorld().playEffect(flameLoc, Effect.MOBSPAWNER_FLAMES, 0, 48);
                    }
                }, i);
            }
        }
    } // doExplosionParticles

    // ------------------------------------------------------------------------
    /**
     * When a hand grenade is thrown in water, fish get blown out.
     *
     * @param loc the impact location of the grenade.
     */
    protected void doExplosiveFishing(Location loc) {
        final double MAX_VELOCITY = 1.0;
        Material material = loc.getBlock().getType();
        if (material == Material.WATER || material == Material.STATIONARY_WATER) {
            for (int i = 0; i < Util.random(2, 5); ++i) {
                double pitch = (0.2 + 0.3 * _random.nextDouble()) * Math.PI;
                double vY = MAX_VELOCITY * Math.sin(pitch);
                double vXZ = MAX_VELOCITY * Math.cos(pitch);

                double yaw = 2.0 * Math.PI * _random.nextDouble();
                double vX = vXZ * Math.cos(yaw);
                double vZ = vXZ * Math.sin(yaw);
                Vector velocity = new Vector(vX, vY, vZ);

                ItemStack fish;
                double choice = _random.nextDouble();
                if (choice < 0.5) {
                    fish = new ItemStack(Material.RAW_FISH, 1, (short) _random.nextInt(2));
                } else if (choice < 0.8) {
                    fish = new ItemStack(Material.COOKED_FISH, 1, (short) _random.nextInt(2));
                } else if (choice < 0.9) {
                    fish = new ItemStack(Material.RAW_FISH, 1, (short) 2);
                } else {
                    fish = new ItemStack(Material.RAW_FISH, 1, (short) 3);
                }
                Item item = loc.getWorld().dropItem(loc, fish);
                item.setVelocity(velocity);
            }
        }
    } // doExplosiveFishing

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified creature is a natural spawn.
     *
     * @param creature the creature.
     * @return true if the specified creature is a natural spawn.
     */
    protected boolean isNaturalSpawn(Entity creature) {
        return creature.hasMetadata(NATURAL_KEY);
    }

    // ------------------------------------------------------------------------
    /**
     * Return the specified creature's head, or null if the creature does not
     * have a droppable head.
     *
     * @param creature the creature.
     * @return the head, or null.
     */
    protected ItemStack getCreatureHead(Creature creature) {
        switch (creature.getType()) {
        case CREEPER:
            return new ItemStack(Material.SKULL_ITEM, 1, (short) 4);

        case SKELETON:
            Skeleton skeleton = (Skeleton) creature;
            if (skeleton.getSkeletonType() == SkeletonType.WITHER) {
                return new ItemStack(Material.SKULL_ITEM, 1, (short) 1);
            } else {
                return new ItemStack(Material.SKULL_ITEM, 1, (short) 0);
            }

        case ZOMBIE:
            return new ItemStack(Material.SKULL_ITEM, 1, (short) 2);

        default:
            return null;
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return the world time when a player damaged the specified entity, if
     * stored as a PLAYER_DAMAGE_TIME_KEY metadata value, or null if that didn't
     * happen.
     *
     * @param entity the entity (mob).
     * @return the damage time stamp as Long, or null.
     */
    protected Long getPlayerDamageTime(Entity entity) {
        List<MetadataValue> playerDamageTime = entity.getMetadata(PLAYER_DAMAGE_TIME_KEY);
        if (playerDamageTime.size() > 0) {
            MetadataValue value = playerDamageTime.get(0);
            if (value.value() instanceof Long) {
                return (Long) value.value();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the looting level metadata value from a mob.
     *
     * This metadata is added when a player damages a mob. It is the level of
     * the Looting enchant on the weapon that did the damage, or 0 if there was
     * no such enchant.
     *
     * @param entity the damaged entity.
     * @return the level of the Looting enchant, or 0 if not so enchanted.
     */
    protected int getLootingLevelMeta(Entity entity) {
        List<MetadataValue> lootingLevel = entity.getMetadata(PLAYER_LOOTING_LEVEL_KEY);
        if (lootingLevel.size() > 0) {
            return lootingLevel.get(0).asInt();
        }
        return 0;
    }

    // ------------------------------------------------------------------------
    /**
     * Do custom drops.
     *
     * @param loc the location to drop the items.
     * @param special if true, low-probability, special drops are possible;
     *        otherwise, the drops are custom but mundane.
     * @param lootingLevel the level of looting on the weapon ([0,3]).
     */
    protected void doCustomDrops(Location loc, boolean special, int lootingLevel) {
        for (Drop drop : CONFIG.DROPS_REGULAR) {
            if (Math.random() < drop.getDropChance() * adjustedChance(lootingLevel)) {
                loc.getWorld().dropItemNaturally(loc, drop.generate());
            }
        }

        if (special) {
            int choice = _random.nextInt(CONFIG.DROPS_SPECIAL.size());
            Drop drop = CONFIG.DROPS_SPECIAL.get(choice);
            if (Math.random() < drop.getDropChance() * adjustedChance(lootingLevel)) {
                loc.getWorld().dropItemNaturally(loc, drop.generate());
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return multiplicative factor to apply to the base drop chance according
     * to a given looting level.
     *
     * The drop chance compounds by 20% per looting level.
     *
     * @param lootingLevel the looting level of the weapon.
     * @return a factor to be multiplied by the base drop chance to compute the
     *         actual drop chance.
     */
    protected double adjustedChance(int lootingLevel) {
        return Math.pow(1.2, lootingLevel);
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified entity type is that of a hostile mob that is
     * eligible to be replaced with a killer rabbit.
     *
     * @param type the entity's type.
     * @return true if the specified entity type is eligible to be replaced.
     */
    protected boolean isEligibleHostileMob(EntityType type) {
        return type == EntityType.CREEPER ||
               type == EntityType.SPIDER ||
               type == EntityType.SKELETON ||
               type == EntityType.ZOMBIE ||
               type == EntityType.ENDERMAN ||
               type == EntityType.WITCH;
    }

    // ------------------------------------------------------------------------

    /**
     * Plugin name; used to generate unique String keys.
     */
    protected static final String PLUGIN_NAME = "HappyEaster";

    /**
     * Metadata used to tag natural spawned mobs.
     */
    protected static final String NATURAL_KEY = PLUGIN_NAME + "_NaturalSpawn";

    /**
     * Metadata added to projectiles so that we can recognise them as the Holy
     * Hand Grenade on impact.
     */
    protected static final String GRENADE_KEY = PLUGIN_NAME + "_HolyHandGrenade";

    /**
     * Metadata name used for metadata stored on mobs to record last damage time
     * (Long) by a player.
     */
    protected static final String PLAYER_DAMAGE_TIME_KEY = PLUGIN_NAME + "_PlayerDamageTime";

    /**
     * Metadata name used for metadata stored on mobs to record looting
     * enchantment level of Looting weapon used by a player.
     */
    protected static final String PLAYER_LOOTING_LEVEL_KEY = PLUGIN_NAME + "_PlayerLootingLevel";

    /**
     * Time in ticks (1/20ths of a second) for which player attack damage
     * "sticks" to a mob. The time between the last player damage on a mob and
     * its death must be less than this for it to drop special stuff.
     */
    protected static final int PLAYER_DAMAGE_TICKS = 100;

    /**
     * Firework types.
     */
    protected static final FireworkEffect.Type[] FIREWORK_TYPES = { FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
                                                                   FireworkEffect.Type.BURST };

    /**
     * Random number generator.
     */
    protected Random _random = new Random();

} // class HappyEaster