package net.Indyuce.mmocore.skill.cast;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.message.actionbar.ActionBarPriority;
import io.lumine.mythic.lib.util.Lazy;
import io.lumine.mythic.lib.util.TemporaryHandler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.skill.binding.BoundSkillInfo;
import net.Indyuce.mmocore.util.Tasks;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public abstract class SkillCastingInstance extends TemporaryHandler {
    protected final PlayerData caster;
    protected final SkillCastingHandler handler;
    private final Lazy<List<BoundSkillInfo>> activeSkills;

    protected int counter = -1, sinceLastActivity;

    private ScheduledTask task;

    protected static final int ACTION_BAR_PRIORITY = ActionBarPriority.LOW;

    public SkillCastingInstance(@NotNull SkillCastingHandler handler, @NotNull PlayerData caster) {
        this.handler = handler;
        this.caster = caster;
        this.activeSkills = Lazy.persistent(() -> caster.getBoundSkills().values().stream().filter(bound -> !bound.isPassive()).collect(Collectors.toList()));

        if (caster.isOnline())
            this.task = Tasks.runEntityTimer(caster.getPlayer(), this::tick, 1, 1);
    }

    @NotNull
    public PlayerData getCaster() {
        return caster;
    }

    public void refreshTimeOut() {
        sinceLastActivity = 0;
    }

    @NotNull
    public List<BoundSkillInfo> getActiveSkills() {
        return activeSkills.get();
    }

    private static final int PARTICLES_PER_TICK = 2;
    private static final int RUNNABLE_PERIOD = 0b111;

    @Override
    protected void onClose() {
        Tasks.cancel(task);
        task = null;
    }

    private void tick(@NotNull ScheduledTask scheduled) {

        /*
         * Casting mode can be left from another listener/thread (quit key,
         * class change, logout...) while this task is still queued. When that
         * happens this instance is stale and PlayerData no longer references
         * it, so calling leaveSkillCasting would fail its "not in casting mode"
         * validation and throw every tick. Cancel the task instead.
         */
        if (!caster.isCasting() || caster.getSkillCasting() != this) {
            scheduled.cancel();
            this.task = null;
            return;
        }

        if (UtilityMethods.isInvalidated(caster.getMMOPlayerData()) || !caster.hasActiveSkillBound()) {
            caster.leaveSkillCasting(true);
            return;
        }

        // Check for timeout
        if (handler.doesTimeOut() && sinceLastActivity++ > handler.getTimeoutDelay()) {
            caster.leaveSkillCasting(true);
            return;
        }

        // Apply casting particles
        final var castParticle = caster.getProfess().getCastParticle();
        if (castParticle != null) for (int k = 0; k < PARTICLES_PER_TICK; k++) {
            final double a = (double) (PARTICLES_PER_TICK * counter + k) / 4;
            castParticle.display(caster.getPlayer().getLocation().add(Math.cos(a), 1 + Math.sin(a / 3) / 1.3, Math.sin(a)));
        }

        // Apply casting mode-specific effects
        if ((++counter & RUNNABLE_PERIOD) == 0) {
            activeSkills.flush();
            onTick();
        }
    }

    /**
     * Method ran twice every second while
     * the player is in casting mode
     */
    public abstract void onTick();
}
