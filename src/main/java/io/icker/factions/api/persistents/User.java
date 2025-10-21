package io.icker.factions.api.persistents;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;

@Name("User")
public class User {
    private static final HashMap<UUID, User> STORE = Database.load(User.class, User::getID);

    public enum ChatMode {
        FOCUS, FACTION, GLOBAL
    }

    public enum Rank {
        OWNER, LEADER, COMMANDER, MEMBER, GUEST
    }

    public enum SoundMode {
        NONE, WARNINGS, FACTION, ALL
    }

    @Field("ID")
    private UUID id;

    @Field("FactionID")
    private UUID factionID;

    @Field("Rank")
    public Rank rank;

    @Field("Radar")
    public boolean radar;

    @Field("Chat")
    public ChatMode chat = ChatMode.GLOBAL;

    @Field("Sounds")
    public SoundMode sounds = SoundMode.ALL;

    @Field("HomeCooldown")
    public long homeCooldown = -1;

    @Field("Power")
    private int power;

    public boolean autoclaim = false;
    public boolean bypass = false;
    public String language = "en_us";

    private User spoof;

    public User(UUID id) {
        this.id = id;
        this.power = FactionsMod.CONFIG.POWER.PLAYER_START;
        this.radar = FactionsMod.CONFIG.DISPLAY.RADAR_DEFAULT;
    }

    public User() {
        this.power = FactionsMod.CONFIG.POWER.PLAYER_START;
        this.radar = FactionsMod.CONFIG.DISPLAY.RADAR_DEFAULT;
    }

    public String getKey() {
        return id.toString();
    }

    @NotNull
    public static User get(UUID id) {
        if (!STORE.containsKey(id)) {
            User.add(new User(id));
        }
        return STORE.get(id);
    }

    public static List<User> getByFaction(UUID factionID) {
        return STORE.values().stream().filter(m -> m.isInFaction() && m.factionID.equals(factionID))
                .toList();
    }

    public static void add(User user) {
        STORE.put(user.id, user);
    }

    public UUID getID() {
        return id;
    }

    public boolean isInFaction() {
        return factionID != null;
    }

    private String getEnumName(Enum<?> value) {
        return Arrays.stream(value.name().split("_"))
                .map(word -> word.isEmpty() ? word
                        : Character.toTitleCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    public String getRankName() {
        return getEnumName(rank);
    }

    public String getChatName() {
        return getEnumName(chat);
    }

    public String getSoundName() {
        return getEnumName(sounds);
    }

    @Nullable
    public Faction getFaction() {
        return Faction.get(factionID);
    }

    public User getSpoof() {
        return spoof;
    }

    public void setSpoof(User user) {
        this.spoof = user;
    }

    public void joinFaction(UUID factionID, Rank rank) {
        this.factionID = factionID;
        this.rank = rank;
        FactionEvents.MEMBER_JOIN.invoker().onMemberJoin(Faction.get(factionID), this);
    }

    public void leaveFaction() {
        UUID oldFactionID = factionID;
        factionID = null;
        rank = null;
        FactionEvents.MEMBER_LEAVE.invoker().onMemberLeave(Faction.get(oldFactionID), this);
    }

    public static Collection<User> all() {
        return STORE.values();
    }

    public static void audit() {
        STORE.values().forEach((user) -> {
            if (Faction.get(user.factionID) == null) {
                user.factionID = null;
            }

            if (!user.isInFaction()) {
                user.rank = null;
            }
        });
    }

    public static void save() {
        Database.save(User.class, STORE.values().stream().toList());
    }

    public int getPower() {
        return power;
    }

    public int getMaxPower() {
        return FactionsMod.CONFIG.POWER.PLAYER_MAX;
    }

    public int adjustPower(int adjustment) {
        int max = getMaxPower();
        int newPower = Math.min(Math.max(0, power + adjustment), max);
        int oldPower = power;

        if (newPower == oldPower) {
            return 0;
        }

        Faction faction = getFaction();
        Integer oldFactionPower = null;
        if (faction != null) {
            oldFactionPower = faction.getPower();
        }

        power = newPower;

        if (faction != null && oldFactionPower != null) {
            faction.notifyPowerChange(oldFactionPower);
        }

        return newPower - oldPower;
    }

    public void setPower(int power) {
        int bounded = Math.min(Math.max(0, power), getMaxPower());
        if (bounded == this.power) {
            return;
        }

        Faction faction = getFaction();
        Integer oldFactionPower = null;
        if (faction != null) {
            oldFactionPower = faction.getPower();
        }

        this.power = bounded;

        if (faction != null && oldFactionPower != null) {
            faction.notifyPowerChange(oldFactionPower);
        }
    }
}
