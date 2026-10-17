package net.Indyuce.mmocore.skill.list;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import io.lumine.mythic.lib.player.skill.PassiveSkill;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.handler.BuiltinSkillHandler;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.result.def.SimpleSkillResult;
import io.lumine.mythic.lib.util.EntityLocationType;
import io.lumine.mythic.lib.util.annotation.BackwardsCompatibility;
import io.lumine.mythic.lib.version.VParticle;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.loot.chest.particle.ParabolicProjectile;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

@BuiltinSkillHandler(mods = {"percent"}, triggerable = false)
public class Ambers extends SkillHandler<SimpleSkillResult> implements Listener {

    @BackwardsCompatibility(version = "1.20.5")
    private final boolean legacyParticles = MythicLib.plugin.getVersion().isUnder(1, 20, 5);

    public Ambers(ConfigurationSection config) {
        super(config);
    }

    @NotNull
    @Override
    public SimpleSkillResult getResult(SkillMetadata meta) {
        return new SimpleSkillResult(meta.hasAttackSource()
                && meta.hasTargetEntity()
                && meta.getTargetEntityOrNull() instanceof LivingEntity);
    }

    @Override
    public void whenCast(SimpleSkillResult result, SkillMetadata skillMeta) {
        LivingEntity target = (LivingEntity) skillMeta.getTargetEntityOrNull();
        Location loc = target.getLocation();

        double a = RANDOM.nextDouble() * 2 * Math.PI;
        new Amber(skillMeta.getCaster().getData(), EntityLocationType.BODY.getLocation(target), loc.clone().add(4 * Math.cos(a), 0, 4 * Math.sin(a)), skillMeta.getParameter("percent"));
    }

    @EventHandler
    public void spawnAmber(PlayerAttackEvent event) {
        MMOPlayerData data = event.getAttacker().getData();
        if (!event.getAttack().getDamage().hasType(DamageType.SKILL)) return;

        PassiveSkill passive = data.getPassiveSkillMap().getSkill(this);
        if (passive == null) return;

        passive.getTriggeredSkill().cast(SkillMetadata.of(event));
    }

    class Amber {
        private final Location loc;
        private final MMOPlayerData data;
        private final double percent;

        private int j;
        private ScheduledTask task;

        private static final double RADIUS_SQUARED = 3;

        private Amber(MMOPlayerData data, Location source, Location loc, double percent) {
            this.loc = loc;
            this.data = data;
            this.percent = percent / 100;

            new ParabolicProjectile(source, loc, VParticle.REDSTONE.get(), this::start, 1, Color.ORANGE, 1.3f);
        }

        private void start() {
            task = Tasks.runAtTimer(loc, scheduled -> tick(scheduled), 1, 3);
        }

        private void tick(ScheduledTask scheduled) {
            if (j++ > 66 || !data.isOnline()) {
                scheduled.cancel();
                return;
            }

            Player player = data.getPlayer();
            Tasks.executeEntity(player, () -> {
                if (!player.getWorld().equals(loc.getWorld())) {
                    Tasks.cancel(task);
                    return;
                }

                if (player.getLocation().add(0, 1, 0).distanceSquared(loc) < RADIUS_SQUARED) {
                    player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1, 1);

                    PlayerData playerData = PlayerData.get(data);
                    double missingMana = data.getStatMap().getStat("MAX_MANA") - playerData.getMana();
                    playerData.giveMana(missingMana * percent, ResourceUpdateReason.SKILL);

                    Tasks.cancel(task);
                }
            });

            for (int j = 0; j < 5; j++)
                if (legacyParticles)
                    loc.getWorld().spawnParticle(VParticle.ENTITY_EFFECT.get(), loc, 0, 1, 0.647, 0, 1);
                else
                    loc.getWorld().spawnParticle(VParticle.ENTITY_EFFECT.get(), loc, 0, 1, 0.647, 0, 1, Color.ORANGE);

            loc.getWorld().spawnParticle(VParticle.REDSTONE.get(), loc, 1, new Particle.DustOptions(Color.ORANGE, 1.3f));
        }
    }
}
