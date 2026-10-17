package net.Indyuce.mmocore.command.builtin;

import io.lumine.mythic.lib.command.CommandTreeExplorer;
import io.lumine.mythic.lib.command.CommandTreeRoot;
import io.lumine.mythic.lib.command.argument.Argument;
import io.lumine.mythic.lib.util.lang3.Validate;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.social.FriendRequest;
import net.Indyuce.mmocore.api.player.social.Request;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.manager.InventoryManager;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FriendsCommand extends CommandTreeRoot {
    private final Argument<String> argAction;

    public FriendsCommand(ConfigurationSection config) {
        super(config);

        argAction = addArgument(Arguments.ACCEPT_OR_DENY_OPTIONAL);
        setOnlyForPlayers();
    }

    @Override
    @NotNull
    public CommandResult execute(CommandTreeExplorer explorer, CommandSender sender, String[] args) {
        final var action = explorer.parse(argAction);

        PlayerData playerData = PlayerData.get((Player) sender);
        if (MMOCoreUtils.callLegacyCommandEvent(playerData, this)) return CommandResult.FAILURE;

        // Open inventory
        if (action == null) {
            Tasks.openInventory((Player) sender, () -> InventoryManager.FRIEND_LIST.newInventory(playerData));
            return CommandResult.SUCCESS;
        }

        final @Nullable FriendRequest invite;
        if (args.length > 1)

            // Search by request ID
            try {
                final UUID uuid = UUID.fromString(args[1]);
                final Request req = MMOCore.plugin.requestManager.getRequest(uuid);
                Validate.isTrue(!req.isTimedOut() && req instanceof FriendRequest);
                Validate.isTrue(!playerData.hasFriend(req.getCreator().getUniqueId()));
                invite = (FriendRequest) req;
            } catch (Exception exception) {
                return CommandResult.FAILURE;
            }

            // Search by target player
        else invite = MMOCore.plugin.requestManager.findRequest(playerData, FriendRequest.class);

        // No invite found with given identifier/target player
        if (invite == null) {
            Message.FRIEND_NO_PENDING_INVITE.send(playerData);
            return CommandResult.FAILURE;
        }

        if (args[0].equalsIgnoreCase("accept")) invite.accept();
        if (args[0].equalsIgnoreCase("deny")) invite.deny();

        return CommandResult.SUCCESS;
    }
}
