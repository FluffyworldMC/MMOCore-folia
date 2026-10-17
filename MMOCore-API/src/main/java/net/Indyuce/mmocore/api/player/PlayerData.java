package net.Indyuce.mmocore.api.player;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.data.SynchronizedDataHolder;
import io.lumine.mythic.lib.player.cooldown.CooldownMap;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import io.lumine.mythic.lib.player.resource.Resources;
import io.lumine.mythic.lib.profile.SessionUpdateReason;
import io.lumine.mythic.lib.skill.handler.SkillHandler;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import io.lumine.mythic.lib.util.lang3.Validate;
import io.lumine.mythic.lib.version.VParticle;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.*;
import net.Indyuce.mmocore.api.event.unlocking.ItemLockedEvent;
import net.Indyuce.mmocore.api.event.unlocking.ItemUnlockedEvent;
import net.Indyuce.mmocore.api.player.attribute.AttributeInstance;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttribute;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttributes;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.api.player.profess.SavedClassInformation;
import net.Indyuce.mmocore.api.player.profess.Subclass;
import net.Indyuce.mmocore.api.player.profess.resource.PlayerResource;
import net.Indyuce.mmocore.api.player.social.FriendRequest;
import net.Indyuce.mmocore.api.player.stats.PlayerStats;
import net.Indyuce.mmocore.api.quest.PlayerQuests;
import net.Indyuce.mmocore.api.quest.trigger.Trigger;
import net.Indyuce.mmocore.api.quest.trigger.api.Removable;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.command.Arguments;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.ExperienceObject;
import net.Indyuce.mmocore.experience.PlayerProfessions;
import net.Indyuce.mmocore.experience.Profession;
import net.Indyuce.mmocore.experience.droptable.ExperienceItem;
import net.Indyuce.mmocore.experience.droptable.ExperienceTable;
import net.Indyuce.mmocore.gui.skilltree.NodeIncrementResult;
import net.Indyuce.mmocore.guild.provided.Guild;
import net.Indyuce.mmocore.manager.data.OfflinePlayerData;
import net.Indyuce.mmocore.party.AbstractParty;
import net.Indyuce.mmocore.party.PartyUtils;
import net.Indyuce.mmocore.party.provided.MMOCorePartyModule;
import net.Indyuce.mmocore.party.provided.Party;
import net.Indyuce.mmocore.player.ClassDataContainer;
import net.Indyuce.mmocore.player.CombatHandler;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.player.Unlockable;
import net.Indyuce.mmocore.skill.ClassSkill;
import net.Indyuce.mmocore.skill.RegisteredSkill;
import net.Indyuce.mmocore.skill.binding.BoundSkillInfo;
import net.Indyuce.mmocore.skill.binding.SkillSlot;
import net.Indyuce.mmocore.skill.cast.SkillCastingInstance;
import net.Indyuce.mmocore.skill.cast.SkillCastingMode;
import net.Indyuce.mmocore.skilltree.NodeState;
import net.Indyuce.mmocore.skilltree.ParentInformation;
import net.Indyuce.mmocore.skilltree.SkillTreeNode;
import net.Indyuce.mmocore.skilltree.display.PathState;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import net.Indyuce.mmocore.util.Language;
import net.Indyuce.mmocore.util.Tasks;
import net.Indyuce.mmocore.waypoint.Waypoint;
import net.Indyuce.mmocore.waypoint.WaypointOption;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PlayerData extends SynchronizedDataHolder implements OfflinePlayerData, ClassDataContainer {

    /**
     * Can be null, the {@link #getProfess()} method will return the
     * player class, or the default one if this field is null.
     * <p>
     * NEVER access the player class using this field, you must
     * use the {@link #getProfess()} method instead
     */
    // TODO convert to live reference
    @Nullable
    private PlayerClass profess;
    private int level, classPoints, skillPoints, attributePoints, attributeReallocationPoints, skillTreeReallocationPoints, skillReallocationPoints;
    private double experience;

    /**
     * Saving resources (especially health) right in player data fixes TONS of issues.
     */
    private double lastHealth, lastMana, lastStamina, lastStellium;
    private double mana, stamina, stellium;
    private Guild guild;
    private SkillCastingInstance skillCasting;
    private final PlayerQuests questData;
    private final PlayerStats playerStats;
    private final List<UUID> friends = new ArrayList<>();

    /**
     * TODO Use {@link #hasUnlocked(Unlockable)} instead
     * <p>
     * Merge waypoints with unlocked items, and create a method
     * plugin-scope to check if some item is class-specific and
     * should be reset when switching class
     */
    private final Set<String> waypoints = new HashSet<>();
    private final Map<String, Integer> skills = new HashMap<>();
    private final Map<Integer, BoundSkillInfo> boundSkills = new HashMap<>();
    private final PlayerProfessions collectSkills = new PlayerProfessions(this);
    private final PlayerAttributes attributes = new PlayerAttributes(this);
    private final Map<String, SavedClassInformation> classSlots = new HashMap<>();
    private final Map<PlayerActivity, Long> lastActivity = new HashMap<>();
    private final CombatHandler combat = new CombatHandler(this);

    /**
     * Saves the NSK's of the items that have been unlocked in the format "namespace:key".
     * This is used for:
     * - waypoints
     * - skills
     */
    private final Set<String> unlockedItems = new HashSet<>();

    /**
     * Saves the amount of times the player has claimed some item in any exp
     * table. The key used in the map is the identifier of the exp table item.
     */
    private final Map<String, Integer> tableItemClaims = new HashMap<>();

    // NON-FINAL player data stuff made public to facilitate field change
    public boolean noCooldown;
    public int permSkillScrollIndex;

    public PlayerData(MMOPlayerData mmoData) {
        super(MMOCore.plugin, mmoData);

        questData = new PlayerQuests(this);
        playerStats = new PlayerStats(this);
    }

    /**
     * Update all references after /mmocore reload so there can be garbage
     * collection with old plugin objects like class or skill instances.
     * <p>
     * It's OK if bound skills disappear because of a configuration issue
     * on the user's end. After all this method is only called when using
     * /mmocore reload
     */
    public void reload() {

        // TODO switch to lazy persistent?
        try {
            profess = profess == null ? null : MMOCore.plugin.classManager.get(profess.getId());
        } catch (NullPointerException exception) {
            MMOCore.log(Level.SEVERE, "[Userdata] Could not find class " + getProfess().getId() + " while refreshing player data.");
        }

        // Update references to dead skills in bound skills
        // TODO switch to lazy persistent?
        for (var entry : new HashMap<>(boundSkills).entrySet())
            try {
                final @Nullable SkillSlot skillSlot = getProfess().getSkillSlot(entry.getKey());
                final String skillId = entry.getValue().getClassSkill().getSkill().getId();
                final @Nullable ClassSkill classSkill = getProfess().getSkill(skillId);
                Validate.notNull(skillSlot, "Could not find skill slot n" + entry.getKey());
                Validate.notNull(classSkill, "Could not find skill with ID '" + skillId + "'");
                unbindSkill(entry.getKey());
                bindSkill(entry.getKey(), classSkill);
            } catch (Exception exception) {
                MMOCore.plugin.getLogger().log(Level.WARNING, "Could not reload data of '" + getPlayer().getName() + "': " + exception.getMessage());
                exception.printStackTrace();
            }

        for (SkillTree skillTree : getProfess().getSkillTrees())
            for (SkillTreeNode node : skillTree.getNodes())
                if (!nodeLevels.containsKey(node)) nodeLevels.put(node, 0);

        setupSkillTrees();
        applyTemporaryTriggers();
        getStats().updateStats();

        // Hardbind skills
        for (var skillSlot : getProfess().getSlots())
            if (skillSlot.getHardBind() != null) this.bindSkill(skillSlot.getSlot(), skillSlot.getHardBind());

        // Flush references to dead attributes
        attributes.reload();
    }

    /**
     * Some triggers are marked with the {@link Removable} interface as
     * they are non-permanent triggers, and they need to be re-applied
     * everytime their MMOPlayerData gets flushed from the MythicLib cache
     * (everytime the player logs out).
     * <p>
     * This method goes through all the player's experience tables that
     * they have spent points into and register all their non-permanent triggers.
     */
    public void applyTemporaryTriggers() {

        // Remove all stats and buffs associated to triggers
        resetTriggerStats();

        // Experience tables from main class
        if (getProfess().hasExperienceTable())
            getProfess().getExperienceTable().applyTemporaryTriggers(this, getProfess());

        // Experience tables from professions
        for (Profession profession : MMOCore.plugin.professionManager.getAll())
            if (profession.hasExperienceTable())
                profession.getExperienceTable().applyTemporaryTriggers(this, profession);

        // Experience tables from skill tree nodes
        for (SkillTree skillTree : MMOCore.plugin.skillTreeManager.getAll())
            for (SkillTreeNode node : skillTree.getNodes())
                node.getExperienceTable().applyTemporaryTriggers(this, node);
    }

    @Override
    protected void onSessionReady() {

        UtilityMethods.debug(MMOCore.plugin, "SQL", String.format("{ class: %s, level: %d }", this.getProfess().getId(), this.getLevel()));

        // Update class stats and all
        this.setupSkillTrees();
        this.applyTemporaryTriggers();
        this.getStats().updateStats();
        PartyUtils.resolvePartyBonuses(this); // In case buffs not removed on logoff

        tryForceClassSelection(); // Force class selection
    }

    //region Force class selection

    private volatile Runnable bufferedForcedClassSelectionCallback;
    private final AtomicBoolean bufferedForcedClassSelection = new AtomicBoolean(false);
    public boolean hasChosenClass = false;

    private void tryForceClassSelection() {
        final var canOpen = this.bufferedForcedClassSelection.getAndSet(true);
        // Useless to check if `forceClassSelect` is enabled, it must be if `canOpen` is `true`
        if (canOpen) {
            this.bufferedForcedClassSelectionCallback.run();
            this.bufferedForcedClassSelectionCallback = null;
        }
    }

    public void bufferForceClassSelection(@NotNull Runnable callback) {
        this.bufferedForcedClassSelectionCallback = callback;
        final var canOpen = this.bufferedForcedClassSelection.getAndSet(true);
        if (canOpen) {
            this.bufferedForcedClassSelectionCallback.run();
            this.bufferedForcedClassSelectionCallback = null;
        }
    }

    //endregion

    private void callLoginScripts() {

        // Class Skills
        for (var skill : getProfess().getSkills())
            if (skill.getTrigger() == TriggerType.LOGIN)
                skill.toCastable(this).cast(getMMOPlayerData());

        // Call Scripts
        for (var script : getProfess().getScripts())
            if (script.getTrigger() == TriggerType.LOGIN) script.getTriggeredSkill().cast(getMMOPlayerData());
    }

    public void onSessionOpen() {
        this.callLoginScripts();

        // Set health again
        UtilityMethods.setHealth(getPlayer(), lastHealth);
        setMana(lastMana, ResourceUpdateReason.CHOOSE_CLASS);
        setStamina(lastStamina, ResourceUpdateReason.CHOOSE_CLASS);
        setStellium(lastStellium, ResourceUpdateReason.CHOOSE_CLASS);
    }

    @Override
    public Map<Integer, String> mapBoundSkills() {
        Map<Integer, String> result = new HashMap<>();
        for (int slot : boundSkills.keySet())
            result.put(slot, boundSkills.get(slot).getClassSkill().getSkill().getId());
        return result;
    }

    public void resetTriggerStats() {
        getMMOPlayerData().getStatMap().getInstances().forEach(statInstance -> statInstance.removeIf(Trigger.STAT_MODIFIER_KEY::equals));
        getMMOPlayerData().getSkillModifierMap().removeModifiers(Trigger.STAT_MODIFIER_KEY);
    }

    public void resetAttributeStats() {
        getMMOPlayerData().getStatMap().getInstances().forEach(statInstance -> statInstance.removeIf(AttributeInstance.MODIFIER_KEY::equals));
    }

    //region Skill trees

    /**
     * Current state of each node. This does not get saved in the player database
     * as it can be inferred from the skill tree node levels map. This is used
     * mainly for displaying the skill tree in the GUI.
     */
    private final Map<SkillTreeNode, NodeState> nodeStates = new HashMap<>();

    /**
     * Current state of each path. This does not get saved in the player database
     * as it can be inferred from the skill tree node levels map. This is used
     * mainly for displaying the skill tree in the GUI.
     */
    private final Map<ParentInformation, PathState> edgeStates = new HashMap<>();

    private final Map<SkillTreeNode, Integer> nodeLevels = new HashMap<>();
    private final Map<String, Integer> skillTreePoints = new HashMap<>();

    /**
     * Cached data
     * <p>
     * Amount of points spent in each tree. This does not get saved in the
     * player database as it can be inferred from the skill tree node levels map.
     */
    private final Map<SkillTree, Integer> skillTreePointsSpent = new HashMap<>();

    @Override
    public int getSkillTreeReallocationPoints() {
        return skillTreeReallocationPoints;
    }

    private void setupSkillTrees() {

        // Node states setup
        for (SkillTree skillTree : getProfess().getSkillTrees())
            skillTree.resolveStates(this);
    }

    public int getPointsSpent(@NotNull SkillTree skillTree) {
        return skillTreePointsSpent.getOrDefault(skillTree, 0);
    }

    @NotNull
    private static String asKey(@Nullable SkillTree skillTree) {
        return skillTree == null ? Arguments.SKILL_TREE_GLOBAL_KEY : skillTree.getId();
    }

    public void setSkillTreePoints(@NotNull SkillTree skillTree, int points) {
        setSkillTreePoints(asKey(skillTree), points);
    }

    public void setSkillTreePoints(@NotNull String skillTreeId, int points) {
        if (points <= 0) skillTreePoints.remove(skillTreeId);
        else skillTreePoints.put(skillTreeId, points);
    }

    public void giveSkillTreePoints(@Nullable SkillTree skillTree, int val) {
        giveSkillTreePoints(asKey(skillTree), val);
    }

    public void giveSkillTreePoints(@NotNull String id, int val) {
        skillTreePoints.merge(id, Math.max(0, val), (points, ignored) -> Math.max(0, points + val));
    }

    /**
     * Make a copy to make sure that the object
     * created is independent of the state of playerData.
     */
    @NotNull
    public Map<String, Integer> mapSkillTreePoints() {
        return new HashMap<>(skillTreePoints);
    }

    @Override
    public Map<String, Integer> getNodeLevels() {
        var mapped = new HashMap<String, Integer>();
        this.nodeLevels.forEach((node, level) -> mapped.put(node.getFullId(), level));
        return mapped;
    }

    public void resetSkillTrees() {

        // Un-apply triggers
        for (SkillTree tree : getProfess().getSkillTrees())
            for (SkillTreeNode node : tree.getNodes())
                node.resetAdvancement(this, false);

        // Skill trees progress
        nodeLevels.clear();
        nodeStates.clear(); // Cache data
        edgeStates.clear();
        skillTreePointsSpent.clear();
        tableItemClaims.keySet().removeIf(s -> s.startsWith(SkillTreeNode.KEY_PREFIX)); // Clear node claim count

        // Skill tree (realloc) points
        skillTreePoints.clear();
        skillTreeReallocationPoints = 0;

        // Setup skill trees again
        setupSkillTrees();
    }

    public void clearStates(@NotNull SkillTree skillTree) {
        for (var node : skillTree.getNodes()) nodeStates.remove(node);
        // TODO clear path states.
    }

    @NotNull
    public NodeIncrementResult canIncrementNodeLevel(@NotNull SkillTreeNode node) {
        // TODO move to UI...?
        final var nodeState = nodeStates.get(node);

        // Node is maxed out
        //if (getNodeLevel(node) >= node.getMaxLevel()) return NodeIncrementResult.MAX_LEVEL_REACHED;
        if (nodeState == NodeState.MAXED_OUT) return NodeIncrementResult.MAX_LEVEL_REACHED;

        // Node is not ACCESSIBLE yet
        if (nodeState != NodeState.UNLOCKED && nodeState != NodeState.UNLOCKABLE)
            return NodeIncrementResult.LOCKED_NODE;

        // Check permission
        if (!node.hasPermissionRequirement(this)) return NodeIncrementResult.PERMISSION_DENIED;

        final int skillTreePoints = this.skillTreePoints.getOrDefault(node.getTree().getId(), 0) + this.skillTreePoints.getOrDefault("global", 0);
        if (skillTreePoints < node.getPointConsumption()) return NodeIncrementResult.NOT_ENOUGH_POINTS;

        return NodeIncrementResult.SUCCESS;
    }

    /**
     * Increments the node level by one, change the states of branches of the tree.
     * Consumes skill tree points from the tree first and then consumes the
     * global skill-tree points ('all')
     */
    public void incrementNodeLevel(@NotNull SkillTreeNode node) {
        final int newLevel = addNodeLevels(node, 1);
        node.updateAdvancement(this, newLevel); // Claim the node exp table

        // Consume skill tree points
        final AtomicInteger cost = new AtomicInteger(node.getPointConsumption());
        skillTreePoints.computeIfPresent(node.getTree().getId(), (key, points) -> {
            final int withdrawn = Math.min(points, cost.get());
            cost.set(cost.get() - withdrawn);
            return points <= withdrawn ? null : points - withdrawn;
        });
        if (cost.get() > 0) withdrawSkillTreePoints("global", cost.get());

        // Reload node/path states from full skill tree
        node.getTree().resolveStates(this);
    }

    public int getSkillTreePoints(@Nullable SkillTree skillTree) {
        return getSkillTreePoints(asKey(skillTree));
    }

    public int getSkillTreePoints(@NotNull String treeId) {
        return skillTreePoints.getOrDefault(treeId, 0);
    }

    public void withdrawSkillTreePoints(@NotNull String treeId, int withdrawn) {
        final int cost = Math.max(0, withdrawn);
        skillTreePoints.computeIfPresent(treeId, (ignored, points) -> cost >= points ? null : points - cost);
    }

    public void setNodeState(@NotNull SkillTreeNode node, @NotNull NodeState nodeState) {
        nodeStates.put(node, nodeState);
    }

    public void setPathState(@NotNull ParentInformation edge, @NotNull PathState pathState) {
        edgeStates.put(edge, pathState);
    }

    public NodeState getNodeState(@NotNull SkillTreeNode node) {
        return nodeStates.get(node);
    }

    public PathState getPathState(@NotNull ParentInformation path) {
        return edgeStates.get(path);
    }

    public boolean hasNodeState(@NotNull SkillTreeNode node) {
        return nodeStates.containsKey(node);
    }

    public int getNodeLevel(@NotNull SkillTreeNode node) {
        return nodeLevels.getOrDefault(node, 0);
    }

    public void setNodeLevel(@NotNull SkillTreeNode node, int nodeLevel) {
        nodeLevels.compute(node, (ignored, currentLevelInteger) -> {
            final int currentLevel = currentLevelInteger == null ? 0 : currentLevelInteger;
            final int delta = (nodeLevel - currentLevel) * node.getPointConsumption();
            skillTreePointsSpent.merge(node.getTree(), delta, (level, ignored2) -> level + delta);
            return nodeLevel;
        });
    }

    public int addNodeLevels(@NotNull SkillTreeNode node, int increment) {
        final int delta = increment * node.getPointConsumption();
        skillTreePointsSpent.merge(node.getTree(), delta, (points, ignored) -> points + delta);
        return nodeLevels.merge(node, increment, (level, ignored) -> level + increment);
    }

    public void resetSkillTree(@NotNull SkillTree skillTree) {
        for (SkillTreeNode node : skillTree.getNodes()) {
            node.resetAdvancement(this, true);
            setNodeLevel(node, 0);
        }
        skillTree.resolveStates(this);
    }

    @NotNull
    public Map<SkillTreeNode, NodeState> getNodeStates() {
        return new HashMap<>(nodeStates);
    }

    public boolean hasNodeStates() {
        return !nodeStates.isEmpty();
    }

    //endregion Skill trees

    @Override
    public Map<String, Integer> getNodeTimesClaimed() {
        Map<String, Integer> result = new HashMap<>();
        tableItemClaims.forEach((str, val) -> {
            if (str.startsWith(SkillTreeNode.KEY_PREFIX)) result.put(str, val);
        });
        return result;
    }

    /**
     * @return If the item is unlocked by the player
     *         This is used for skills that can be locked & unlocked.
     */
    public boolean hasUnlocked(Unlockable unlockable) {
        return unlockable.isUnlockedByDefault() || unlockedItems.contains(unlockable.getUnlockNamespacedKey());
    }

    /**
     * Unlocks an item for the player. This is mainly used to unlock skills.
     *
     * @return If the item was locked when calling this method.
     */
    public boolean unlock(Unlockable unlockable) {
        Validate.isTrue(!unlockable.isUnlockedByDefault(), "Cannot unlock an item unlocked by default");
        final boolean wasLocked = unlockedItems.add(unlockable.getUnlockNamespacedKey());
        if (wasLocked) {
            unlockable.whenUnlocked(this);
            scheduleOnPlayer(() -> Bukkit.getPluginManager().callEvent(new ItemUnlockedEvent(this, unlockable.getUnlockNamespacedKey())));
        }
        return wasLocked;
    }

    /**
     * Locks an item for the player by removing it from the unlocked items map if it is present.
     * This is mainly used to remove unlocked items when changing class or reallocating a skill tree.
     *
     * @return If the item was unlocked when calling this method.
     */
    public boolean lock(Unlockable unlockable) {
        Validate.isTrue(!unlockable.isUnlockedByDefault(), "Cannot lock an item unlocked by default");
        boolean wasUnlocked = unlockedItems.remove(unlockable.getUnlockNamespacedKey());
        if (wasUnlocked) {
            unlockable.whenLocked(this);
            scheduleOnPlayer(() -> Bukkit.getPluginManager().callEvent(new ItemLockedEvent(this, unlockable.getUnlockNamespacedKey())));
        }
        return wasUnlocked;
    }

    public Set<String> getUnlockedItems() {
        return new HashSet<>(unlockedItems);
    }

    public void setUnlockedItems(Set<String> unlockedItems) {
        this.unlockedItems.clear();
        this.unlockedItems.addAll(unlockedItems);
    }

    @Override
    public void onSaved(@NotNull SessionUpdateReason reason) {
        super.onSaved(reason);

        // Saves player health before saveData as the player will be considered offline into it if it is async
        lastHealth = getPlayer().getHealth();

        if (reason == SessionUpdateReason.AUTOSAVE) return;

        // Remove from party if it is MMO Party Module
        if (MMOCore.plugin.partyModule instanceof MMOCorePartyModule) {
            AbstractParty party = getParty();
            if (party != null && party instanceof Party) ((Party) party).removeMember(this);
        }

        // Close combat handler
        combat.close();

        // Close quest data
        questData.close();

        // Close bound skills
        boundSkills.forEach((slot, info) -> info.close());

        // Stop skill casting
        if (isCasting()) leaveSkillCasting(true);
    }

    public List<UUID> getFriends() {
        return friends;
    }

    public PlayerProfessions getCollectionSkills() {
        return collectSkills;
    }

    public PlayerQuests getQuestData() {
        return questData;
    }

    public long getLastActivity(PlayerActivity activity) {
        return this.lastActivity.getOrDefault(activity, 0l);
    }

    public long getActivityTimeOut(PlayerActivity activity) {
        return Math.max(0, getLastActivity(activity) + activity.getTimeOut() - System.currentTimeMillis());
    }

    public void setLastActivity(PlayerActivity activity) {
        setLastActivity(activity, System.currentTimeMillis());
    }

    public void setLastActivity(PlayerActivity activity, long timestamp) {
        this.lastActivity.put(activity, timestamp);
    }

    @Override
    public long getLastLogin() {
        return getMMOPlayerData().getLastLogActivity();
    }

    @Override
    public int getLevel() {
        // Level 0 does not exist
        // Watch out for raw accesses of #level
        return Math.max(1, level);
    }

    @Nullable
    public AbstractParty getParty() {
        return MMOCore.plugin.partyModule.getParty(this);
    }

    public boolean hasGuild() {
        return guild != null;
    }

    public Guild getGuild() {
        return guild;
    }

    public int getClassPoints() {
        return classPoints;
    }

    @Override
    public int getSkillPoints() {
        return skillPoints;
    }

    public void giveSkillReallocationPoints(int value) {
        skillReallocationPoints += value;
    }

    public int countSkillPointsSpent() {
        int sum = 0;
        for (ClassSkill skill : getProfess().getSkills())
            // 0 if the skill is level 1 (just unlocked) or 0 locked
            sum += Math.max(0, getSkillLevel(skill.getSkill()) - 1);

        return sum;
    }

    @NotNull
    public List<ClassSkill> getUnlockedSkills() {
        return getProfess().getSkills().stream()
                .filter(skill -> hasUnlocked(skill) && hasUnlockedLevel(skill))
                .collect(Collectors.toList());
    }

    @Override
    public int getAttributePoints() {
        return attributePoints;
    }

    @Override
    public int getAttributeReallocationPoints() {
        return attributeReallocationPoints;
    }

    public int getClaims(@NotNull ExperienceObject object, @NotNull ExperienceItem item) {
        final ExperienceTable table = object.getExperienceTable();
        return getClaims(object.getKey() + "." + table.getId() + "." + item.getId());
    }

    /**
     * @param key The identifier of an exp table item.
     * @return Amount of times an item has been claimed
     *         inside an experience table.
     */
    public int getClaims(@NotNull String key) {
        return tableItemClaims.getOrDefault(key, 0);
    }

    public void setClaims(@NotNull ExperienceObject object, @NotNull ExperienceItem item, int times) {
        final ExperienceTable table = object.getExperienceTable();
        setClaims(object.getKey() + "." + table.getId() + "." + item.getId(), times);
    }

    public void setClaims(@NotNull String itemKey, int times) {
        if (times <= 0) tableItemClaims.remove(itemKey);
        else tableItemClaims.put(itemKey, times);
    }

    public Map<String, Integer> getItemClaims() {
        return tableItemClaims;
    }

    /**
     * @return Experience needed in order to reach next level
     */
    public long getLevelUpExperience() {
        return getProfess().getExpCurve().getExperience(this, getLevel());
    }

    public boolean isOnline() {
        return getMMOPlayerData().isOnline();
    }

    public boolean inGuild() {
        return guild != null;
    }

    public void setLevel(int level, @NotNull PlayerLevelChangeEvent.Reason reason) {

        // Compute effective new level
        final var oldLevel = getLevel();
        var newLevel = Math.max(1, level);
        if (getProfess().hasMaxLevel()) newLevel = Math.min(getProfess().getMaxLevel(), newLevel);

        if (oldLevel == newLevel) return; // Safeguard when changing class

        this.level = newLevel;

        if (reason != PlayerLevelChangeEvent.Reason.CHOOSE_PROFILE) // No event, data is loaded async
            Bukkit.getPluginManager().callEvent(new PlayerLevelChangeEvent(this, null, oldLevel, newLevel, reason));

        if (getMMOPlayerData().isPlaying()) getStats().updateStats();
        if (isOnline()) refreshVanillaExp();
    }

    public void giveLevels(int value, @NotNull EXPSource source) {
        long equivalentExp = 0;
        while (value-- > 0) equivalentExp += getProfess().getExpCurve().getExperience(this, getLevel() + value);
        giveExperience(equivalentExp, source);
    }

    public void setExperience(double value) {
        experience = Math.max(0, value);

        if (isOnline()) refreshVanillaExp();
    }

    /**
     * Class experience can be displayed on the player's exp bar.
     * This updates the exp bar to display the player class level and exp.
     */
    public void refreshVanillaExp() {
        if (!MMOCore.plugin.configManager.overrideVanillaExp) return;

        getPlayer().sendExperienceChange(0.01f);
        getPlayer().setLevel(getLevel());
        // [Safeguard] Avoid division by zero
        final var maxExp = Math.max(1, getLevelUpExperience());
        getPlayer().setExp(Math.max(0, Math.min(1, (float) experience / (float) maxExp)));
    }

    public void setAttributePoints(int value) {
        attributePoints = Math.max(0, value);
    }

    public void setAttributeReallocationPoints(int value) {
        attributeReallocationPoints = Math.max(0, value);
    }

    public void setSkillReallocationPoints(int value) {
        skillReallocationPoints = Math.max(0, value);
    }

    public int getSkillReallocationPoints() {
        return skillReallocationPoints;
    }

    public void setSkillPoints(int value) {
        skillPoints = Math.max(0, value);
    }

    public void setClassPoints(int value) {
        classPoints = Math.max(0, value);
    }

    public void setSkillTreeReallocationPoints(int value) {
        skillTreeReallocationPoints = Math.max(0, value);
    }

    public boolean hasSavedClass(PlayerClass profess) {
        return classSlots.containsKey(profess.getId());
    }

    public Set<String> getSavedClasses() {
        return classSlots.keySet();
    }

    @Nullable
    public SavedClassInformation getClassInfo(PlayerClass profess) {
        return getClassInfo(profess.getId());
    }

    @Nullable
    public SavedClassInformation getClassInfo(String profess) {
        return classSlots.get(profess);
    }

    /**
     * Saves information of a class
     *
     * @param profess Class
     * @param info    Class player progress
     */
    public void applyClassInfo(PlayerClass profess, SavedClassInformation info) {
        classSlots.put(profess.getId(), info);
    }

    public void unloadClassInfo(PlayerClass profess) {
        unloadClassInfo(profess.getId());
    }

    public void unloadClassInfo(String profess) {
        classSlots.remove(profess);
    }

    public Set<String> getWaypoints() {
        return waypoints;
    }

    public boolean hasWaypoint(Waypoint waypoint) {
        return waypoint.hasOption(WaypointOption.DEFAULT) || waypoints.contains(waypoint.getId());
    }

    public void unlockWaypoint(@NotNull Waypoint waypoint) {
        Message.WAYPOINT_UNLOCK.send(this, "waypoint", waypoint.getName());
        waypoints.add(waypoint.getId());
    }

    public void lockWaypoint(Waypoint waypoint) {
        waypoints.remove(waypoint.getId());
    }

    //region Resources

    public boolean setHealth(double amount, @NotNull ResourceUpdateReason reason) {
        return Resources.setHealth(getPlayer(), amount, reason);
    }

    public boolean setMana(double amount, @NotNull ResourceUpdateReason reason) {

        final var maxValue = getStats().getStat("MAX_MANA");
        var newValue = Math.max(0, Math.min(amount, maxValue));
        if (mana == newValue) return false;

        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            final var called = new PlayerResourceUpdateEvent(this, PlayerResource.MANA, this.mana, newValue, reason);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled()) return false;
            newValue = called.getNewAmount();
        }

        mana = Math.max(0, Math.min(newValue, maxValue));
        return true;
    }

    public boolean setStamina(double amount, @NotNull ResourceUpdateReason reason) {

        final var maxValue = getStats().getStat("MAX_STAMINA");
        var newValue = Math.max(0, Math.min(amount, maxValue));
        if (stamina == newValue) return false;

        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            final var called = new PlayerResourceUpdateEvent(this, PlayerResource.STAMINA, this.stamina, newValue, reason);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled()) return false;
            newValue = called.getNewAmount();
        }

        stamina = Math.max(0, Math.min(newValue, maxValue));
        return true;
    }

    public boolean setStellium(double amount, @NotNull ResourceUpdateReason reason) {

        final var maxValue = getStats().getStat("MAX_STELLIUM");
        var newValue = Math.max(0, Math.min(amount, maxValue));
        if (stellium == newValue) return false;

        if (reason != ResourceUpdateReason.CHOOSE_CLASS) {
            final var called = new PlayerResourceUpdateEvent(this, PlayerResource.STELLIUM, this.stellium, newValue, reason);
            Bukkit.getPluginManager().callEvent(called);
            if (called.isCancelled()) return false;
            newValue = called.getNewAmount();
        }

        stellium = Math.max(0, Math.min(newValue, maxValue));
        return true;
    }

    public boolean giveHealth(double amount, @NotNull ResourceUpdateReason reason) {
        return Resources.heal(getPlayer(), amount, reason);
    }

    public boolean giveMana(double amount, @NotNull ResourceUpdateReason reason) {
        return setMana(mana + amount, reason);
    }

    public boolean giveStamina(double amount, @NotNull ResourceUpdateReason reason) {
        return setStamina(stamina + amount, reason);
    }

    public boolean giveStellium(double amount, @NotNull ResourceUpdateReason reason) {
        return setStellium(stellium + amount, reason);
    }

    //endregion

    public void addFriend(UUID uuid) {
        friends.add(uuid);
    }

    @Override
    public void removeFriend(UUID uuid) {
        friends.remove(uuid);
    }

    @Override
    public boolean hasFriend(UUID uuid) {
        return friends.contains(uuid);
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }

    public void log(Level level, String message) {
        MMOCore.plugin.getLogger().log(level, "[Userdata:" + getMMOPlayerData().getPlayerName() + "] " + message);
    }

    public void sendFriendRequest(PlayerData target) {
        if (!isOnline() || !target.isOnline()) return;

        setLastActivity(PlayerActivity.FRIEND_REQUEST);
        FriendRequest request = new FriendRequest(this, target);
        MMOCore.plugin.requestManager.registerRequest(request);

        Message.FRIEND_REQUEST.send(target.getPlayer(), "player", getPlayer().getName(), "uuid", request.getUniqueId().toString());
    }

    /**
     * Teleports the player to a specific waypoint. This applies
     * the stellium waypoint cost and plays the teleport animation.
     *
     * @param target Target waypoint
     */
    public void warp(Waypoint target, double cost) {

        /*
         * This cooldown is only used internally to make sure the player is not
         * spamming waypoints. There is no need to reset it when resetting the
         * player waypoints data
         */
        setLastActivity(PlayerActivity.USE_WAYPOINT);
        giveStellium(-cost, ResourceUpdateReason.WAYPOINT);

        final Player player = getPlayer();
        final int x = player.getLocation().getBlockX();
        final int y = player.getLocation().getBlockY();
        final int z = player.getLocation().getBlockZ();
        final int warpTime = target.getWarpTime();
        final boolean hasPerm = player.hasPermission("mmocore.bypass-waypoint-wait");
        final int[] ticks = {0};

        Tasks.runEntityTimer(player, scheduled -> {
            if (!isOnline() || player.getLocation().getBlockX() != x || player.getLocation().getBlockY() != y || player.getLocation().getBlockZ() != z) {
                if (isOnline()) Message.WAYPOINT_TP_CANCEL.send(player);
                giveStellium(cost, ResourceUpdateReason.WAYPOINT);
                scheduled.cancel();
                return;
            }

            if (hasPerm || ticks[0]++ >= warpTime) {
                player.teleportAsync(target.getLocation()).thenAccept(success -> {
                    if (!Boolean.TRUE.equals(success) || !isOnline()) return;
                    Tasks.executeEntity(player, () -> {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 1, false, false));
                        Message.WAYPOINT_TP_DONE.send(player, "waypoint", target.getName());
                    });
                });
                scheduled.cancel();
                return;
            }

            Message.WAYPOINT_TP_CHARGE.send(player, "left", String.valueOf((warpTime - ticks[0]) / 20));
            final double r = Math.sin((double) ticks[0] / warpTime * Math.PI);
            for (double j = 0; j < Math.PI * 2; j += Math.PI / 4)
                player.getWorld().spawnParticle(VParticle.REDSTONE.get(), player.getLocation().add(Math.cos((double) 5 * ticks[0] / warpTime + j) * r, (double) 2 * ticks[0] / warpTime, Math.sin((double) 5 * ticks[0] / warpTime + j) * r), 1, new Particle.DustOptions(Color.PURPLE, 1.25f));
        }, 1, 1);
    }

    private void scheduleOnPlayer(Runnable runnable) {
        if (isOnline()) Tasks.runEntity(getPlayer(), runnable);
        else Tasks.runGlobal(runnable);
    }

    public boolean hasReachedMaxLevel() {
        return getProfess().getMaxLevel() > 0 && getLevel() >= getProfess().getMaxLevel();
    }

    /**
     * Gives experience without displaying an EXP hologram around the player
     *
     * @param value  Experience to give the player
     * @param source How the player earned experience
     */
    public void giveExperience(double value, @NotNull EXPSource source) {
        giveExperience(value, source, null, true);
    }

    /**
     * Called when giving experience to a player
     *
     * @param value            Experience to give the player
     * @param source           How the player earned experience
     * @param hologramLocation Location used to display the hologram.
     *                         If it's null, no hologram will be displayed
     * @param splitExp         Should the exp be split among party members
     */
    public void giveExperience(double value, @NotNull EXPSource source, @Nullable Location hologramLocation, boolean splitExp) {

        // Take exp on negative amounts
        if (value <= 0) {
            setExperience(experience + value);
            return;
        }

        // Splitting exp through party members
        final AbstractParty party;
        if (splitExp && (party = getParty()) != null && MMOCore.plugin.configManager.splitMainExp) {
            var nearbyMembers = party.findPlayersForExp(this);
            value /= (nearbyMembers.size() + 1); // Share exp
            for (PlayerData member : nearbyMembers)
                member.giveExperience(value, source, null, false);
        }

        // Must be placed after exp splitting
        if (hasReachedMaxLevel()) {
            setExperience(0);
            return;
        }

        // Apply buffs AFTER splitting exp
        value *= (1 + getStats().getStat("ADDITIONAL_EXPERIENCE") / 100) * MMOCore.plugin.boosterManager.getMultiplier(null);

        final var event = new PlayerExperienceGainEvent(this, value, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        // Experience hologram
        if (hologramLocation != null)
            MMOCoreUtils.displayIndicator(hologramLocation, Language.EXP_HOLOGRAM.getFormat().replace("{exp}", MythicLib.plugin.getMMOConfig().decimal.format(event.getExperience())));

        experience = Math.max(0, experience + event.getExperience());

        // Calculate the player's next level
        final var maxLevel = getProfess().getMaxLevel();
        final var oldLevel = getLevel();
        var newLevel = oldLevel;
        long experienceNeeded;

        /*
         * Loop for exp overload when leveling up, will continue
         * looping until exp is 0 or max currentLevel has been reached
         */
        while (experience >= (experienceNeeded = getProfess().getExpCurve().getExperience(this, newLevel))) {

            if (maxLevel > 0 && newLevel >= maxLevel) {
                experience = 0;
                break;
            }

            experience -= experienceNeeded;
            newLevel++;

            // Apply class experience table
            getProfess().updateAdvancement(this, newLevel);
        }

        if (newLevel > oldLevel) {
            setLevel(newLevel, PlayerLevelChangeEvent.Reason.LEVEL_UP); // Update 'level'
            Message.LEVEL_UP.send(this, "level", getLevel());
        }

        refreshVanillaExp();
    }

    public double getExperience() {
        return experience;
    }

    @Override
    @NotNull
    public PlayerClass getProfess() {
        return profess == null ? MMOCore.plugin.classManager.getDefaultClass() : profess;
    }

    @Override
    public double getLastHealth() {
        return lastHealth;
    }

    @Override
    public double getMana() {
        return mana;
    }

    public double getStamina() {
        return stamina;
    }

    @Override
    public double getStellium() {
        return stellium;
    }

    public PlayerStats getStats() {
        return playerStats;
    }

    public PlayerAttributes getAttributes() {
        return attributes;
    }

    public void loadResources(double lastHealth, double lastMana, double lastStamina, double lastStellium) {

        // Player started playing, update resources now
        if (isSessionReady()) {
            UtilityMethods.setHealth(getPlayer(), lastHealth);
            setMana(lastMana, ResourceUpdateReason.CHOOSE_CLASS);
            setStamina(lastStamina, ResourceUpdateReason.CHOOSE_CLASS);
            setStellium(lastStellium, ResourceUpdateReason.CHOOSE_CLASS);
        }

        // Cache and load later
        else {
            this.lastHealth = lastHealth;
            this.lastMana = lastMana;
            this.lastStamina = lastStamina;
            this.lastStellium = lastStellium;
        }
    }

    public boolean isCasting() {
        return skillCasting != null;
    }

    /**
     * @return If the PlayerEnterCastingModeEvent successfully put the player
     *         into casting mode, otherwise if the event is cancelled, returns false.
     */
    public boolean setSkillCasting() {
        Validate.isTrue(!isCasting(), "Player already in casting mode");
        PlayerEnterCastingModeEvent event = new PlayerEnterCastingModeEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        this.skillCasting = SkillCastingMode.getInstance().newInstance(this);
        return true;
    }

    @NotNull
    public SkillCastingInstance getSkillCasting() {
        return Objects.requireNonNull(skillCasting, "Player not in casting mode");
    }

    /**
     * @return If player successfully left skill casting i.e the Bukkit
     *         event has not been cancelled
     */
    public boolean leaveSkillCasting() {
        return leaveSkillCasting(false);
    }

    /**
     * @param skipEvent Skip firing the exit event
     * @return If player successfully left skill casting i.e the Bukkit
     *         event has not been canceled
     */
    public boolean leaveSkillCasting(boolean skipEvent) {
        // The player may have already left casting mode, for instance when this
        // is called while the casting task is being cancelled from another thread.
        if (!isCasting()) return false;

        if (!skipEvent) {
            PlayerExitCastingModeEvent event = new PlayerExitCastingModeEvent(this);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
        }

        skillCasting.close();
        this.skillCasting = null;
        return true;
    }

    @Override
    public Map<String, Integer> mapAttributeLevels() {
        return getAttributes().mapPoints();
    }

    public int getSkillLevel(@NotNull SkillHandler<?> skill) {
        return skills.getOrDefault(skill.getId(), 1);
    }

    public void setSkillLevel(@NotNull SkillHandler<?> skill, int level) {
        setSkillLevel(skill.getId(), level);
    }

    public void setSkillLevel(@NotNull String skill, int level) {
        if (level <= 1) skills.remove(skill);
        else skills.put(skill, level);
    }

    public void resetSkills() {
        skills.clear();
        while (hasSkillBound(0)) unbindSkill(0);
    }

    public void resetSkillLevel(@NotNull String skill) {
        skills.remove(skill);
    }

    /**
     * Checks for the player's level and compares it to the skill unlock level.
     * <p>
     * Any skill, when the player has the right level is instantly
     * unlocked, therefore one must NOT check if the player has unlocked the
     * skill by checking if the skills map contains the skill id as key. This
     * only checks if the player has spent any skill point.
     *
     * @return If the player unlocked the skill
     */
    public boolean hasUnlockedLevel(ClassSkill skill) {
        return getLevel() >= skill.getUnlockLevel();
    }

    public Map<String, Integer> mapSkillLevels() {
        return new HashMap<>(skills);
    }

    public void giveClassPoints(int value) {
        setClassPoints(classPoints + value);
    }

    public void giveSkillPoints(int value) {
        setSkillPoints(skillPoints + value);
    }

    public void giveAttributePoints(int value) {
        setAttributePoints(attributePoints + value);
    }

    // public void giveSkillReallocationPoints(int value) {
    // setSkillReallocationPoints(skillReallocationPoints + value);
    // }

    public void giveAttributeReallocationPoints(int value) {
        setAttributeReallocationPoints(attributeReallocationPoints + value);
    }

    public void giveSkillTreeReallocationPoints(int value) {
        setSkillTreeReallocationPoints(skillTreeReallocationPoints + value);
    }

    public CooldownMap getCooldownMap() {
        return getMMOPlayerData().getCooldownMap();
    }

    public void setClass(@Nullable PlayerClass profess) {
        this.profess = profess;

        // Clear bound skills
        boundSkills.forEach((slot, info) -> info.close());
        boundSkills.clear();

        // Apply temporary triggers (class exp-table triggers)
        applyTemporaryTriggers();

        // Hardbind skills
        for (var skillSlot : getProfess().getSlots())
            if (skillSlot.getHardBind() != null) this.bindSkill(skillSlot.getSlot(), skillSlot.getHardBind());

        // Update stats
        if (isOnline()) getStats().updateStats();
    }

    public boolean hasSkillBound(int slot) {
        return boundSkills.containsKey(slot);
    }

    @Nullable
    public ClassSkill getBoundSkill(int slot) {
        final BoundSkillInfo found = boundSkills.get(slot);
        return found != null ? found.getClassSkill() : null;
    }

    /**
     * Binds a skill to the player.
     *
     * @param slot  Slot to which you're binding the skill
     * @param skill Skill being bound
     */
    public void bindSkill(int slot, @NotNull ClassSkill skill) {
        Validate.notNull(skill, "Skill cannot be null");
        if (slot <= 0) return; // TODO why is this not a Validate?

        // Friendly error in case server owner makes a skill permanent while players have already bound it
        if (skill.isPermanent()) {
            MMOCore.plugin.getLogger().log(Level.WARNING, "Attempted to bind permanent skill " + skill.getSkill().getName() + " to player " + getUniqueId());
            return;
        }

        // Unbinds the previous skill (important for passive skills)
        unbindSkill(slot);
        final SkillSlot skillSlot = getProfess().getSkillSlot(slot);
        boundSkills.put(slot, new BoundSkillInfo(skillSlot, skill, this));
        SkillCastingMode.getInstance().onSkillBindChange(this);
    }

    @Nullable
    public BoundSkillInfo unbindSkill(int slot) {
        final @Nullable BoundSkillInfo boundSkillInfo = boundSkills.remove(slot);
        if (boundSkillInfo != null) {
            boundSkillInfo.close();
            SkillCastingMode.getInstance().onSkillBindChange(this);
        }
        return boundSkillInfo;
    }

    @NotNull
    public Map<Integer, BoundSkillInfo> getBoundSkills() {
        return boundSkills;
    }

    /**
     * @return If the player has at least one active skill bound
     */
    public boolean hasActiveSkillBound() {
        for (var bound : boundSkills.values())
            if (!bound.isPassive()) return true;
        return false;
    }

    @NotNull
    public CombatHandler getCombat() {
        return combat;
    }

    public boolean isInCombat() {
        return getCombat().isInCombat();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerData that = (PlayerData) o;
        return getUniqueId().equals(that.getUniqueId());
    }

    @Override
    public int hashCode() {
        return getMMOPlayerData().hashCode();
    }

    //region Static methods

    public static PlayerData get(@NotNull MMOPlayerData playerData) {
        return get(playerData.getPlayer());
    }

    public static PlayerData get(@NotNull OfflinePlayer player) {
        return get(player.getUniqueId());
    }

    public static PlayerData get(@NotNull UUID uuid) {
        return MMOCore.plugin.playerDataManager.get(uuid);
    }

    /**
     * This is used to check if the player data is loaded for a
     * specific player. This might seem redundant because the given
     * Player instance is linked to an online player, and data
     * is always loaded for an online player.
     * <p>
     * In fact a Player instance can be attached to a Citizens NPC
     * which has no player data loaded hence this method
     *
     * @param player Either a real player or an NPC
     * @return If player data for that player is loaded
     */
    public static boolean has(Player player) {
        return has(player.getUniqueId());
    }

    /**
     * This is used to check if the player data is loaded for a
     * specific player. This might seem redundant because the given
     * Player instance is linked to an online player, and data
     * is always loaded for an online player.
     * <p>
     * In fact a Player instance can be attached to a Citizens NPC
     * which has no player data loaded hence this method
     *
     * @param uuid A (real or fictive) player UUID
     * @return If player data for that player is loaded
     */
    public static boolean has(UUID uuid) {
        return MMOCore.plugin.playerDataManager.isLoaded(uuid);
    }

    public static Collection<PlayerData> getAll() {
        return MMOCore.plugin.playerDataManager.getLoaded();
    }

    //endregion

    //region Deprecated

    @Deprecated
    public void setHealth(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.setHealth(amount, reason.adapt());
    }

    @Deprecated
    public void setMana(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.setMana(amount, reason.adapt());
    }

    @Deprecated
    public void setStamina(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.setStamina(amount, reason.adapt());
    }

    @Deprecated
    public void setStellium(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.setStellium(amount, reason.adapt());
    }

    @Deprecated
    public void heal(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.giveHealth(amount, reason.adapt());
    }

    @Deprecated
    public void giveMana(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.giveMana(amount, reason.adapt());
    }

    @Deprecated
    public void giveStamina(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.giveStamina(amount, reason.adapt());
    }

    @Deprecated
    public void giveStellium(double amount, @NotNull PlayerResourceUpdateEvent.UpdateReason reason) {
        this.giveStellium(amount, reason.adapt());
    }

    @Deprecated
    public void setSkillLevel(@NotNull RegisteredSkill skill, int level) {
        setSkillLevel(skill.getHandler().getId(), level);
    }

    @Deprecated
    public int getSkillLevel(@NotNull RegisteredSkill skill) {
        return skills.getOrDefault(skill.getHandler().getId(), 1);
    }

    @Deprecated
    public void takeLevels(int value) {
        setLevel(getLevel() - value, PlayerLevelChangeEvent.Reason.UNKNOWN);
    }

    @Deprecated
    public void setLevel(int level) {
        setLevel(level, PlayerLevelChangeEvent.Reason.UNKNOWN);
    }

    @Deprecated
    public void clearNodeStates(@NotNull SkillTree skillTree) {
        clearStates(skillTree);
    }

    /**
     * @see #setMana(double, ResourceUpdateReason)
     * @deprecated
     */
    @Deprecated
    public void setMana(double amount) {
        setMana(amount, ResourceUpdateReason.OTHER);
    }

    /**
     * @see #setStamina(double, ResourceUpdateReason)
     * @deprecated
     */
    @Deprecated
    public void setStamina(double amount) {
        setStamina(amount, ResourceUpdateReason.OTHER);
    }

    /**
     * @see #setStellium(double, ResourceUpdateReason)
     * @deprecated
     */
    @Deprecated
    public void setStellium(double amount) {
        setStellium(amount, ResourceUpdateReason.OTHER);
    }

    /**
     * @see #loadResources(double, double, double, double)
     * @deprecated
     */
    @Deprecated
    public void setHealth(double amount) {
        this.lastHealth = amount;
    }

    @Deprecated
    public double getHealth() {
        return getLastHealth();
    }

    @Deprecated
    public double getCachedHealth() {
        return getLastHealth();
    }

    /**
     * @deprecated Provide reason with {@link #giveStellium(double, ResourceUpdateReason)}
     */
    @Deprecated
    public void giveStellium(double amount) {
        giveStellium(amount, ResourceUpdateReason.OTHER);
    }

    /**
     * @deprecated Provide reason with {@link #giveStamina(double, ResourceUpdateReason)}
     */
    @Deprecated
    public void giveStamina(double amount) {
        giveStamina(amount, ResourceUpdateReason.OTHER);
    }

    /**
     * @deprecated Provide reason with {@link #giveMana(double, ResourceUpdateReason)}
     */
    @Deprecated
    public void giveMana(double amount) {
        giveMana(amount, ResourceUpdateReason.OTHER);
    }

    @Deprecated
    public void setupRemovableTrigger() {
        applyTemporaryTriggers();
    }

    @Deprecated
    public int getPointSpent(SkillTree skillTree) {
        return getPointsSpent(skillTree);
    }

    @Deprecated
    public int getSkillTreePoint(String treeId) {
        return getSkillTreePoints(treeId);
    }

    @Deprecated
    public void clearSkillTrees() {
        resetSkillTrees();
    }

    @Deprecated
    public void resetTimesClaimed() {
        tableItemClaims.clear();
    }

    @Deprecated
    public Set<Map.Entry<String, Integer>> getNodeLevelsEntrySet() {
        HashMap<String, Integer> nodeLevelsString = new HashMap<>();
        for (SkillTreeNode node : nodeLevels.keySet())
            nodeLevelsString.put(node.getFullId(), nodeLevels.get(node));
        return nodeLevelsString.entrySet();
    }

    @Deprecated
    public void setClaims(ExperienceObject object, ExperienceTable table, ExperienceItem item, int times) {
        setClaims(object.getKey() + "." + table.getId() + "." + item.getId(), times);
    }

    @Deprecated
    public int getClaims(ExperienceObject object, ExperienceTable table, ExperienceItem item) {
        return getClaims(object.getKey() + "." + table.getId() + "." + item.getId());
    }

    /**
     * @see #heal(double, PlayerResourceUpdateEvent.UpdateReason)
     * @deprecated
     */
    @Deprecated
    public void heal(double heal) {
        this.giveHealth(heal, ResourceUpdateReason.OTHER);
    }

    @Deprecated
    public NodeState getNodeStatus(SkillTreeNode node) {
        return getNodeState(node);
    }

    @Deprecated
    public boolean setSkillCasting(@NotNull SkillCastingInstance skillCasting) {
        return setSkillCasting();
    }

    @Deprecated
    public boolean isFullyLoaded() {
        return isSessionReady();
    }

    @Deprecated
    public void setFullyLoaded() {
        this.markSessionReady();
    }

    @Deprecated
    public void setBoundSkill(int slot, ClassSkill skill) {
        bindSkill(slot, skill);
    }

    /**
     * Loops through all the subclasses available to the player and
     * checks if they could potentially upgrade to one of these
     *
     * @return If the player can change its current class to
     *         a subclass
     */
    @Deprecated
    public boolean canChooseSubclass() {
        for (Subclass subclass : getProfess().getSubclasses())
            if (getLevel() >= subclass.getLevel()) return true;
        return false;
    }

    /**
     * @see #hasUnlockedLevel(ClassSkill)
     * @deprecated
     */
    @Deprecated
    public boolean hasSkillUnlocked(RegisteredSkill skill) {
        return getProfess().hasSkill(skill.getHandler().getId()) && hasSkillUnlocked(getProfess().getSkill(skill.getHandler().getId()));
    }

    /**
     * @see #hasUnlockedLevel(ClassSkill)
     * @deprecated
     */
    @Deprecated
    public boolean hasSkillUnlocked(ClassSkill skill) {
        return hasUnlockedLevel(skill);
    }

    /**
     * Everytime a player does a combat action, like taking
     * or dealing damage to an entity, this method is called.
     *
     * @see #getCombat()
     * @deprecated
     */
    @Deprecated
    public void updateCombat() {
        getCombat().update();
    }

    @Deprecated
    public void displayActionBar(@NotNull String message) {
        getMMOPlayerData().getActionBar().show(message);
    }

    @Deprecated
    public void displayActionBar(@NotNull String message, boolean raw) {

        // TODO add an option to disable action-bar properly in all casting modes
        if (ChatColor.stripColor(message).isEmpty()) return;

        // TODO move raw/not raw decision to MythicLib
        var handler = getMMOPlayerData().getActionBar();
        if (!raw) handler.show(message);
        else if (handler.show(null)) MythicLib.plugin.getVersion().getWrapper().sendActionBarRaw(getPlayer(), message);
    }

    /**
     * @see #getAttributes()
     * @deprecated
     */
    @Deprecated
    public void setAttribute(PlayerAttribute attribute, int value) {
        setAttribute(attribute.getId(), value);
    }

    /**
     * @see #getAttributes()
     * @deprecated
     */
    @Deprecated
    public void setAttribute(String id, int value) {
        attributes.getInstance(id).setBase(value);
    }

    /**
     * Counts the number of points spent in that skill tree
     *
     * @see #getPointsSpent(SkillTree)
     * @deprecated
     */
    @Deprecated
    public int countSkillTreePoints(@NotNull SkillTree skillTree) {
        return nodeLevels.keySet().stream().filter(node -> node.getTree().equals(skillTree)).mapToInt(node -> nodeLevels.get(node) * node.getPointConsumption()).sum();
    }

    //endregion
}
