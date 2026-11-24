package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class PointsCommandTreeNode extends CommandTreeNode {
    private final String type;
    private final Function<PlayerData, Integer> get;

    public PointsCommandTreeNode(String type, CommandTreeNode parent, BiConsumer<PlayerData, Integer> set, BiConsumer<PlayerData, Integer> give,
                                 Function<PlayerData, Integer> get) {
        super(parent, type + "-points");

        this.type = type;
        this.get = get;

        addChild(new ActionCommandTreeNode(this, "set", set));
        addChild(new ActionCommandTreeNode(this, "give", give));
    }

    public class ActionCommandTreeNode extends CommandTreeNode {
        private final BiConsumer<PlayerData, Integer> action;

        private final Argument<Player> argPlayer;
        private final Argument<Integer> argAmount;

        public ActionCommandTreeNode(CommandTreeNode parent, String type, BiConsumer<PlayerData, Integer> action) {
            super(parent, type);

            this.action = action;

            argPlayer = addArgument(Argument.PLAYER);
            argAmount = addArgument(Argument.AMOUNT_INT);
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            final var player = explorer.parse(argPlayer);
            final var amount = explorer.parse(argAmount);

            return PlayerCommand.execute(player, explorer, () -> {
                PlayerData data = PlayerData.get(player);
                action.accept(data, amount);
                return explorer.success("&6" + player.getName() + "&e now has &6" + get.apply(data) + "&e " + type + " points.");
            });
        }
    }
}
