# Economy

AeroWars can pay **coins for kills and wins** and charge for **purchasable kits and cosmetics** — through whatever economy plugin you already run. There is **no hard dependency**: the economy provider is resolved reflectively at runtime, and if none is present the feature is simply a no-op.

## Supported providers

Providers are detected automatically; the first one present wins:

- **EliteEssentials**.
- **Ecotale**.

If none is installed, reward payouts are silently skipped and paid content falls back to free.

## Rewards

Enable payouts in [`config.json`](/guide/setup/config#rewards):

```json
"Rewards": {
  "Economy": {
    "Enabled": true,
    "WinAmount": 100,
    "KillAmount": 10
  }
}
```

- `WinAmount` — coins paid to each winner at the end of a match.
- `KillAmount` — coins paid per kill during a match.

The winner also triggers `Rewards.WinnerCommands`, a list of console commands run on victory (with `{player}` and `{arena}` placeholders) — useful even without an economy plugin.

## Paid kits & cosmetics

Kits and cosmetics can carry a price. When a player buys one, AeroWars charges their balance through the provider.

::: info Deposit-only providers
Some economy plugins can only **pay** balances, not withdraw them. AeroWars detects this: when the provider can't charge, paid content **falls back to free** rather than becoming unbuyable.
:::
