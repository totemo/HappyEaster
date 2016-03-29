package io.totemo.happyeaster;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;

// ----------------------------------------------------------------------------
/**
 * This task despawns killer rabbits that have not had their custom name changed
 * by a player after 5 minutes in the world, to keep the overall population
 * down.
 *
 * Because Minecraft does not count killer rabbits as hostile mobs, they don't
 * despawn, and they are continually being added to the world by replacing other
 * hostile mobs.
 */
public class RabbitDespawnTask implements Runnable {
    // ------------------------------------------------------------------------
    /**
     * Scan loaded chunks and despawn killer rabbits older than 5 minutes unless
     * they have been renamed by a player.
     *
     * Scanning is limited to less than 500 microseconds (0.5 milliseconds, or
     * 1% of a tick), per call to run().
     *
     * @see java.lang.Runnable#run()
     *
     *
     */
    @Override
    public void run() {
        if (_loadedChunks == null) {
            _loadedChunks = HappyEaster.CONFIG.WORLD.getLoadedChunks();
            return;
        }

        long start = System.nanoTime();
        long elapsed;
        do {
            if (_nextChunk >= _loadedChunks.length) {
                _nextChunk = 0;
                _loadedChunks = null;
                return;
            }

            Chunk chunk = _loadedChunks[_nextChunk];
            for (Entity entity : chunk.getEntities()) {
                if (HappyEaster.isKillerRabbit(entity) &&
                    HappyEaster.KILLER_RABBIT_NAME.equals(entity.getCustomName()) &&
                    entity.getTicksLived() > 5 * 60 * 20) {
                    entity.remove();

                    if (HappyEaster.CONFIG.DEBUG_DESPAWN) {
                        HappyEaster.PLUGIN.getLogger().info(
                            "Despawning killer rabbit at " +
                            Util.formatLocation(entity.getLocation()) +
                            " (age " + entity.getTicksLived() + ")");
                    }
                }
            }

            ++_nextChunk;
            elapsed = System.nanoTime() - start;
        } while (elapsed < 500_000L);
    } // run

    // ------------------------------------------------------------------------
    /**
     * All loaded chunks.
     *
     * The array is refreshed when _nextChunk wraps around to 0.
     */
    protected Chunk[] _loadedChunks;

    /**
     * Index of the next entity to consider.
     */
    protected int _nextChunk;
} // class RabbitDespawnTask