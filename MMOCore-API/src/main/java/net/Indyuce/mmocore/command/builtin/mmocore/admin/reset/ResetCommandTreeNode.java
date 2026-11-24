package net.Indyuce.mmocore.command.builtin.mmocore.admin.reset;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeNode;
import io.lumine.mythic.lib.command.argument.Argument;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.PlayerLevelChangeEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttribute;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes;
import net.Indyuce.mmocore.experience.Profession;
import net.Indyuce.mmocore.command.PlayerCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

public class ResetCommandTreeNode extends CommandTreeNode {
    public ResetCommandTreeNode(CommandTreeNode parent) {
        super(parent, "reset");

        addChild(new ResetClassesCommandTreeNode(this));
        addChild(new ResetLevelsCommandTreeNode(this));
        addChild(new ResetSkillsCommandTreeNode(this));
        addChild(new ResetQuestsCommandTreeNode(this));
        addChild(new ResetAttributesCommandTreeNode(this));
        addChild(new ResetWaypointsCommandTreeNode(this));
        addChild(new ResetSkillTreesCommandTreeNode(this));
        addChild(new ResetAllCommandTreeNode(this));
    }

    abstract static class AbstractResetNode extends CommandTreeNode {
        protected final Argument<Player> argPlayer;

        public AbstractResetNode(CommandTreeNode parent, String name) {
            super(parent, name);

            argPlayer = addArgument(Argument.PLAYER);
        }
    }

    static class ResetAllCommandTreeNode extends AbstractResetNode {
        public ResetAllCommandTreeNode(CommandTreeNode parent) {
            super(parent, "all");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);
            final boolean givePoints = args.length > 4 && args[4].equalsIgnoreCase("-reallocate");

            return PlayerCommand.execute(player, explorer, () -> {
                PlayerData data = PlayerData.get(player);
                ResetClassesCommandTreeNode.resetClasses(data);
                ResetLevelsCommandTreeNode.resetLevels(data);
                ResetSkillsCommandTreeNode.resetSkills(data);
                ResetQuestsCommandTreeNode.resetQuests(data);
                ResetAttributesCommandTreeNode.resetAttributes(data, givePoints);
                ResetWaypointsCommandTreeNode.resetWaypoints(data);
                ResetSkillTreesCommandTreeNode.resetSkillTrees(data);
                // Reset times-claimed not being properly emptied otherwise
                data.getItemClaims().clear();
                return explorer.success("Player data of &6" + player.getName() + "&e was successfully reset.");
            });
        }
    }


    static class ResetWaypointsCommandTreeNode extends AbstractResetNode {
        public ResetWaypointsCommandTreeNode(CommandTreeNode parent) {
            super(parent, "waypoints");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            return PlayerCommand.execute(player, explorer, () -> {
                resetWaypoints(PlayerData.get(player));
                return explorer.success("Waypoint data of &6" + player.getName() + "&e was successfully reset.");
            });
        }

        static void resetWaypoints(@NotNull PlayerData playerData) {
            playerData.getWaypoints().clear();
        }
    }

    static class ResetQuestsCommandTreeNode extends AbstractResetNode {
        public ResetQuestsCommandTreeNode(CommandTreeNode parent) {
            super(parent, "quests");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            return PlayerCommand.execute(player, explorer, () -> {
                resetQuests(PlayerData.get(player));
                return explorer.success("Quest data of &6" + player.getName() + "&e was successfully reset.");
            });
        }

        static void resetQuests(@NotNull PlayerData data) {
            data.getQuestData().resetFinishedQuests();
            data.getQuestData().start(null);
        }
    }

    static class ResetSkillsCommandTreeNode extends AbstractResetNode {
        public ResetSkillsCommandTreeNode(CommandTreeNode parent) {
            super(parent, "skills");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            return PlayerCommand.execute(player, explorer, () -> {
                resetSkills(PlayerData.get(player));
                return explorer.success("Skill data of &6" + player.getName() + "&e was successfully reset.");
            });
        }

        static void resetSkills(@NotNull PlayerData data) {
            data.resetSkills();
            data.setUnlockedItems(new HashSet<>()); // TODO class-specific unlockables etc.
        }
    }

    static class ResetSkillTreesCommandTreeNode extends AbstractResetNode {
        public ResetSkillTreesCommandTreeNode(CommandTreeNode parent) {
            super(parent, "skill-trees");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            return PlayerCommand.execute(player, explorer, () -> {
                resetSkillTrees(PlayerData.get(player));
                return explorer.success("Skill tree data of &6" + player.getName() + "&e was successfully reset.");
            });
        }

        // TODO option to reallocate skill tree points instead of not giving any back
        static void resetSkillTrees(@NotNull PlayerData data) {
            data.resetSkillTrees();
        }
    }

    static class ResetAttributesCommandTreeNode extends AbstractResetNode {
        public ResetAttributesCommandTreeNode(CommandTreeNode parent) {
            super(parent, "attributes");

            addArgument(new Argument<>("-reallocate", (explore, list) -> list.add("-reallocate"), (explore, input) -> input, explorer -> ""));
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            final boolean givePoints = args.length > 4 && args[4].equalsIgnoreCase("-reallocate");
            return PlayerCommand.execute(player, explorer, () -> {
                resetAttributes(PlayerData.get(player), givePoints);
                return explorer.success("Attribute data of &6" + player.getName() + "&e was successfully reset.");
            });
        }

        static void resetAttributes(@NotNull PlayerData data, boolean givePoints) {

            // Give back attribute points
            if (givePoints) {

                int points = 0;
                for (var inst : data.getAttributes().getInstances()) {
                    points += inst.getBase();
                    inst.setBase(0);
                }

                data.giveAttributePoints(points);
                return;
            }

            for (PlayerAttribute attribute : MMOCore.plugin.attributeManager.getAll()) {
                attribute.resetAdvancement(data, true);
                data.getAttributes().getInstance(attribute).setBase(0);
            }
        }
    }

    static class ResetLevelsCommandTreeNode extends AbstractResetNode {
        public ResetLevelsCommandTreeNode(CommandTreeNode parent) {
            super(parent, "levels");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            return PlayerCommand.execute(player, explorer, () -> {
                resetLevels(PlayerData.get(player));
                return explorer.success("Main and profession levels of &6" + player.getName() + "&e were successfully reset.");
            });
        }

        static void resetLevels(@NotNull PlayerData data) {

            // Class
            data.setLevel(MMOCore.plugin.playerDataManager.getDefaultData().getLevel(), PlayerLevelChangeEvent.Reason.RESET);
            data.setExperience(0);
            data.getProfess().resetAdvancement(data, true);

            // Professions
            for (Profession profession : MMOCore.plugin.professionManager.getAll()) {
                data.getCollectionSkills().setExperience(profession, 0);
                data.getCollectionSkills().setLevel(profession, 0, PlayerLevelChangeEvent.Reason.RESET);
                profession.resetAdvancement(data, true);
            }
        }
    }

    static class ResetClassesCommandTreeNode extends AbstractResetNode {
        public ResetClassesCommandTreeNode(CommandTreeNode parent) {
            super(parent, "classes");
        }

        @Override
        public @NotNull CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
            Player player = explorer.parse(argPlayer);

            return PlayerCommand.execute(player, explorer, () -> {
                resetClasses(PlayerData.get(player));
                return explorer.success("Class data of &6" + player.getName() + "&e was successfully reset.");
            });
        }

        static void resetClasses(@NotNull PlayerData data) {
            MMOCore.plugin.classManager.getAll().forEach(data::unloadClassInfo);
            MMOCore.plugin.playerDataManager.getDefaultData().apply(data, PlayerLevelChangeEvent.Reason.RESET);
            data.setClass(MMOCore.plugin.classManager.getDefaultClass());
        }
    }
}
