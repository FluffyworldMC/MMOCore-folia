package net.Indyuce.mmocore.command.builtin.mmocore.clazz;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ForceCommandTreeNode extends CommandTreeNode {
    private final Argument<Player> argPlayer;
    private final Argument<PlayerClass> argClass;

    public ForceCommandTreeNode(CommandTreeNode parent) {
        super(parent, "force");

        argPlayer = addArgument(Argument.PLAYER);
        argClass = addArgument(Arguments.CLASS);
    }

    @Override
    public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var player = explorer.parse(argPlayer);
        final var profess = explorer.parse(argClass);

        return PlayerCommand.execute(player, explorer, () -> {
            final var playerData = PlayerData.get(player);

            // Cannot select already existing class
            if (playerData.getProfess().equals(profess)) {
                return explorer.fail("Player " + player.getName() + " is already a " + profess.getName() + ".");
            }

            final var called = new PlayerChangeClassEvent(playerData, profess, PlayerChangeClassEvent.Reason.COMMAND_FORCE);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled()) return explorer.fail("Bukkit event canceled");

            playerData.setClass(profess);
            return explorer.success("Class of player &6" + player.getName() + "&e forcefully set to &6" + profess.getName());
        });
    }
}
