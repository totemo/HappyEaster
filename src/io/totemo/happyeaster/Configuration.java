package io.totemo.happyeaster;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

// ----------------------------------------------------------------------------
/**
 * Reads and exposes the plugin configuration.
 */
public class Configuration {
    /**
     * Do debug logging of killer rabbit despawning.
     */
    public boolean DEBUG_DESPAWN;

    /**
     * If true, do less particle effects.
     */
    public boolean LESS_PARTICLES;

    /**
     * Debug log natural spawns.
     */
    public boolean DEBUG_NATURAL_SPAWN;

    /**
     * The world affected by the plugin.
     */
    public World WORLD;

    /**
     * Maximum health of a killer rabbit.
     */
    public double KILLER_RABBIT_HEALTH;

    /**
     * Fraction of hostile mobs replaced by killer rabbits. Beware of setting
     * this close to unity: the server will keep spawning more hostile mobs up
     * to the mob cap, leading to ever-increasing numbers of killer rabbits,
     * which are NOT counted as hostiles.
     */
    public double KILLER_RABBIT_CHANCE;

    /**
     * Potion effects applied to killer rabbits.
     */
    public List<PotionEffect> KILLER_RABBIT_EFFECTS;

    /**
     * Name of the Holy Hand Grenade with colour codes translated.
     *
     * A string comparison against this name is used to differentiated between
     * an egg throw and a grenade throw. It is assumed impossible for a player
     * to set a non-default colour in the name.
     */
    public String GRENADE_NAME;

    /**
     * Maximum damage of the hand grenade at ground zero.
     */
    public double GRENADE_DAMAGE;

    /**
     * Radius over which grenade explosion damage is the maximum.
     */
    public double GRENADE_RADIUS;

    /**
     * Chance of a head being blown off by a grenade.
     */
    public double DROPS_HEAD_CHANCE;

    /**
     * Regular drops.
     */
    public ArrayList<Drop> DROPS_REGULAR;

    /**
     * Special drops.
     */
    public ArrayList<Drop> DROPS_SPECIAL;

    // ------------------------------------------------------------------------
    /**
     * Load the plugin configuration.
     */
    public void reload() {
        HappyEaster.PLUGIN.reloadConfig();

        DEBUG_DESPAWN = HappyEaster.PLUGIN.getConfig().getBoolean("debug.despawn");
        DEBUG_NATURAL_SPAWN = HappyEaster.PLUGIN.getConfig().getBoolean("debug.natural_spawn");
        LESS_PARTICLES = HappyEaster.PLUGIN.getConfig().getBoolean("less_particles");
        WORLD = Bukkit.getWorld(HappyEaster.PLUGIN.getConfig().getString("world.name"));
        if (WORLD == null) {
            WORLD = Bukkit.getWorld("world");
        }

        KILLER_RABBIT_HEALTH = HappyEaster.PLUGIN.getConfig().getDouble("killer_rabbit.health");
        KILLER_RABBIT_CHANCE = HappyEaster.PLUGIN.getConfig().getDouble("killer_rabbit.chance");
        KILLER_RABBIT_EFFECTS = new ArrayList<PotionEffect>();
        ConfigurationSection effectsSection = HappyEaster.PLUGIN.getConfig().getConfigurationSection("killer_rabbit.effects");
        for (String effectKey : effectsSection.getKeys(false)) {
            KILLER_RABBIT_EFFECTS.add((PotionEffect) effectsSection.get(effectKey));
        }

        GRENADE_NAME = ChatColor.translateAlternateColorCodes('&',
            HappyEaster.PLUGIN.getConfig().getString("holy_hand_grenade.name"));
        GRENADE_DAMAGE = HappyEaster.PLUGIN.getConfig().getDouble("holy_hand_grenade.damage");
        GRENADE_RADIUS = HappyEaster.PLUGIN.getConfig().getDouble("holy_hand_grenade.radius");

        DROPS_HEAD_CHANCE = HappyEaster.PLUGIN.getConfig().getDouble("drops.head.chance");
        DROPS_REGULAR = loadDrops(HappyEaster.PLUGIN.getConfig().getConfigurationSection("drops.regular"));
        DROPS_SPECIAL = loadDrops(HappyEaster.PLUGIN.getConfig().getConfigurationSection("drops.special"));
    } // reload

    // ------------------------------------------------------------------------
    /**
     * Return true if the world of the entity event is the configured affected
     * world.
     *
     * @param event an entity-related event.
     * @return true if the world of the entity event is the configured affected
     *         world.
     */
    public boolean isAffectedWorld(EntityEvent event) {
        return isAffectedWorld(event.getEntity().getLocation().getWorld());
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the specified world is the configured affected world.
     *
     * @param world the world to check.
     * @return true if the specified world is the configured affected world.
     */
    public boolean isAffectedWorld(World world) {
        return world.equals(WORLD);
    }

    // ------------------------------------------------------------------------
    /**
     * Load a shaped recipe from the specified configuration section.
     *
     * @param section the section to load from.
     * @return a shaped recipe.
     */
    public ShapedRecipe loadShapedRecipe(ConfigurationSection section) {
        Material resultMaterial = Material.valueOf(section.getString("type"));
        ItemStack result = new ItemStack(resultMaterial);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', section.getString("name")));
        meta.setLore(section.getStringList("lore"));
        ConfigurationSection enchants = section.getConfigurationSection("enchants");
        for (String enchantSection : enchants.getKeys(false)) {
            loadEnchantment(enchants.getConfigurationSection(enchantSection), meta);
        }
        result.setItemMeta(meta);

        ShapedRecipe recipe = new ShapedRecipe(result);
        recipe.shape(section.getStringList("recipe.shape").toArray(new String[3]));

        ConfigurationSection ingredients = section.getConfigurationSection("recipe.ingredients");
        for (String letter : ingredients.getKeys(false)) {
            Material ingredient = Material.valueOf(ingredients.getString(letter));
            recipe.setIngredient(letter.charAt(0), ingredient);
        }
        return recipe;
    }

    // ------------------------------------------------------------------------
    /**
     * Load an enchantment from a specified configuration section and apply it
     * to ItemMeta.
     *
     * @param section the section to load the enchantment from.
     * @param meta the ItemMeta into which the enchantment will be added.
     */
    public void loadEnchantment(ConfigurationSection section, ItemMeta meta) {
        Enchantment type = Enchantment.getByName(section.getString("type"));
        int level = section.getInt("level", 1);
        meta.addEnchant(type, level, true);
    }

    // ------------------------------------------------------------------------
    /**
     * Load a string list from the configuration under the specified path,
     * translate alternate colour codes and return a list of the translated
     * strings.
     *
     * @param path the configuration section path.
     * @return a list of lore strings.
     */
    public List<String> loadAndTranslateLore(String path) {
        ArrayList<String> loreList = new ArrayList<String>();
        for (String lore : HappyEaster.PLUGIN.getConfig().getStringList(path)) {
            loreList.add(ChatColor.translateAlternateColorCodes('&', lore));
        }
        return loreList;
    }

    // ------------------------------------------------------------------------
    /**
     * Load an array of {@link Drop}s from the specified section,
     *
     * @param section the configuration section.
     * @return the array of {@link Drop}s.
     */
    public ArrayList<Drop> loadDrops(ConfigurationSection section) {
        ArrayList<Drop> drops = new ArrayList<Drop>();
        for (String key : section.getKeys(false)) {
            drops.add(new Drop(section.getConfigurationSection(key)));
        }
        return drops;
    }
} // class Configuration