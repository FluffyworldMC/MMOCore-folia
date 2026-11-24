package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.manager.ActionBarManager;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HideActionBarCommandTreeNode extends CommandTreeNode {
    private final Argument<Player> argPlayer;
    private final Argument<Long> argDuration;

    public HideActionBarCommandTreeNode(CommandTreeNode parent) {
        super(parent, "hideab");

        argPlayer = addArgument(Argument.PLAYER);
        argDuration = addArgument(Argument.DURATION_TICKS);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var player = explorer.parse(argPlayer);
        final var amount = explorer.parse(argDuration);

        return PlayerCommand.execute(player, explorer, () -> {
            PlayerData.get(player).getMMOPlayerData().getActionBar().hide(ActionBarManager.PRIORITY, amount);
            return CommandResult.SUCCESS;
        });
    }
}
