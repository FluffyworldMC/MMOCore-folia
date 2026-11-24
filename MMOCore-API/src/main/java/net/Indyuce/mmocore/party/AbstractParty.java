package net.Indyuce.mmocore.party;

import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public interface AbstractParty {

    /**
     * @return If given player is in that party
     */
    default boolean hasMember(@NotNull Player player) {
        for (PlayerData member : getOnlineMembers())
            if (member.getPlayer().equals(player)) return true;
        return false;
    }

    /**
     * @return List of players eligible to EXP splitting
     */
    @NotNull
    default List<PlayerData> findPlayersForExp(@NotNull PlayerData source) {
        var result = new ArrayList<PlayerData>();

        var maxRange = MMOCore.plugin.configManager.partyMaxExpSplitRange;
        var levelRange = MMOCore.plugin.configManager.maxPartyLevelDifference;

        for (var member : getOnlineMembers()) {

            // Basic checks
            // [Bugfix] Dead players don't get any experience
            if (member.equals(source) || member.getPlayer().isDead() || member.hasReachedMaxLevel()) continue;

            // Max level difference
            if (levelRange >= 0 && Math.abs(member.getLevel() - source.getLevel()) > levelRange)
                continue;

            // Exp range
            if (maxRange > 0 && !areClose(member.getPlayer(), source.getPlayer(), maxRange)) continue;

            result.add(member);
        }

        return result;
    }

    private static boolean areClose(Player source, Player target, double distance) {
        final var sourceLocation = source.getLocation();
        final var targetLocation = target.getLocation();
        return sourceLocation.getWorld().equals(targetLocation.getWorld())
                && sourceLocation.distanceSquared(targetLocation) <= distance * distance;
    }

    /**
     * @return List of online members
     */
    List<PlayerData> getOnlineMembers();

    default PlayerData getMember(int n) {
        return getOnlineMembers().get(n);
    }

    /**
     * @return Number of online/offline players in the party
     */
    int countMembers();
}
