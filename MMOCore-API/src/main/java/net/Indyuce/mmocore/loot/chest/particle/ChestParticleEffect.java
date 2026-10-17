package net.Indyuce.mmocore.loot.chest.particle;

import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.function.BiConsumer;

public enum ChestParticleEffect {

	HELIX((loc, particle) -> {
		double[] ti = {0};
		Tasks.runAtTimer(loc, task -> {
			if ((ti[0] += Math.PI / 16) > Math.PI * 2) {
				task.cancel();
				return;
			}
			for (double j = 0; j < Math.PI * 2; j += Math.PI * 2 / 5)
				loc.getWorld().spawnParticle(particle, loc.clone().add(Math.cos(j + ti[0] / 2), -.5 + ti[0] / Math.PI / 2, Math.sin(j + ti[0] / 2)), 0);
		}, 1, 1);
	}),

	OFFSET((loc, particle) -> {
		int[] ti = {0};
		Tasks.runAtTimer(loc, task -> {
			if (ti[0]++ > 20) {
				task.cancel();
				return;
			}
			for (double j = 0; j < Math.PI * 2; j += Math.PI * 2 / 5)
				loc.getWorld().spawnParticle(particle, loc.clone(), 1, .5, .5, .5, 0);
		}, 1, 1);
	}),

	GALAXY((loc, particle) -> {
		double[] ti = {0};
		Tasks.runAtTimer(loc, task -> {
			if ((ti[0] += Math.PI / 16) > Math.PI * 2) {
				task.cancel();
				return;
			}
			for (double j = 0; j < Math.PI * 2; j += Math.PI * 2 / 5)
				loc.getWorld().spawnParticle(particle, loc.clone().add(0, -.1, 0), 0, Math.cos(j + ti[0] / 2), 0, Math.sin(j + ti[0] / 2), .13);
		}, 1, 1);
	});

	private final BiConsumer<Location, Particle> func;

	ChestParticleEffect(BiConsumer<Location, Particle> func) {
		this.func = func;
	}

	public void play(Location loc, Particle particle) {
		func.accept(loc, particle);
	}
}
