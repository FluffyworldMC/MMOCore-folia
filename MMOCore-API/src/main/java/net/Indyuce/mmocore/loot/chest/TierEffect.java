package net.Indyuce.mmocore.loot.chest;

import io.lumine.mythic.lib.util.lang3.Validate;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.loot.chest.particle.ChestParticleEffect;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

public class TierEffect {
	private final ChestParticleEffect type;
	private final Particle particle;
	private final int period;

	public TierEffect(ConfigurationSection config) {
		Validate.notNull(config, "Could not load tier config");
		type = ChestParticleEffect.valueOf(config.getString("type", "OFFSET").toUpperCase().replace("-", "_").replace(" ", "_"));
		particle = Particle.valueOf(config.getString("particle", "FLAME").toUpperCase().replace("-", "_").replace(" ", "_"));
		period = Math.max(20, config.getInt("period", 5 * 20));
	}

	public void play(Location loc) {
		type.play(loc, particle);
	}

	public ScheduledTask startNewRunnable(Location loc) {
		return Tasks.runAtTimer(loc, task -> type.play(loc, particle), 1, period);
	}
}