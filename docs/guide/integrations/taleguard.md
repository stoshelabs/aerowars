# TaleGuard

[TaleGuard](https://github.com/stoshelabs/taleguard) is a protection plugin. AeroWars registers a **protection hook** with it so that interactions TaleGuard governs (item use/pickup/drop, building, etc.) follow AeroWars' own rules inside match, setup, and lobby worlds.

## How it works

- The hook is a **soft, reflective** integration — there is no compile-time dependency. AeroWars publishes an adapter into a shared registry that TaleGuard discovers on its own.
- If TaleGuard isn't installed, the hook is simply never read and has no effect.
- No configuration is required; the hook registers automatically at startup.

## What the hook enforces

Inside AeroWars worlds, the hook answers TaleGuard's "is this allowed?" checks:

- **Admins** mid-setup can build freely.
- **Spectators** are denied all interaction.
- **In a match**, destructive or interactive actions are denied until the match is actually running (after the cages drop).
- **In the lobby world**, build-type actions (break, place, harvest, and similar) are denied for non-admins.

This layers on top of AeroWars' own ECS protection systems, which already cancel block breaking/placing for spectators and pre-game players regardless of TaleGuard.
