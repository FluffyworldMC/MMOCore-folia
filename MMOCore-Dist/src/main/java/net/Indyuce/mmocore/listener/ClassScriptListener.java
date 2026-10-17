package net.Indyuce.mmocore.listener;

import net.Indyuce.mmocore.api.event.PlayerChangeClassEvent;
import net.Indyuce.mmocore.api.event.PlayerLevelChangeEvent;
import net.Indyuce.mmocore.script.trigger.MMOCoreTriggerType;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * This class calls trigger types registered by MMOCore
 * which are specific to player classes.
 *
 * @see MMOCoreTriggerType
 */
public class ClassScriptListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClassChange(PlayerChangeClassEvent event) {

        // With delay
        Tasks.runEntity(event.getData().getPlayer(), () -> {
            final var caster = event.getData().getMMOPlayerData();
            caster.triggerSkills(MMOCoreTriggerType.CLASS_CHOSEN);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLevelUp(PlayerLevelChangeEvent event) {

        // With delay
        if (event.getReason() == PlayerLevelChangeEvent.Reason.LEVEL_UP)
            Tasks.runEntity(event.getData().getPlayer(), () -> {
                final var caster = event.getData().getMMOPlayerData();
                caster.triggerSkills(MMOCoreTriggerType.LEVEL_UP);
            });
    }

   /* @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        performScripts(event.getPlayer(), MMOCoreTriggerType.BREAK_BLOCK);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockBreakEvent event) {
        performScripts(event.getPlayer(), MMOCoreTriggerType.PLACE_BLOCK);
    }*/
}
