package net.Indyuce.mmocore;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.command.BuiltinCommand;
import io.lumine.mythic.lib.metrics.bukkit.Metrics;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.player.modifier.PlayerModifier;
import io.lumine.mythic.lib.util.lang3.Validate;
import io.lumine.mythic.lib.version.SpigotPlugin;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.AttributeModifier;
import net.Indyuce.mmocore.api.player.profess.resource.PlayerResource;
import net.Indyuce.mmocore.command.MMOCoreCommands;
import net.Indyuce.mmocore.comp.citizens.CitizenInteractEventListener;
import net.Indyuce.mmocore.comp.citizens.CitizensMMOLoader;
import net.Indyuce.mmocore.comp.mythicmobs.MythicHook;
import net.Indyuce.mmocore.comp.mythicmobs.MythicMobsMMOLoader;
import net.Indyuce.mmocore.comp.placeholder.MMOCorePlaceholderExpansion;
import net.Indyuce.mmocore.comp.placeholder.ClassPlaceholders;
import net.Indyuce.mmocore.comp.region.DefaultRegionHandler;
import net.Indyuce.mmocore.comp.region.RegionHandler;
import net.Indyuce.mmocore.comp.region.WorldGuardMMOLoader;
import net.Indyuce.mmocore.comp.region.WorldGuardRegionHandler;
import net.Indyuce.mmocore.comp.region.pvpmode.PvPModeListener;
import net.Indyuce.mmocore.comp.vault.VaultEconomy;
import net.Indyuce.mmocore.comp.vault.VaultMMOLoader;
import net.Indyuce.mmocore.guild.GuildModule;
import net.Indyuce.mmocore.guild.GuildModuleType;
import net.Indyuce.mmocore.guild.GuildRelationHandler;
import net.Indyuce.mmocore.guild.provided.Guild;
import net.Indyuce.mmocore.guild.provided.MMOCoreGuildModule;
import net.Indyuce.mmocore.guild.provided.YAMLGuildDataManager;
import net.Indyuce.mmocore.manager.*;
import net.Indyuce.mmocore.manager.data.DataProvider;
import net.Indyuce.mmocore.manager.data.GuildDataManager;
import net.Indyuce.mmocore.manager.data.LegacyDataProvider;
import net.Indyuce.mmocore.manager.data.PlayerDataManager;
import net.Indyuce.mmocore.manager.data.sql.SQLDatabaseImpl;
import net.Indyuce.mmocore.manager.data.yaml.YAMLDatabaseImpl;
import net.Indyuce.mmocore.manager.profession.*;
import net.Indyuce.mmocore.manager.social.BoosterManager;
import net.Indyuce.mmocore.manager.social.PartyManager;
import net.Indyuce.mmocore.manager.social.RequestManager;
import net.Indyuce.mmocore.party.PartyModule;
import net.Indyuce.mmocore.party.PartyModuleType;
import net.Indyuce.mmocore.party.PartyRelationHandler;
import net.Indyuce.mmocore.party.provided.MMOCorePartyModule;
import net.Indyuce.mmocore.player.MaxResourceStatUpdateListener;
import net.Indyuce.mmocore.player.ResourceRegenRunnable;
import net.Indyuce.mmocore.quest.QuestModule;
import net.Indyuce.mmocore.quest.QuestModuleType;
import net.Indyuce.mmocore.quest.provided.MMOCoreQuestModule;
import net.Indyuce.mmocore.script.mechanic.*;
import net.Indyuce.mmocore.skill.list.Ambers;
import net.Indyuce.mmocore.skill.list.Neptune_Gift;
import net.Indyuce.mmocore.skill.list.Sneaky_Picky;
import net.Indyuce.mmocore.skill.trigger.MMOCoreTriggerType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.logging.Level;

public class MMOCore extends MMOPlugin {
    public static MMOCore plugin;
    public final WaypointManager waypointManager = new WaypointManager();
    public final RequestManager requestManager = new RequestManager();
    public final ConfigItemManager configItems = new ConfigItemManager();
    public final ActionBarManager actionBarManager = new ActionBarManager();
    @Deprecated
    public final SkillManager skillManager = new SkillManager();
    public final ClassManager classManager = new ClassManager();
    public final DropTableManager dropTableManager = new DropTableManager();
    public final BoosterManager boosterManager = new BoosterManager();
    public final AttributeManager attributeManager = new AttributeManager(this);
    public final PartyManager partyManager = new PartyManager();
    public final QuestManager questManager = new QuestManager();
    public final ProfessionManager professionManager = new ProfessionManager();
    public final ExperienceManager experience = new ExperienceManager();
    public final LootChestManager lootChests = new LootChestManager();
    public final MMOLoadManager loadManager = new MMOLoadManager();
    public final RestrictionManager restrictionManager = new RestrictionManager();
    public final SkillTreeManager skillTreeManager = new SkillTreeManager();
    public final StatManager statManager = new StatManager();
    public final GuildDataManager nativeGuildManager = new YAMLGuildDataManager();
    public final PlayerDataManager playerDataManager = new PlayerDataManager(this);
    public final ClassPlaceholders classPlaceholders = new ClassPlaceholders();
    @Deprecated
    public final DataProvider dataProvider = new LegacyDataProvider();

    // Profession managers
    public final CustomBlockManager mineManager = new CustomBlockManager();
    public final FishingManager fishingManager = new FishingManager();
    public final AlchemyManager alchemyManager = new AlchemyManager();
    public final EnchantManager enchantManager = new EnchantManager();
    public final SmithingManager smithingManager = new SmithingManager();

    @NotNull
    public ConfigManager configManager;
    public VaultEconomy economy;
    public RegionHandler regionHandler = new DefaultRegionHandler();

    // Modules
    @NotNull
    public PartyModule partyModule;
    @NotNull
    public GuildModule guildModule;
    @NotNull
    public QuestModule questModule;

    public MMOCore() {
        plugin = this;
    }

    @Override
    public void onLoad() {
        getLogger().log(Level.INFO, "Plugin file is called '" + getFile().getName() + "'");

        // Register MMOCore-specific objects
        MythicLib.plugin.getEntities().registerRelationHandler(new PartyRelationHandler());
        MythicLib.plugin.getEntities().registerRelationHandler(new GuildRelationHandler());
        PlayerModifier.registerPlayerModifierType("attribute", AttributeModifier::new);
        MMOCoreTriggerType.registerAll();

        // Register MMOCore builtin skills
        MythicLib.plugin.getSkills().registerBuiltinSkillHandlerType(Ambers.class);
        MythicLib.plugin.getSkills().registerBuiltinSkillHandlerType(Neptune_Gift.class);
        MythicLib.plugin.getSkills().registerBuiltinSkillHandlerType(Sneaky_Picky.class);

        // Custom scripts
        MythicLib.plugin.getSkills().registerMechanic("mana", ManaMechanic::new);
        MythicLib.plugin.getSkills().registerMechanic("stamina", StaminaMechanic::new);
        MythicLib.plugin.getSkills().registerMechanic("stellium", StelliumMechanic::new);
        MythicLib.plugin.getSkills().registerMechanic("mmocore_experience", ExperienceMechanic::new);
        MythicLib.plugin.getSkills().registerMechanic("cast_mmocore_skill", CastSkillMechanic::new, "mmocore_skill", "cast_skill");

        // Register extra objective, drop items...
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null)
            loadManager.registerLoader(new WorldGuardMMOLoader());

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null)
            loadManager.registerLoader(new CitizensMMOLoader());

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) loadManager.registerLoader(new VaultMMOLoader());

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null)
            loadManager.registerLoader(new MythicMobsMMOLoader());
    }

    @Override
    public void onEnable() {
        new SpigotPlugin(70575, this).checkForUpdate();
        new Metrics(this);
        saveDefaultConfig();

        playerDataManager.setupDatabase(SQLDatabaseImpl::new, YAMLDatabaseImpl::new);

        if (getConfig().isConfigurationSection("default-playerdata"))
            playerDataManager.loadDefaultData(getConfig().getConfigurationSection("default-playerdata"));

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) economy = new VaultEconomy();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MMOCorePlaceholderExpansion(this).register();
            getLogger().log(Level.INFO, "Hooked onto PlaceholderAPI");
        }

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            Bukkit.getPluginManager().registerEvents(new CitizenInteractEventListener(), this);
            getLogger().log(Level.INFO, "Hooked onto Citizens");
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            regionHandler = new WorldGuardRegionHandler();
            if (getConfig().getBoolean("pvp_mode.enabled"))
                Bukkit.getPluginManager().registerEvents(new PvPModeListener(), this);
            getLogger().log(Level.INFO, "Hooked onto WorldGuard");
        }

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            Bukkit.getServer().getPluginManager().registerEvents(new MythicHook(), this);
            MMOCore.plugin.getLogger().log(Level.INFO, "Hooked onto MythicMobs");
        }

        // Resource regen task
        new ResourceRegenRunnable().schedule();

        // Clamp resource on max-resource stat changes
        // Fixes https://gitlab.com/phoenix-dvpmt/mythiclib/-/issues/311
        for (var playerResource : PlayerResource.values())
            MythicLib.plugin.getStats().computeStat(playerResource.getMaxStat()).addUpdateListener(new MaxResourceStatUpdateListener(playerResource));

        /*
         * For the sake of the lord, make sure they aren't using MMOItems Mana and
         * Stamina Addon...This should prevent a couple error reports produced by people
         * not reading the installation guide...
         */
        if (Bukkit.getPluginManager().getPlugin("MMOMana") != null) {
            getLogger().log(Level.SEVERE, ChatColor.DARK_RED + "MMOCore is not meant to be used with MMOItems ManaAndStamina");
            getLogger().log(Level.SEVERE, ChatColor.DARK_RED + "Please read the installation guide!");
            return;
        }

        initializePlugin(false);

        // Load party module
        try {
            var moduleType = UtilityMethods.prettyValueOf(PartyModuleType::valueOf, getConfig().getString("party-plugin"), "No party module named '%s'");
            Validate.isTrue(moduleType.isValid(), "Plugin " + moduleType.getPluginName() + " is not installed");
            partyModule = moduleType.provideModule();
            getLogger().log(Level.INFO, "Hooked parties onto " + moduleType.getPluginName());
        } catch (Throwable exception) {
            getLogger().log(Level.WARNING, "Could not initialize party module: " + exception.getMessage());
            partyModule = new MMOCorePartyModule();
        }

        // Load quest module
        try {
            var moduleType = UtilityMethods.prettyValueOf(QuestModuleType::valueOf, getConfig().getString("quest-plugin"), "No quest module named '%s'");
            Validate.isTrue(moduleType.isValid(), "Plugin " + moduleType.getPluginName() + " is not installed");
            questModule = moduleType.provideModule();
            getLogger().log(Level.INFO, "Hooked quests onto " + moduleType.getPluginName());
        } catch (Throwable exception) {
            exception.printStackTrace();
            getLogger().log(Level.WARNING, "Could not initialize quest module: " + exception.getMessage());
            questModule = new MMOCoreQuestModule();
        }

        // Load guild module
        try {
            GuildModuleType moduleType = UtilityMethods.prettyValueOf(GuildModuleType::valueOf, getConfig().getString("guild-plugin"), "No guild module named '%s'");
            Validate.isTrue(moduleType.isValid(), "Plugin '" + moduleType.name() + "' is not installed");
            guildModule = moduleType.provideModule();
            getLogger().log(Level.INFO, "Hooked guilds onto " + moduleType.getPluginName());
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Could not initialize guild module: " + exception.getMessage());
            guildModule = new MMOCoreGuildModule();
        }

        // Load MMOCore-Bukkit module
        UtilityMethods.bukkitBootstrap(this, "net.Indyuce.mmocore.MMOCoreBukkit");

        /*
         * Initialize player data from all online players. This is very important to do
         * that after registering all the professses otherwise the player datas can't
         * recognize what profess the player has and professes will be lost
         */
        playerDataManager.initialize(EventPriority.LOW, EventPriority.NORMAL);
        if (playerDataManager.getDatabase() instanceof SQLDatabaseImpl)
            ((SQLDatabaseImpl) playerDataManager.getDatabase()).startLiveSync();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) classPlaceholders.start();

        // load guild data after loading player data
        nativeGuildManager.load();

        // Init commands
        BuiltinCommand.initializeAll(this, MMOCoreCommands.class);
    }

    @Override
    public void onDisable() {

        // Save guild info
        for (Guild guild : nativeGuildManager.getAll())
            nativeGuildManager.save(guild);

        // Close player data manager
        classPlaceholders.close();
        playerDataManager.close();

        // Reset active blocks
        mineManager.resetRemainingBlocks();

        // Clear spawned loot chests
        lootChests.getActive().forEach(chest -> chest.expire(null));
    }

    /**
     * Called either when the server starts when initializing the manager for
     * the first time, or when issuing a plugin reload; in that case, stuff
     * like listeners must all be cleared before.
     * <p>
     * Also see {@link MMOCoreManager}
     *
     * @param clearBefore True when issuing a plugin reload
     */
    public void initializePlugin(boolean clearBefore) {
        if (clearBefore) reloadConfig();

        configManager = new ConfigManager();

        mineManager.initialize(clearBefore);
        partyManager.initialize(clearBefore);
        attributeManager.initialize(clearBefore);

        // Experience must be loaded before professions and classes
        experience.initialize(clearBefore);

        // Drop tables must be loaded before professions
        dropTableManager.initialize(clearBefore);
        statManager.initialize(clearBefore);
        professionManager.initialize(clearBefore);

        skillTreeManager.initialize(clearBefore);
        classManager.initialize(clearBefore);
        questManager.initialize(clearBefore);
        lootChests.initialize(clearBefore);
        restrictionManager.initialize(clearBefore);
        waypointManager.initialize(clearBefore);
        requestManager.initialize(clearBefore);
        configItems.initialize(clearBefore);
        //Needs to be loaded after the class manager.
        InventoryManager.load();

        actionBarManager.reload(getConfig().getConfigurationSection("action-bar"));

        if (clearBefore)
            PlayerData.getAll().forEach(PlayerData::reload);
    }

    public static void log(String message) {
        log(Level.INFO, message);
    }

    public static void log(Level level, String message) {
        plugin.getLogger().log(level, message);
    }

    public File getJarFile() {
        return getFile();
    }

    public boolean hasEconomy() {
        return economy != null && economy.isValid();
    }
}
