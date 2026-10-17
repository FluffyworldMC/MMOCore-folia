package net.Indyuce.mmocore.command.builtin.mmocore.waypoint;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.waypoint.Waypoint;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeleportCommandTreeNode extends CommandTreeNode {
    private final Argument<Waypoint> argWaypoint;
    private final Argument<Player> argPlayer;

    public TeleportCommandTreeNode(CommandTreeNode parent) {
        super(parent, "teleport");

        argWaypoint = addArgument(Arguments.WAYPOINT);
        argPlayer = addArgument(Argument.PLAYER);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        Waypoint waypoint = explorer.parse(argWaypoint);
        Player player = explorer.parse(argPlayer);

        player.teleportAsync(waypoint.getLocation());
        return explorer.success("Successfully teleported &6" + player.getName() + "&e to &6" + waypoint.getId());
    }
}
