package net.Indyuce.mmocore.api.util.input;

import io.lumine.mythic.lib.gui.editable.GeneratedInventory;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ChatInput extends PlayerInput {
    private final InputType inputType;
    private final GeneratedInventory lastOpened;

    @Deprecated
    public ChatInput(@NotNull Player player, @NotNull InputType inputType, @NotNull Consumer<String> output) {
        this(player, inputType, null, output);
    }

    /**
     * Have a player input a string in the global chat
     *
     * @param player     Player requesting chat input
     * @param inputType  Type of chat input
     * @param lastOpened Inventory opened again if 'cancel' is input. Set to null to disable
     * @param output     What to do when input is detected
     */
    public ChatInput(@NotNull Player player, @NotNull InputType inputType, @Nullable GeneratedInventory lastOpened, @NotNull Consumer<String> output) {
        super(player, output);

        this.inputType = inputType;
        this.lastOpened = lastOpened;

        player.closeInventory();
        inputType.inputMessage.send(player);
    }

    @Override
    public void close() {
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
        InventoryOpenEvent.getHandlerList().unregister(this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void registerInput(AsyncPlayerChatEvent event) {
        if (event.getPlayer().equals(getPlayer())) {
            close();
            event.setCancelled(true);

            if (event.getMessage().equals("cancel")) {
                if (lastOpened != null) Tasks.runEntity(getPlayer(), lastOpened::open);
                inputType.cancelMessage.send(getPlayer());
            } else
                // Run sync
                Tasks.runEntity(getPlayer(), () -> output(event.getMessage()));
        }
    }

    @EventHandler
    public void b(InventoryOpenEvent event) {
        if (event.getPlayer().equals(getPlayer()))
            close();
    }
}
