# Parties & Queues

AeroWars has both a **party** system for playing with friends and **matchmaking queues** for finding games.

## Queues

Instead of joining a specific arena, players can queue for a mode and be placed automatically:

```
/aerowars queue solo
/aerowars queue teams
```

There are separate Solo and Teams queues. As players accumulate, AeroWars drops them into a fitting arena; queued players are told when their position improves. Both `/aerowars join` and `/aerowars queue` require the `aerowars.play` permission (granted by default).

## Parties

A party lets a group move and queue together. Open the party menu with `/aerowars party`, or use the chat actions:

| Action | Description |
| --- | --- |
| `/aerowars party invite <player>` | Invite someone (invites expire after `Party.InviteExpirySeconds`). |
| `/aerowars party accept` / `decline` | Respond to an invite. |
| `/aerowars party kick <player>` | Remove a member (leader only). |
| `/aerowars party leave` | Leave your party. |
| `/aerowars party disband` | Disband it (leader only). |

Party size is capped at `Party.MaxSize` (default 4). The menu also lets the leader promote members and toggle **keep-together**.

### Joining a match as a party

When the **leader** joins or queues, the whole party comes along. How they're placed depends on the **keep-together** preference (default from `Party.KeepTogetherByDefault`, toggleable in the menu):

- **Keep together on** — the whole party is placed on **one team**; the match is only chosen if the party fits a single team. Best for co-op Teams play.
- **Keep together off** — an oversized party is **split**, with overflow moved onto other teams. In Solo, everyone counts as their own team (FFA).

Only the party **leader** can queue the group; other members are told to wait for the leader.
