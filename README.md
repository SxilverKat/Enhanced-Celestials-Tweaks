# Enhanced Celestials Tweaks

This is an unoffical add-on for Enhanced Celestials.

This is a addon mod for Enhanced Celestials that adds many configuration options. Every lunar event can be tweaked from a single TOML config file. Yes, this mod does work with Configured.

This mod also fixes a bug with Enhanced Celestials that would replay the moon/sky fade-in animation every time you respawned or rejoined a world during an event.

## Bug fixes

Enhanced Celestials' `readFromNetwork` has a buggy comparison (`Holder<LunarEvent> != LunarEvent`) which triggers `eventSwitched` on every sync packet which replays the entire fade-in animation when you respawn during a lunar event.

When rejoining a world during an event, the client's `dayTime` is briefly 0 before the server's time-sync arrives. This causes the `lastTickEvent` field to drop to default and causes the fade animation to replay. The fix was suppressing for ~2 seconds after each sync.

## Configuration

The config file is created at `config/enhancedcelestialstweaks-common.toml`

- **Lunar Forecast**: chance multiplier, minimum night between, etc..
- **Spawn Control**: mob multiplier, per-category multipliers, spawn additions, spawn removals, light-level override
- **Mob attribute multipliers** for health, damage, movement speed, and swim speed.
- Multiply the chance for monsters to spawn with armor and weapons and toggle if they drop the gear.
- **Despawn Control**: prevent event-spawned mobs from despawning and/or force-despawn them after a event ends.
- **Status Effects**: per-event effect listing with target filtering.
- **Drop Boosts**: You can configure how harvest moons multiply drops for any list of item tags and you can multiply rare drop rates during any event.
- **Visuals**: A large amount of config options to be able to change moon color, sky color, moon texture, fog density, fog color, sound track volume, pitch, loop, and customize the start and end messages of events.
- Customize the night length in ticks per event.
- **Multi-dimensions**: Have the ability to add specific lunar events to specific dimensions.
- **Visual only** option.
- Sleep message customization which allows you to change the text shown when a lunar event blocks sleep.
- The ability to disable Enhanced Celestials commands for non-operators.

### Example

```toml
[events.blood_moon]
chance_multiplier = 1.5
mob_spawn_multiplier = 2.0
mob_category_multipliers = ["MONSTER:3.0"]
mob_health_multiplier = 1.5
mob_damage_multiplier = 1.5
spawn_additions = ["minecraft:phantom;80;1;2", "minecraft:wither_skeleton;40;1;1"]
spawn_removals = ["minecraft:creeper"]
moon_color = "ff0000"
night_length_ticks = 24000
```

Requires **Enhanced Celestials** (duh)
