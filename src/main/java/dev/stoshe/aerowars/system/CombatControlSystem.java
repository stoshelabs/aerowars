package dev.stoshe.aerowars.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.stoshe.aerowars.game.Match;
import dev.stoshe.aerowars.game.Team;
import dev.stoshe.aerowars.model.AeroWarsConfig;
import dev.stoshe.aerowars.manager.MatchManager;
import dev.stoshe.aerowars.model.GameMode;

import java.util.UUID;

/**
 * Governs PvP during a match: no damage before the cages drop, no damage to/from
 * spectators, and optional friendly-fire suppression in team arenas.
 */
public class CombatControlSystem extends EntityEventSystem<EntityStore, Damage> {
    private final MatchManager matchManager;
    private final AeroWarsConfig config;

    public CombatControlSystem(MatchManager matchManager, AeroWarsConfig config) {
        super(Damage.class);
        this.matchManager = matchManager;
        this.config = config;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(PlayerRef.getComponentType());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, Damage event) {
        PlayerRef victim = chunk.getComponent(index, PlayerRef.getComponentType());
        if (victim == null) {
            return;
        }
        UUID victimUuid = victim.getUuid();
        Match match = matchManager.getPlayerMatch(victimUuid);
        if (match == null) {
            return;
        }
        // Only living combat during the ACTIVE phase counts.
        if (match.state.isRunning() == false) {
            event.setCancelled(true);
            return;
        }
        if (match.spectators.contains(victimUuid)) {
            event.setCancelled(true);
            return;
        }
        // Resolve the attacker once. UUIDs must be compared with .equals() (not ==): each
        // PlayerRef.getUuid() hands back a distinct instance, so identity checks are unreliable.
        UUID attacker = attackerOf(event, store);
        boolean selfDamage = attacker != null && attacker.equals(victimUuid);
        // A spectator (or hidden admin spectator) must never be able to deal damage.
        if (attacker != null && !selfDamage
                && (match.spectators.contains(attacker) || matchManager.isAdminSpectator(attacker))) {
            event.setCancelled(true);
            return;
        }
        // Friendly fire in team arenas.
        if (match.mode() == GameMode.TEAMS && !config.Match.FriendlyFire) {
            if (attacker != null && !selfDamage) {
                Team victimTeam = match.teamOf(victimUuid);
                Team attackerTeam = match.teamOf(attacker);
                if (victimTeam != null && victimTeam == attackerTeam) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private UUID attackerOf(Damage event, Store<EntityStore> store) {
        if (event.getSource() instanceof Damage.EntitySource source) {
            Ref<EntityStore> ref = source.getRef();
            if (ref != null) {
                PlayerRef attacker = store.getComponent(ref, PlayerRef.getComponentType());
                if (attacker != null) {
                    return attacker.getUuid();
                }
            }
        }
        return null;
    }
}
