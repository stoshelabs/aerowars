package dev.stoshe.aerowars.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root config object, deserialized from config.json by Gson. Field names match
 * the JSON keys (PascalCase). Sections are nested static classes with defaults,
 * so a missing key falls back to a sane value.
 */
public final class AeroWarsConfig {
    public General General = new General();
    public Setup Setup = new Setup();
    public Match Match = new Match();
    public Cages Cages = new Cages();
    public Loot Loot = new Loot();
    public Kits Kits = new Kits();
    public Rewards Rewards = new Rewards();
    public Scoreboard Scoreboard = new Scoreboard();
    public Effects Effects = new Effects();
    public Party Party = new Party();
    public Platform Platform = new Platform();
    public Spectator Spectator = new Spectator();
    public Database Database = new Database();

    /**
     * Optional SQL persistence for stats/leaderboards (mirrors the Plots DatabaseSettings). The driver
     * is chosen by the JDBC URL (sqlite/mysql/mariadb/postgres). When disabled — or when the JDBC driver
     * isn't on the server classpath — stats fall back to {@code stats.json}.
     */
    public static final class Database {
        public boolean Enabled = false;
        public String JdbcUrl = "jdbc:sqlite:aerowars.db";
        public String Username = "";
        public String Password = "";
        public int MaxPoolSize = 10;
    }

    /**
     * Hypixel-style spectator hotbar: on death the player gets a couple of named tool items
     * (a living-player tracker that opens a teleport modal, and a return-to-lobby item). Slot
     * and item id are configurable; the items are locked in place and removed on leave.
     */
    public static final class Spectator {
        public boolean Enabled = true;
        /** Hotbar slot (0-8) and item id of the "track living players" tool. */
        public int TrackerSlot = 0;
        public String TrackerItem = "AeroWars_SpecTracker";
        /** Hotbar slot (0-8) and item id of the "return to lobby" tool. */
        public int LobbySlot = 8;
        public String LobbyItem = "AeroWars_SpecLobby";
    }

    /** Generation of the spawn platform in a freshly created void template world. */
    public static final class Platform {
        /** Block the platform is paved with. */
        public String Block = "Rock_Stone";
        /** Side length (blocks) of the square platform; clamped to an odd number ≥ 1. */
        public int Size = 9;
        /** Y level the platform sits at. */
        public int Height = 100;
    }

    public static final class Party {
        public boolean Enabled = true;
        /** Maximum members (leader included). */
        public int MaxSize = 4;
        /** How long a pending invite stays valid. */
        public int InviteExpirySeconds = 60;
        /**
         * Default value of a party's "keep together" preference (leaders can flip it in the party menu).
         * When ON, the party only joins a match where the WHOLE party fits on a single team; it is never
         * split across teams — and if no arena can fit them the leader is told instead of waiting forever.
         * When OFF, an oversized party is split: the overflow members are randomly moved onto other teams.
         */
        public boolean KeepTogetherByDefault = false;
    }

    public static final class Effects {
        /** Launch a firework show from the arena centre on victory. */
        public boolean VictoryFireworks = true;
        /** Native Hytale firework particle systems, picked at random per burst. */
        public List<String> FireworkParticles = new ArrayList<>(List.of(
                "Firework_GS", "Firework_Mix2", "Firework_Mix3", "Firework_Mix4",
                "Example_Fireworks", "Example_Firework_Mix", "Example_Firework_ColorBase",
                "Cinematic_Fireworks_Red_XL", "Cinematic_Fire_Firework"));
        /** How many bursts, over how many seconds, and how high above the arena they may pop. */
        public int FireworkBursts = 14;
        public int FireworkDurationSeconds = 6;
        public int FireworkMaxRise = 24;
        public float FireworkScale = 1.5f;
        /** Raise the winner(s) onto a podium above the arena centre during the end celebration. */
        public boolean PodiumEnabled = true;
        /** Block the podium is built from, and how high above the arena centre it sits. */
        public String PodiumBlock = "Glass_Block_Yellow";
        public int PodiumHeight = 8;
    }

    public static final class General {
        public String Language = "pt_br";
        public int AutoSaveIntervalSeconds = 300;
        public String LobbyWorld = "world";
        public String LobbySpawn = "";
        /**
         * Chat prefix prepended to the plugin's info/success/error/warning messages. Branding lives HERE
         * (not baked into every translated line), so the admin fully owns it. Set {@code PrefixEnabled}
         * to false for prefix-less messages, or change {@code Prefix} to rebrand. Supports {#RRGGBB} tags.
         */
        public boolean PrefixEnabled = true;
        public String Prefix = "{#55ccff}[AeroWars] {#ffffff}";
    }

    public static final class Setup {
        /** Item handed to the admin during setup; left-clicking a block with it marks a spawn.
         *  Must be a breaking TOOL (not a weapon) so the left-click fires a BreakBlockEvent. */
        public String SpawnWand = "AeroWars_SetupWand";
    }

    public static final class Match {
        public int MinPlayers = 2;
        public int MaxDurationSeconds = 900;
        public int CountdownSeconds = 15;
        public int StartGraceSeconds = 3;
        public int EndCelebrationSeconds = 30;
        public int EmptyMatchDeletionSeconds = 30;
        public boolean FriendlyFire = false;
        public boolean AllowSpectators = true;
        public boolean SpectateOnDeath = true;
        /** Save the player's inventory + game mode on join and restore it when they leave/finish. */
        public boolean SaveInventory = true;
    }

    public static final class Cages {
        public boolean Enabled = true;
        /** Cage block. AeroWars ships a custom "Glass_Block" (+ colours: Glass_Block_Red, _Blue,
         *  _Green, _Cyan, _Yellow, _Lime, _Orange, _Pink, _Purple, _Magenta, _White, _Black). */
        public String BlockId = "Glass_Block";
        public int Radius = 1;
        public int Height = 2;
    }

    public static final class Loot {
        /** Fill every chest once when the match starts (the initial loot; not itself an "event"). */
        public boolean FillOnStart = true;
        public Events Events = new Events();

        /**
         * Unified in-match EVENTS. Everything that changes chests mid-game — refills, gradual loot
         * upgrades, and anything added later — is a single entry in {@link #List}, each with its own
         * {@code Type} and {@code Time}. This replaces the old separate "chest refill" + "loot event"
         * toggles so there is ONE place to configure what happens and when.
         *
         * <p><b>Randomize</b>: when {@code true}, every event's fixed {@code Time} is IGNORED. Instead,
         * at match start each enabled event is dropped at a random moment, in a random order, spread
         * across the match — always kept inside the match duration and never closer together than
         * {@link #MinGapSeconds} (so events don't fire too fast / all at once). When {@code false} the
         * events simply fire at their configured {@code Time} (seconds since the match started).
         */
        public static final class Events {
            public boolean Enabled = true;
            /** Ignore each event's Time and schedule them at random moments/order (see class doc). */
            public boolean Randomize = false;
            /** Minimum spacing between randomized events, and the earliest one can fire, in seconds. */
            public int MinGapSeconds = 90;
            /** The configured events. Order only matters when {@code Randomize} is false. */
            public List<Event> List = defaultEvents();

            /** One scheduled event. {@code Time} is ignored when {@link Events#Randomize} is on. */
            public static final class Event {
                /** Event kind: "refill" or "loot_upgrade" (see {@link LootEventType}). */
                public String Type = "refill";
                /** Seconds into the match when it fires (ignored when Randomize is enabled). */
                public int Time = 300;
                public boolean Enabled = true;

                public Event() {
                }

                public Event(String type, int time) {
                    this.Type = type;
                    this.Time = time;
                }
            }
        }

        /** Sensible starter timeline: an upgrade, a refill, then a final full upgrade. */
        private static List<Events.Event> defaultEvents() {
            List<Events.Event> list = new ArrayList<>();
            list.add(new Events.Event("loot_upgrade", 300));
            list.add(new Events.Event("refill", 480));
            list.add(new Events.Event("loot_upgrade", 660));
            return list;
        }
    }

    public static final class Kits {
        public boolean Enabled = true;
        public String DefaultKit = "warrior";
        public boolean SelectionDuringCountdown = true;
    }

    public static final class Rewards {
        public List<String> WinnerCommands = new ArrayList<>();
        public Economy Economy = new Economy();
    }

    public static final class Economy {
        public boolean Enabled = false;
        public int WinAmount = 100;
        public int KillAmount = 10;
    }

    public static final class Scoreboard {
        public boolean Enabled = true;
        /** Footer line at the bottom of the scoreboard (server link/brand). Supports {#RRGGBB} and
         *  legacy &-colour codes; blank hides it. */
        public String Footer = "{#55ccff}stoshe.dev";
    }

    /**
     * Copies every top-level section reference from {@code other} into this instance. Used by the
     * runtime {@code /aerowars reload}: managers hold THIS config object and read nested sections
     * ({@code config.Match.X}) live, so swapping the section refs here propagates new values to them
     * without reconstructing anything.
     */
    public void applyFrom(AeroWarsConfig other) {
        if (other == null) {
            return;
        }

        General = other.General;
        Setup = other.Setup;
        Match = other.Match;
        Cages = other.Cages;
        Loot = other.Loot;
        Kits = other.Kits;
        Rewards = other.Rewards;
        Scoreboard = other.Scoreboard;
        Effects = other.Effects;
        Party = other.Party;
        Platform = other.Platform;
        Spectator = other.Spectator;
        Database = other.Database;
    }

    /** Fills nulls left by a sparse config file so callers never NPE. */
    public void normalize() {
        if (General == null) General = new General();
        if (Setup == null) Setup = new Setup();
        if (Match == null) Match = new Match();
        if (Cages == null) Cages = new Cages();
        if (Loot == null) Loot = new Loot();
        if (Loot.Events == null) Loot.Events = new Loot.Events();
        if (Loot.Events.List == null) Loot.Events.List = new ArrayList<>();
        if (Kits == null) Kits = new Kits();
        if (Rewards == null) Rewards = new Rewards();
        if (Rewards.WinnerCommands == null) Rewards.WinnerCommands = new ArrayList<>();
        if (Rewards.Economy == null) Rewards.Economy = new Economy();
        if (Scoreboard == null) Scoreboard = new Scoreboard();
        if (Effects == null) Effects = new Effects();
        if (Party == null) Party = new Party();
        if (Platform == null) Platform = new Platform();
        if (Spectator == null) Spectator = new Spectator();
        if (Database == null) Database = new Database();
        if (Effects.FireworkParticles == null || Effects.FireworkParticles.isEmpty()) {
            Effects.FireworkParticles = new ArrayList<>(List.of(
                    "Firework_GS", "Firework_Mix2", "Firework_Mix3", "Firework_Mix4",
                    "Example_Fireworks", "Example_Firework_Mix", "Example_Firework_ColorBase",
                    "Cinematic_Fireworks_Red_XL", "Cinematic_Fire_Firework"));
        }
    }

    public static AeroWarsConfig getDefault() {
        AeroWarsConfig c = new AeroWarsConfig();
        c.Rewards.WinnerCommands.add("broadcast &6{player} {#ffffff}venceu a partida em &e{arena}&f!");
        return c;
    }
}
