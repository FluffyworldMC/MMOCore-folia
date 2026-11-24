package net.Indyuce.mmocore.command.builtin.mmocore.admin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.util.TriConsumer;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.command.PlayerCommand;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public class SkillTreePointsCommandTreeNode extends CommandTreeNode {
    private final BiFunction<PlayerData, SkillTree, Integer> get;

    public SkillTreePointsCommandTreeNode(CommandTreeNode parent,
                                          TriConsumer<PlayerData, Integer, SkillTree> set,
                                          TriConsumer<PlayerData, Integer, SkillTree> give,
                                          BiFunction<PlayerData, SkillTree, Integer> get) {
        super(parent, "skill-tree-points");

        addChild(new ActionCommandTreeNode(this, "give", give));
        addChild(new ActionCommandTreeNode(this, "set", set));
        this.get = get;
    }

    public class ActionCommandTreeNode extends CommandTreeNode {
        private final TriConsumer<PlayerData, Integer, SkillTree> action;

        private final Argument<Player> argPlayer;
        private final Argument<Integer> argAmount;
        private final Argument<SkillTree> argType;

        public ActionCommandTreeNode(CommandTreeNode parent, String id, TriConsumer<PlayerData, Integer, SkillTree> action) {
            super(parent, id);

            this.action = action;

            argPlayer = addArgument(Argument.PLAYER);
            argAmount = addArgument(Argument.AMOUNT_INT);
            argType = addArgument(Arguments.SKILL_TREE_OR_GLOBAL);
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            final var player = explorer.parse(argPlayer);
            final var amount = explorer.parse(argAmount);
            final @Nullable var skillTree = explorer.parse(argType);

            return PlayerCommand.execute(player, explorer, () -> {
                final var skillTreeName = skillTree == null ? "global" : skillTree.getId();
                final var data = PlayerData.get(player);
                action.accept(data, amount, skillTree);
                return explorer.success("&6" + player.getName() + "&e now has &6" + get.apply(data, skillTree) + "&e " + skillTreeName + " skill tree points.");
            });
        }
    }
}
