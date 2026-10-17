package net.Indyuce.mmocore.experience.source;

import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.util.lang3.Validate;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.experience.dispenser.ExperienceDispenser;
import net.Indyuce.mmocore.experience.source.type.SpecificExperienceSource;
import net.Indyuce.mmocore.manager.profession.ExperienceSourceManager;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CraftItemExperienceSource extends SpecificExperienceSource<Material> {
    public final Material material;

    public CraftItemExperienceSource(ExperienceDispenser dispenser, MMOLineConfig config) {
        super(dispenser, config);

        config.validate("type");
        material = Material.valueOf(config.getString("type").toUpperCase().replace("-", "_").replace(" ", "_"));
    }

    @Override
    public ExperienceSourceManager<CraftItemExperienceSource> newManager() {
        return new Manager();
    }

    @Override
    public boolean matchesParameter(PlayerData player, Material obj) {
        return material == obj;
    }

    private static class Manager extends ExperienceSourceManager<CraftItemExperienceSource> {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void a(CraftItemEvent event) {
            if (event.getAction() == InventoryAction.NOTHING || event.getInventory().getResult() == null) return;

            PlayerData data = PlayerData.get((Player) event.getWhoClicked());

            /*
             * This makes sure that the crafting recipe was performed correctly.
             *
             * In some scenarii, the CraftItemEvent (which is only a click event
             * by the way) is not cancelled, but the item is not crafted which makes
             * EXP duplication a game breaking glitch. MMOCore make sure that at
             * least one ingredient has lowered in amount.
             *
             * The second objective of that check is to deduce the amount of items that
             * were crafted during this event. For that, it finds the item with the lowest
             * amount in the crafting matrix and see how many items disappeared,
             * multiplied by the recipe output amount (works for a shift click).
             *
             * References:
             * - https://git.lumine.io/mythiccraft/mmocore/-/issues/102
             * - https://www.spigotmc.org/threads/how-to-get-amount-of-item-crafted.377598/
             */
            final var index = getLowerAmountIngredientIndex(event.getInventory().getMatrix());
            final var oldItem = event.getInventory().getMatrix()[index];
            final var oldAmount = oldItem.getAmount();
            final var itemsCraftedPerRecipe = event.getInventory().getResult().getAmount();
            final var resultType = event.getInventory().getResult().getType();

            Tasks.runEntity((Player) event.getWhoClicked(), () -> {

                final var newItem = event.getInventory().getMatrix()[index];
                final var newAmount = getEffectiveNewAmount(newItem, oldItem);

                if (newAmount >= oldAmount) return;

                // Deduce amount crafted
                final var amountCrafted = (event.getClick().isShiftClick() ? oldAmount - newAmount : 1) * itemsCraftedPerRecipe;
                for (CraftItemExperienceSource source : getSources())
                    if (source.matches(data, resultType))
                        source.giveExperience(data, amountCrafted, event.getInventory().getLocation());
            });
        }

        private int getEffectiveNewAmount(@Nullable ItemStack item, @NotNull ItemStack oldItem) {
            // Some items are consumed and leave behind a different type (e.g. buckets)
            // If that's the case just say all items were crafted
            // Fixes https://gitlab.com/phoenix-dvpmt/mmocore/-/issues/1165
            return item == null || item.getType() == Material.AIR || item.getType() != oldItem.getType() ? 0 : item.getAmount();
        }

        private int getLowerAmountIngredientIndex(ItemStack[] matrix) {
            int lower = Integer.MAX_VALUE;
            int index = -1;

            for (int i = 0; i < matrix.length; i++) {
                ItemStack checked = matrix[i];
                if (checked != null && checked.getType() != Material.AIR && checked.getAmount() > 0 && checked.getAmount() < lower) {
                    lower = checked.getAmount();
                    index = i;
                }
            }

            Validate.isTrue(index != -1, "No item in matrix");
            return index;
        }
    }
}
