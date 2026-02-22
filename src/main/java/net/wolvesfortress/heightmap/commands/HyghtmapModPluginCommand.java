package net.wolvesfortress.heightmap.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import net.wolvesfortress.heightmap.ui.HeightmapImportPage;

import javax.annotation.Nonnull;

/**
 * Main command for HyghtmapMod plugin.
 *
 * Usage:
 * - /heightmap - Open heightmap import dialog
 */
public class HyghtmapModPluginCommand extends AbstractPlayerCommand {

    public HyghtmapModPluginCommand() {
        super("heightmap", "Open heightmap import dialog");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // No permission required for base command
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Error: Could not get player"));
            return;
        }

        // Open heightmap import dialog directly
        HeightmapImportPage dialog = new HeightmapImportPage(playerRef);
        player.getPageManager().openCustomPage(ref, store, dialog);
    }
}