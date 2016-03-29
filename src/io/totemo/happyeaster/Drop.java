package io.totemo.happyeaster;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

// ----------------------------------------------------------------------------
/**
 * Represents a possible item drop.
 */
public class Drop {
    /**
     * Construct an instance by loading the ItemStack at "item" in the specified
     * configuration section.
     *
     * @param section the section.
     */
    public Drop(ConfigurationSection section) {
        _itemStack = (ItemStack) section.get("item");
        _min = section.getInt("min", 1);
        _max = section.getInt("max", Math.max(1, _min));
        _dropChance = section.getDouble("chance", 0.0);
    }

    // ------------------------------------------------------------------------
    /**
     * Return the probability of this drop, in the range [0.0,1.0].
     */
    public double getDropChance() {
        return _dropChance;
    }

    // ------------------------------------------------------------------------
    /**
     * Generate a new ItemStack by selecting a random number of items within the
     * configured range.
     *
     * @return the ItemStack.
     */
    public ItemStack generate() {
        ItemStack result = _itemStack.clone();
        result.setAmount(Util.random(_min, _max));
        return result;
    }

    // ------------------------------------------------------------------------
    /**
     * Minimum number of items in item stack.
     */
    protected int _min;

    /**
     * Maximum number of items in item stack.
     */
    protected int _max;

    /**
     * Drop chance, [0.0,1.0].
     */
    protected double _dropChance;

    /**
     * The ItemStack.
     */
    protected ItemStack _itemStack;
} // class Drop