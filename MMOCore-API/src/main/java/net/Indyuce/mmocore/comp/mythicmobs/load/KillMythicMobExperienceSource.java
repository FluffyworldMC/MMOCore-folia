package net.Indyuce.mmocore.comp.mythicmobs.load;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.lib.api.MMOLineConfig;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.dispenser.ExperienceDispenser;
import net.Indyuce.mmocore.experience.source.type.SpecificExperienceSource;
import net.Indyuce.mmocore.manager.profession.ExperienceSourceManager;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

public class KillMythicMobExperienceSource extends SpecificExperienceSource<String> {
    private final String internalName;

    public KillMythicMobExperienceSource(ExperienceDispenser dispenser, MMOLineConfig config) {
        super(dispenser, config);

        config.validate("type");
        internalName = config.getString("type");
    }

    @Override
    public ExperienceSourceManager<KillMythicMobExperienceSource> newManager() {
        return new ExperienceSourceManager<KillMythicMobExperienceSource>() {
            @EventHandler
            public void a(MythicMobDeathEvent event) {
                Entity dead = event.getEntity();
                Entity killer = event.getKiller();
                Location deathLocation = dead.getLocation().clone();
                String mobName = event.getMobType().getInternalName();
                Runnable grant = () -> {
                    if (!(killer instanceof Player) || !((Player) killer).isOnline()) return;
                    Player player = (Player) killer;
                    Tasks.executeEntity(player, () -> {
                        if (player.hasMetadata("NPC")) return;
                        PlayerData data = PlayerData.get(player);
                        for (KillMythicMobExperienceSource source : getSources())
                            if (source.matches(data, mobName))
                                source.giveExperience(data, 1, deathLocation);
                    });
                };
                if (dead.getScheduler().runDelayed(MMOCore.plugin, task -> {
                    if (!dead.isDead()) return;
                    grant.run();
                }, grant, 2L) == null) grant.run();
            }
        };
    }

    @Override
    public boolean matchesParameter(PlayerData player, String name) {
        return name.equals(internalName);
    }
}
