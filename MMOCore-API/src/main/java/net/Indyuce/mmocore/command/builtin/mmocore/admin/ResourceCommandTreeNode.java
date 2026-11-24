package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.resource.PlayerResource;
import net.Indyuce.mmocore.api.quest.trigger.ManaTrigger;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ResourceCommandTreeNode extends CommandTreeNode {
    private final String type;
    private final PlayerResource resource;

    public ResourceCommandTreeNode(String type, CommandTreeNode parent, PlayerResource resource) {
        super(parent, "resource-" + type);

        this.type = type;
        this.resource = resource;

        addChild(new ActionCommandTreeNode(this, "set", ManaTrigger.Operation.SET));
        addChild(new ActionCommandTreeNode(this, "give", ManaTrigger.Operation.GIVE));
        addChild(new ActionCommandTreeNode(this, "take", ManaTrigger.Operation.TAKE));
    }

    public class ActionCommandTreeNode extends CommandTreeNode {
        private final ManaTrigger.Operation action;

        private final Argument<Player> argPlayer;
        private final Argument<Double> argAmount;

        public ActionCommandTreeNode(CommandTreeNode parent, String type, ManaTrigger.Operation action) {
            super(parent, type);

            this.action = action;

            argPlayer = addArgument(Argument.PLAYER);
            argAmount = addArgument(Argument.AMOUNT_DOUBLE);
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            final var player = explorer.parse(argPlayer);
            final var amount = explorer.parse(argAmount);

            return PlayerCommand.execute(player, explorer, () -> {
                PlayerData data = PlayerData.get(player);
                resource.getConsumer(action).accept(data, amount, ResourceUpdateReason.COMMAND);
                return explorer.success("&6" + player.getName() + "&e now has &6" + resource.getCurrent(data) + "&e " + type + " points.");
            });
        }
    }
}
