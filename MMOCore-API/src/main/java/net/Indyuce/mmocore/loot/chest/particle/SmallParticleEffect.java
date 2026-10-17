package net.Indyuce.mmocore.loot.chest.particle;

import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;

@Deprecated
public class SmallParticleEffect {
    private final Location loc;
    private final Particle particle;
    private final double r;

    private double t;

    /**
     * Causes problems with versions inconsistencies. For instance, SPELL requires
     * different extra data types based on versions. It's safer to move that to
     * MythicLib and give more configurability to the user, and better, until now
     * that was hardcoded, which is pretty sadge
     */
    @Deprecated
    public SmallParticleEffect(Entity entity, Particle particle) {
        this(entity, particle, .7);
    }

    public SmallParticleEffect(Entity entity, Particle particle, double r) {
        this.loc = entity.getLocation().add(0, .5, 0);
        this.particle = particle;
        this.r = r;

        Tasks.runAtTimer(loc, task -> {
            if (t > Math.PI * 2) {
                task.cancel();
                return;
            }

            for (int k = 0; k < 3; k++) {
                t += Math.PI / 10;
                loc.getWorld().spawnParticle(particle, loc.clone().add(r * Math.cos(t), t / Math.PI / 2, r * Math.sin(t)), 0);
            }
        }, 1, 1);
    }
}
