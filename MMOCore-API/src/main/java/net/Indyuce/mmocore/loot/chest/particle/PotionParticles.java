package net.Indyuce.mmocore.loot.chest.particle;

import io.lumine.mythic.lib.version.VParticle;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Color;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.meta.PotionMeta;

public class PotionParticles {
	private double r, g, b;
	private final ThrownPotion potion;
	private boolean valid = true;

	public PotionParticles(ThrownPotion potion) {
		this.potion = potion;

		Color color = ((PotionMeta) potion.getItem().getItemMeta()).getColor();
		if (color == null) {
			valid = false;
			return;
		}

		r = Math.max((double) 1 / 255, ratio(color.getRed()));
		g = ratio(color.getGreen());
		b = ratio(color.getBlue());

	}

	public void start() {
		if (!valid) return;
		Tasks.runEntityTimer(potion, task -> {
			if (potion.isDead()) {
				task.cancel();
				return;
			}

			potion.getWorld().spawnParticle(VParticle.ENTITY_EFFECT.get(), potion.getLocation(), 0, r, g, b);
		}, 1, 1);
	}

	private double ratio(int l) {
		return (double) l / 255.;
	}
}
