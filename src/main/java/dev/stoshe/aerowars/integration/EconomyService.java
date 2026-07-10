package dev.stoshe.aerowars.integration;

import dev.stoshe.aerowars.util.Console;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional economy hook for paying out match rewards (win / kill). Resolved
 * reflectively so AeroWars keeps <em>zero</em> compile-time dependency on any
 * economy plugin — exactly like {@link TaleGuardBridge}.
 *
 * <p>Primary target is the sibling Overworld economy
 * ({@code com.overworldlabs.economy.Economy#getAPI()} → {@code EconomyAPI.deposit(UUID,double)}).
 * A couple of common third-party providers are probed as fallbacks. If none is
 * present the service is simply inert and every payout is a silent no-op.
 */
public final class EconomyService {
    private volatile Provider provider;
    private volatile boolean resolved;

    /** True once a provider is available (lazily resolves on first use). */
    public boolean isAvailable() {
        return ensureProvider() != null;
    }

    /** Name of the active provider, or {@code "none"}. */
    public String providerName() {
        Provider p = ensureProvider();
        return p != null ? p.name : "none";
    }

    /**
     * True when a provider is present AND able to take money from a player. Deposit-only providers
     * (which can pay rewards but not withdraw) return false — callers should then treat paid content
     * as free instead of leaving it unbuyable, since a purchase could never succeed.
     */
    public boolean canCharge() {
        Provider p = ensureProvider();
        return p != null && p.withdraw != null;
    }

    /**
     * Pay {@code amount} into the player's balance. Returns {@code true} if the
     * deposit went through. Non-positive amounts and a missing provider are
     * treated as no-ops (return {@code false} for "nothing paid").
     */
    public boolean reward(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0) {
            return false;
        }
        Provider p = ensureProvider();
        if (p == null) {
            return false;
        }
        try {
            return p.deposit(playerId, amount);
        } catch (Throwable t) {
            Console.warning("Economy deposit failed via " + p.name + ": " + t.getMessage());
            return false;
        }
    }

    /** True if the player can afford {@code amount} (and a provider that supports balances exists). */
    public boolean has(UUID playerId, double amount) {
        if (amount <= 0.0) {
            return true;
        }
        Provider p = ensureProvider();
        if (p == null || p.has == null) {
            return false;
        }
        try {
            return p.has.apply(playerId, amount);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Player balance, or {@code -1} when no balance-capable provider is present. */
    public double balance(UUID playerId) {
        Provider p = ensureProvider();
        if (p == null || p.balance == null) {
            return -1;
        }
        try {
            return p.balance.apply(playerId);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Charges {@code amount} from the player (checking funds first). Returns {@code true} only if the
     * money was actually withdrawn. No provider / no withdraw support / insufficient funds → false.
     */
    public boolean charge(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0) {
            return false;
        }
        Provider p = ensureProvider();
        if (p == null || p.withdraw == null) {
            return false;
        }
        try {
            if (p.has != null && !p.has.apply(playerId, amount)) {
                return false;
            }
            return p.withdraw.apply(playerId, amount);
        } catch (Throwable t) {
            Console.warning("Economy charge failed via " + p.name + ": " + t.getMessage());
            return false;
        }
    }

    private Provider ensureProvider() {
        if (resolved) {
            return provider;
        }
        synchronized (this) {
            if (resolved) {
                return provider;
            }
            provider = resolve();
            resolved = true;
            if (provider != null) {
                Console.success("AeroWars economy provider active: " + provider.name);
            } else {
                Console.info("No economy provider found — match money rewards are disabled.");
            }
            return provider;
        }
    }

    private Provider resolve() {
        Provider p = buildOverworld();
        if (p != null) {
            return p;
        }
        p = buildEliteEssentials();
        if (p != null) {
            return p;
        }
        return buildEcotale();
    }

    /** Sibling {@code com.overworldlabs.economy} — the reference provider on this server. */
    private Provider buildOverworld() {
        try {
            Class<?> economy = Class.forName("com.overworldlabs.economy.Economy");
            Method getApi = economy.getMethod("getAPI");
            Object api = getApi.invoke(null);
            if (api == null) {
                return null;
            }
            Method deposit = api.getClass().getMethod("deposit", UUID.class, double.class);
            Method has = api.getClass().getMethod("has", UUID.class, double.class);
            Method withdraw = api.getClass().getMethod("withdraw", UUID.class, double.class);
            Method getBalance = api.getClass().getMethod("getBalance", UUID.class);
            Provider p = new Provider("overworld", (uuid, amount) -> {
                deposit.invoke(api, uuid, amount);
                return true;
            });
            p.has = (uuid, amount) -> Boolean.TRUE.equals(has.invoke(api, uuid, amount));
            p.withdraw = (uuid, amount) -> Boolean.TRUE.equals(withdraw.invoke(api, uuid, amount));
            p.balance = (uuid) -> ((Number) getBalance.invoke(api, uuid)).doubleValue();
            return p;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Provider buildEliteEssentials() {
        try {
            Class<?> api = Class.forName("com.eliteessentials.api.EconomyAPI");
            Method isEnabled = api.getMethod("isEnabled");
            if (!Boolean.TRUE.equals(isEnabled.invoke(null))) {
                return null;
            }
            Method deposit = api.getMethod("deposit", UUID.class, double.class);
            Provider p = new Provider("eliteessentials", (uuid, amount) -> {
                deposit.invoke(null, uuid, amount);
                return true;
            });
            // Best-effort: wire charging support if this build exposes it. Each probe is independent,
            // so partial support (e.g. balance but no withdraw) degrades gracefully to null.
            try {
                Method has = api.getMethod("has", UUID.class, double.class);
                p.has = (uuid, amount) -> Boolean.TRUE.equals(has.invoke(null, uuid, amount));
            } catch (Throwable ignored) {
            }
            try {
                Method withdraw = api.getMethod("withdraw", UUID.class, double.class);
                p.withdraw = (uuid, amount) -> Boolean.TRUE.equals(withdraw.invoke(null, uuid, amount));
            } catch (Throwable ignored) {
            }
            try {
                Method getBalance = api.getMethod("getBalance", UUID.class);
                p.balance = (uuid) -> ((Number) getBalance.invoke(null, uuid)).doubleValue();
            } catch (Throwable ignored) {
            }
            return p;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Provider buildEcotale() {
        try {
            Class<?> api = Class.forName("com.ecotale.api.EcotaleAPI");
            Method isAvailable = api.getMethod("isAvailable");
            if (!Boolean.TRUE.equals(isAvailable.invoke(null))) {
                return null;
            }
            Method deposit = api.getMethod("deposit", UUID.class, double.class, String.class);
            Provider p = new Provider("ecotale", (uuid, amount) -> {
                deposit.invoke(null, uuid, amount, "AeroWars");
                return true;
            });
            // Best-effort charging support (Ecotale threads a source label through its mutations).
            try {
                Method has = api.getMethod("has", UUID.class, double.class);
                p.has = (uuid, amount) -> Boolean.TRUE.equals(has.invoke(null, uuid, amount));
            } catch (Throwable ignored) {
            }
            try {
                Method withdraw = api.getMethod("withdraw", UUID.class, double.class, String.class);
                p.withdraw = (uuid, amount) -> Boolean.TRUE.equals(withdraw.invoke(null, uuid, amount, "AeroWars"));
            } catch (Throwable ignored) {
            }
            try {
                Method getBalance = api.getMethod("getBalance", UUID.class);
                p.balance = (uuid) -> ((Number) getBalance.invoke(null, uuid)).doubleValue();
            } catch (Throwable ignored) {
            }
            return p;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Deposit {
        boolean apply(UUID uuid, double amount) throws Throwable;
    }

    @FunctionalInterface
    private interface Predicate2 {
        boolean apply(UUID uuid, double amount) throws Throwable;
    }

    @FunctionalInterface
    private interface Balance {
        double apply(UUID uuid) throws Throwable;
    }

    private static final class Provider {
        final String name;
        private final Deposit deposit;
        /** Optional: null on providers that only support deposits. */
        Predicate2 has;
        Predicate2 withdraw;
        Balance balance;

        Provider(String name, Deposit deposit) {
            this.name = name;
            this.deposit = deposit;
        }

        boolean deposit(UUID uuid, double amount) throws Throwable {
            return deposit.apply(uuid, amount);
        }
    }
}
