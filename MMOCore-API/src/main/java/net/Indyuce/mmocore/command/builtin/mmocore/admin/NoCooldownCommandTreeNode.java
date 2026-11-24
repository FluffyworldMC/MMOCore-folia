package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class NoCooldownCommandTreeNode extends CommandTreeNode {
    private final Argument<Player> argPlayer;

    public NoCooldownCommandTreeNode(CommandTreeNode parent) {
        super(parent, "nocd");

        argPlayer = addArgument(Argument.PLAYER);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var player = explorer.parse(argPlayer);

        return PlayerCommand.execute(player, explorer, () -> {
            PlayerData data = PlayerData.get(player);
            data.noCooldown = !data.noCooldown;
            return explorer.success("NoCD " + (data.noCooldown ? "enabled" : "disabled") + " for " + player.getName());
        });
    }
}
