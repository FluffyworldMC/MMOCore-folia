package net.Indyuce.mmocore.command;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode.CommandResult;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class PlayerCommand {
    private PlayerCommand() {
    }

    public static CommandResult execute(Player player, CommandTreeExplorer explorer, Supplier<CommandResult> action) {
        if (Bukkit.isOwnedByCurrentRegion(player)) return action.get();
        if (Tasks.runEntity(player, action::get) == null)
            return explorer.fail("Player " + player.getName() + " is no longer online.");
        return CommandResult.SUCCESS;
    }
}
