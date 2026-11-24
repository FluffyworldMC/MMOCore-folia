package net.Indyuce.mmocore.experience;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.gson.JsonElement;
import io.lumine.mythic.lib.gson.JsonObject;
import io.lumine.mythic.lib.util.lang3.Validate;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.PlayerExperienceGainEvent;
import net.Indyuce.mmocore.api.event.PlayerLevelChangeEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.party.AbstractParty;
import net.Indyuce.mmocore.player.Message;
import net.Indyuce.mmocore.util.Language;
import net.Indyuce.mmocore.util.Tasks;
import net.Indyuce.mmocore.util.formula.FormulaFailsafeException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class PlayerProfessions {
    private final Map<String, Double> exp = new HashMap<>();
    private final Map<String, Integer> level = new HashMap<>();

    private final PlayerData playerData;

    public PlayerProfessions(PlayerData playerData) {
        this.playerData = playerData;
    }

    /**
     * When loading data through YAML
     */
    public PlayerProfessions load(ConfigurationSection config) {
        for (String key : config.getKeys(false))
            if (MMOCore.plugin.professionManager.has(key)) {
                exp.put(key, config.getDouble(key + ".exp"));
                level.put(key, config.getInt(key + ".level"));
            }

        if (config.contains("times-claimed"))
            // Watch out for the deep section lookup
            for (String key : config.getConfigurationSection("times-claimed").getKeys(true))
                playerData.getItemClaims().put("profession." + key, config.getInt("times-claimed." + key));

        return this;
    }

    /**
     * When saving data through YAML
     */
    public void save(ConfigurationSection config) {
        for (String id : exp.keySet())
            config.set(id + ".exp", exp.get(id));
        for (String id : level.keySet())
            config.set(id + ".level", level.get(id));
    }

    /**
     * When saving data through SQL
     */
    public String toJsonString() {
        JsonObject json = new JsonObject();
        for (Profession profession : MMOCore.plugin.professionManager.getAll()) {
            JsonObject object = new JsonObject();
            object.addProperty("exp", getExperience(profession));
            object.addProperty("level", getLevel(profession));

            json.add(profession.getId(), object);
        }

        return json.toString();
    }

    /**
     * When loading data through SQL
     */
    public void load(String json) {
        JsonObject obj = MythicLib.plugin.getGson().fromJson(json, JsonObject.class);

        // Load profession exp and levels
        for (Entry<String, JsonElement> entry : obj.entrySet())
            if (MMOCore.plugin.professionManager.has(entry.getKey())) {
                JsonObject value = entry.getValue().getAsJsonObject();
                exp.put(entry.getKey(), value.get("exp").getAsDouble());
                level.put(entry.getKey(), value.get("level").getAsInt());
            }

        // Load times claimed
        if (obj.has("timesClaimed"))
            for (Entry<String, JsonElement> entry : obj.getAsJsonObject("timesClaimed").entrySet())
                playerData.getItemClaims().put("profession." + entry.getKey(), entry.getValue().getAsInt());
    }

    public PlayerData getPlayerData() {
        return playerData;
    }

    public int getLevel(String profession) {
        return Math.max(1, level.getOrDefault(profession, 1));
    }

    public int getLevel(Profession profession) {
        return getLevel(profession.getId());
    }

    public double getExperience(String id) {
        return exp.getOrDefault(id, 0d);
    }

    public double getExperience(Profession profession) {
        return getExperience(profession.getId());
    }

    public long getLevelUpExperience(Profession profession) {
        return profession.getExpCurve().getExperience(playerData, getLevel(profession));
    }

    @Deprecated
    public long getLevelUpExperience(String id) {
        final var prof = MMOCore.plugin.professionManager.get(id);
        if (prof == null) return 0;
        return prof.getExpCurve().getExperience(null, getLevel(id));
    }

    @Deprecated
    public void setLevel(Profession profession, int value) {
        setLevel(profession, value, PlayerLevelChangeEvent.Reason.UNKNOWN);
    }

    @Deprecated
    public void takeLevels(@NotNull Profession profession, int value) {
        int current = Math.max(1, level.getOrDefault(profession.getId(), 1));
        level.put(profession.getId(), Math.max(1, current - value));
    }

    public void setLevel(@NotNull Profession profession, int newLevel, @NotNull PlayerLevelChangeEvent.Reason reason) {
        if (profession.hasMaxLevel()) newLevel = Math.min(profession.getMaxLevel(), newLevel);
        final var previousValue = level.put(profession.getId(), Math.max(1, newLevel));
        final var oldLevel = previousValue == null ? 1 : Math.max(1, previousValue);

        // Data loaded async (sync event, clashes) => no event called
        if (reason != PlayerLevelChangeEvent.Reason.CHOOSE_PROFILE)
            Bukkit.getPluginManager().callEvent(new PlayerLevelChangeEvent(playerData, profession, oldLevel, newLevel, reason));

        if (getPlayerData().getMMOPlayerData().isPlaying()) {
            playerData.getStats().updateStats();
        }
    }

    public void setExperience(Profession profession, double value) {
        exp.put(profession.getId(), value);
    }

    public void giveLevels(Profession profession, int value, EXPSource source) {
        long equivalentExp = 0;
        final var currentLevel = getLevel(profession);
        while (value-- > 0)
            equivalentExp += profession.getExpCurve().getExperience(playerData, currentLevel + value);
        giveExperience(profession, equivalentExp, source);
    }

    public boolean hasReachedMaxLevel(Profession profession) {
        return profession.hasMaxLevel() && getLevel(profession) >= profession.getMaxLevel();
    }

    public void giveExperience(@NotNull Profession profession, double value, @NotNull EXPSource source) {
        giveExperience(profession, value, source, null, true);
    }

    public void giveExperience(@NotNull Profession profession, double value, @NotNull EXPSource source, @Nullable Location hologramLocation, boolean splitExp) {
        Validate.isTrue(playerData.isOnline(), "Cannot give experience to offline player");
        if (value <= 0) {
            exp.merge(profession.getId(), value, (current, v) -> Math.max(0, current + v));
            return;
        }

        if (hasReachedMaxLevel(profession)) {
            setExperience(profession, 0);
            return;
        }

        // Splitting exp through party members
        AbstractParty party;
        if (splitExp && (party = playerData.getParty()) != null && MMOCore.plugin.configManager.splitProfessionExp) {
            var nearbyMembers = party.findPlayersForExp(getPlayerData());
            value /= (nearbyMembers.size() + 1); // Share exp
            final double share = value;
            for (PlayerData member : nearbyMembers)
                Tasks.executeOnEntityThread(member.getPlayer(), () ->
                        member.getCollectionSkills().giveExperience(profession, share, source, null, false));
        }

        // Apply buffs AFTER splitting exp
        value *= (1 + playerData.getStats().getStat("ADDITIONAL_EXPERIENCE_" + UtilityMethods.enumName(profession.getId())) / 100) * MMOCore.plugin.boosterManager.getMultiplier(profession);

        final var event = new PlayerExperienceGainEvent(playerData, profession, value, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        // Display hologram
        if (hologramLocation != null && profession.getOption(Profession.ProfessionOption.EXP_HOLOGRAMS)) {
            // TODO custom location per profession/exp source.
            hologramLocation.add(.5 + .7 * Math.random(), 1.3 + Math.random() * .33, .5 + .7 * Math.random());
            MMOCoreUtils.displayIndicator(hologramLocation, Language.EXP_HOLOGRAM.getFormat().replace("{exp}", MythicLib.plugin.getMMOConfig().decimal.format(event.getExperience())));
        }

        final var maxLevel = profession.getMaxLevel();
        var currentExp = Math.max(0d, exp.getOrDefault(profession.getId(), 0d) + event.getExperience());
        final var oldLevel = getLevel(profession);
        int newLevel = oldLevel;
        double experienceNeeded;

        /*
         * Loop for exp overload when leveling up, will continue
         * looping until exp is 0 or max newLevel has been reached
         */
        while (currentExp >= (experienceNeeded = profession.getExpCurve().getExperience(playerData, newLevel))) {

            if (maxLevel > 0 && newLevel >= maxLevel) {
                currentExp = 0;
                break;
            }

            currentExp -= experienceNeeded;
            newLevel++;

            // Give main class exp
            try {
                var mainExpGiven = profession.getExperience().evaluate(newLevel, playerData);
                playerData.giveExperience(mainExpGiven, EXPSource.PROFESSION_TO_CLASS);
            } catch (FormulaFailsafeException exception) {
                exception.log("Could not evaluate profession level-up exp for %s", profession.getId());
            }

            // Apply profession experience table
            profession.updateAdvancement(playerData, newLevel);
        }

        this.exp.put(profession.getId(), currentExp);

        if (newLevel > oldLevel) {
            setLevel(profession, newLevel, PlayerLevelChangeEvent.Reason.LEVEL_UP);
            Message.PROFESSION_LEVEL_UP.send(playerData, "level", newLevel, "profession", profession.getName());
        }

        if (playerData.isOnline()) {

            // Build exp bar
            StringBuilder bar = new StringBuilder(ChatColor.BOLD.toString());
            int chars = (int) (currentExp / experienceNeeded * 20);
            for (int j = 0; j < 20; j++)
                bar.append(j == chars ? ChatColor.WHITE.toString() + ChatColor.BOLD : "").append("|");

            var ratioFormatted = MythicLib.plugin.getMMOConfig().decimal.format(currentExp / experienceNeeded * 100);
            Message.EXP_NOTIFICATION.send(playerData,
                    "profession", profession.getName(),
                    "progress", bar.toString(),
                    "ratio", ratioFormatted);
        }
    }
}
