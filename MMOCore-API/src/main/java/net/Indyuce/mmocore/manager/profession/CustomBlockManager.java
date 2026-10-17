package net.Indyuce.mmocore.manager.profession;

import io.lumine.mythic.lib.api.MMOLineConfig;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.util.Tasks;
import net.Indyuce.mmocore.api.block.*;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.loot.chest.condition.Condition;
import net.Indyuce.mmocore.loot.chest.condition.ConditionInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

// TODO move to standalone module class. merge with RestrictionManager. rename to CustomMiningModule and properly register/unregister listeners
public class CustomBlockManager extends SpecificProfessionManager {

    /**
     * Registered block infos
     */
    private final Map<BlockType, BlockInfo> map = new HashMap<>();

    /**
     * Blocks that are regenerating and that must be refreshed whenever the
     * server reloads or shuts down not to hurt the world map
     */
    private final Set<BlockInfo.RegeneratingBlock> active = ConcurrentHashMap.newKeySet();

    /**
     * Stores conditions which must be met to apply custom mining
     */
    private final List<Condition> customMineConditions = new ArrayList<>();

    /**
     * List of functions which let MMOCore recognize what block a player is
     * currently breaking
     */
    private final List<Function<Block, Optional<BlockType>>> blockTypes = new ArrayList<>();

    private boolean enabled, protectVanillaBlocks, enableToolRestrictions;

    public CustomBlockManager() {
        super("on-mine");

        registerBlockType(block -> MMOCoreUtils.isPlayerHead(block.getType()) ? Optional.of(new SkullBlockType(block)) : Optional.empty());
        registerBlockType(block -> block.getType() == Material.NOTE_BLOCK ? Optional.of(new NoteBlockType(block)) : Optional.empty());
        registerBlockType(block -> isMushroom(block.getType()) ? Optional.of(new MushroomBlockType(block)) : Optional.empty());
    }

    private static boolean isMushroom(Material material) {
        return material == Material.MUSHROOM_STEM
                || material == Material.BROWN_MUSHROOM_BLOCK
                || material == Material.RED_MUSHROOM_BLOCK;
    }

    public void registerBlockType(Function<Block, Optional<BlockType>> function) {
        // Very important - add at index 0 so that block types
        // that are registered later take precedence over default
        // block types, that are built-in MMOCore
        blockTypes.add(0, function);
    }

    public void register(@NotNull BlockInfo regen) {
        map.put(regen.getBlock(), regen);
    }

    /**
     * Checks if the behaviour of a block was changed by a specific profession
     * (different drop tables, block regen..)
     *
     * @param block Block to check
     * @return The new block behaviour or null if no new behaviour
     */
    @Nullable
    public BlockInfo getInfo(@NotNull Block block) {
        return map.get(findBlockType(block));
    }

    @NotNull
    public BlockType findBlockType(Block block) {
        for (var blockType : blockTypes) {
            var type = blockType.apply(block);
            if (type.isPresent()) return type.get();
        }

        return new VanillaBlockType(block);
    }

    /**
     * Used when a block is being broken and MMOCore needs to regen it after X
     * seconds. Also places the temporary block at the block location
     *
     * @param info          Block info
     * @param scheduleRegen If block regeneration should be scheduled or not. If
     *                      the block broken is a temporary block and is part of
     *                      a "block chain", no regen should be scheduled as
     *                      there is already one
     */
    public void initialize(BlockInfo.RegeneratingBlock info, boolean scheduleRegen) {
        if (scheduleRegen) {
            active.add(info);
            Tasks.runAtLater(info.getLocation(), () -> regen(info, false), info.getRegeneratingBlock().getRegenerationInfo().getTime());
        }

        if (info.getRegeneratingBlock().getRegenerationInfo().hasTemporaryBlock())
            info.getRegeneratingBlock().getRegenerationInfo().getTemporaryBlock().place(info);
    }

    /**
     * Called when a block regens, either due to regen timer or because the
     * server shuts down.
     *
     * @param info     Block which must be regened
     * @param shutdown Must be set to true if the server is shutting down. When
     *                 the server shuts down, it iterates through active blocks.
     *                 This prevents any issue when editing lists being iterated
     */
    private void regen(BlockInfo.RegeneratingBlock info, boolean shutdown) {

        Location loc = info.getLocation();
        if (loc.getWorld() == null) return;
        loc.getWorld().getChunkAtAsync(loc).whenComplete((chunk, ex) -> {
            if (ex != null || chunk == null) return;
            Tasks.executeAt(loc, () -> {
                info.getRegeneratingBlock().getBlock().regenerate(info);
                info.getLocation().getBlock().getState().update();
                if (!shutdown)
                    active.remove(info);
            });
        });
    }

    /**
     * Called when the server disables so every mined block which was in timer
     * are reset and put back in place.
     */
    public void resetRemainingBlocks() {
        active.forEach(info -> regen(info, true));
    }

    /**
     * @param block Potentially vanilla block being broken by a player
     * @return Returns if the block being broken is a temporary block. If
     *         it is, players should not be able to break it
     */
    public boolean isTemporaryBlock(Block block) {
        Location loc = block.getLocation();
        for (BlockInfo.RegeneratingBlock info : active)
            if (info.getLocation().getBlockX() == loc.getBlockX() && info.getLocation().getBlockY() == loc.getBlockY()
                    && info.getLocation().getBlockZ() == loc.getBlockZ())
                return true;

        return false;
    }

    public boolean isEnabled(Entity entity) {
        return isEnabled(entity, entity.getLocation());
    }

    public boolean isEnabled(Entity entity, Location loc) {

        // Global enable/disable
        if (!enabled) return false;

        // Empty conditions => enabled server-wide
        if (customMineConditions.isEmpty()) return true;

        // Check conditions one-by-one
        var conditionEntity = new ConditionInstance(entity, loc);
        for (Condition condition : customMineConditions)
            if (!condition.isMet(conditionEntity)) return false;

        return true;
    }

    @Override
    public void loadProfessionConfiguration(ConfigurationSection config) {
        for (String key : config.getKeys(false))
            try {
                register(new BlockInfo(config.getConfigurationSection(key)));
            } catch (IllegalArgumentException exception) {
                MMOCore.log(Level.WARNING, "Could not load custom block '" + key + "': " + exception.getMessage());
            }
    }

    public boolean protectVanillaBlocks() {
        return protectVanillaBlocks;
    }

    public boolean hasToolRestrictions() {
        return enableToolRestrictions;
    }

    @Override
    public void initialize(boolean clearBefore) {
        if (clearBefore) {
            customMineConditions.clear();
            map.clear();
        }

        final var config = MMOCore.plugin.getConfig().getConfigurationSection("custom-mining");

        this.enabled = config.getBoolean("enable");
        this.protectVanillaBlocks = config.getBoolean("protect-vanilla-blocks");
        this.enableToolRestrictions = config.getBoolean("enable-tool-restrictions");

        // Avoid warnings if disabled
        if (enabled)
            for (String key : config.getStringList("conditions"))
                try {
                    customMineConditions.add(MMOCore.plugin.loadManager.loadCondition(new MMOLineConfig(key)));
                } catch (IllegalArgumentException exception) {
                    MMOCore.plugin.getLogger().log(Level.WARNING, "Could not load custom mining condition '" + key + "': " + exception.getMessage());
                }
        else customMineConditions.clear();
    }

    @Deprecated
    public boolean shouldProtect() {
        return protectVanillaBlocks();
    }
}
