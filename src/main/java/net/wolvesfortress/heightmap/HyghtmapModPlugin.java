package net.wolvesfortress.heightmap;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.logger.HytaleLogger;

import net.wolvesfortress.heightmap.commands.HyghtmapModPluginCommand;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * HyghtmapMod - A Hytale server plugin.
 */
public class HyghtmapModPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HyghtmapModPlugin instance;

    public HyghtmapModPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /**
     * Get the plugin instance.
     * @return The plugin instance
     */
    public static HyghtmapModPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();
    }

    /**
     * Register plugin commands.
     */
    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new HyghtmapModPluginCommand());
            LOGGER.at(Level.INFO).log("[HyghtmapMod] Registered /heightmap command");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HyghtmapMod] Failed to register commands");
        }
    }

    /**
     * Register event listeners.
     */
    private void registerListeners() {
    }

    @Override
    protected void start() {
    }

    @Override
    protected void shutdown() {
        instance = null;
    }
}