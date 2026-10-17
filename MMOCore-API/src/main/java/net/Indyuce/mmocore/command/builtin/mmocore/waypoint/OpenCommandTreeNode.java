package net.Indyuce.mmocore.command.builtin.mmocore.waypoint;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.manager.InventoryManager;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class OpenCommandTreeNode extends CommandTreeNode {
    private final Argument<Player> argPlayer;

    public OpenCommandTreeNode(CommandTreeNode parent) {
        super(parent, "open");

        argPlayer = addArgument(Argument.PLAYER);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        Player player = explorer.parse(argPlayer);
        Tasks.openInventory(player, () -> InventoryManager.WAYPOINTS.newInventory(PlayerData.get(player)));
        return CommandResult.SUCCESS;
    }
}
