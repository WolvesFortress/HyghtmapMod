package net.wolvesfortress.heightmap;

import com.hypixel.hytale.builtin.buildertools.tooloperations.ToolOperation;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.logger.HytaleLogger;

import net.wolvesfortress.heightmap.commands.FsuCommand;
import net.wolvesfortress.heightmap.commands.HyghtmapModPluginCommand;
import com.hypixel.hytale.builtin.buildertools.tooloperations.LayerBrushOperation;

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
        // Register custom tool operations
        registerToolOperations();

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
            getCommandRegistry().registerCommand(new FsuCommand());
            LOGGER.at(Level.INFO).log("[HyghtmapMod] Registered /heightmap and /fsu commands");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HyghtmapMod] Failed to register commands");
        }
    }

    /**
     * Register custom builder tool operations into the ToolOperation registry.
     */
    @SuppressWarnings("unchecked")
    private void registerToolOperations() {
        try {
            ToolOperation.OPERATIONS.put("LayerBrush", LayerBrushOperation::new);
            LOGGER.at(Level.INFO).log("[HyghtmapMod] Registered LayerBrush tool operation");
        } catch (UnsupportedOperationException e) {
            // Map might be unmodifiable — try reflection as fallback
            LOGGER.at(Level.WARNING).log("[HyghtmapMod] OPERATIONS map is unmodifiable, attempting reflection fallback...");
            try {
                java.lang.reflect.Field field = ToolOperation.class.getDeclaredField("OPERATIONS");
                field.setAccessible(true);

                // If wrapped in Collections.unmodifiableMap, the backing map is in a field called 'm'
                Object mapObj = field.get(null);
                if (mapObj instanceof java.util.Map) {
                    java.lang.reflect.Field mField = mapObj.getClass().getDeclaredField("m");
                    mField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> backingMap = (java.util.Map<String, Object>) mField.get(mapObj);
                    backingMap.put("LayerBrush", (com.hypixel.hytale.builtin.buildertools.tooloperations.OperationFactory) LayerBrushOperation::new);
                    LOGGER.at(Level.INFO).log("[HyghtmapMod] Registered LayerBrush tool operation (via reflection)");
                }
            } catch (Exception reflectionEx) {
                LOGGER.at(Level.SEVERE).withCause(reflectionEx)
                        .log("[HyghtmapMod] Failed to register LayerBrush operation — tool will not work");
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e)
                    .log("[HyghtmapMod] Failed to register LayerBrush operation");
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