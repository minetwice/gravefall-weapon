# ☠ GRAVEFALL ☠ — The Corrupted Hammer of the Fallen Kingdom

[![Build](https://github.com/minetwice/gravefall-weapon/actions/workflows/build.yml/badge.svg)](https://github.com/minetwice/gravefall-weapon/actions/workflows/build.yml)

A PaperMC plugin: **GRAVEFALL**, a legendary hammer (netherite-axe based)
obtained through a corrupted soul ritual inside a custom **Soul Dimension**,
with three abilities, a death-fragment economy, a portal system, custom
dimension mobs, a 10-minute altar awakening and a tornado finale.

> **Download the jar:** **Actions** tab → latest **Build Gravefall** run →
> download the **Gravefall-plugin** artifact.
> **Resource packs:** see the `resourcepacks/` folder — pick the one for your
> Minecraft version (1.21–1.21.3 / 1.21.4–1.21.11 / 1.21.11–26.2.2 / 26.3+).

**Built for:** Paper 1.21+ (`api-version: 1.21`, stable APIs only, no NMS).
**Requires Java 21+**.

---

## What's inside

### The weapon
| Ability | Trigger | Effect |
|---|---|---|
| **Soul Spiral v2** | Right Click | Soul spiral around you + a ghost-line of soul particles lashes out to the enemy you're aiming at, coils around their body as a double helix, then detonates with damage + knockback. Spectral wolf claws also sweep anyone in melee range. |
| **Grave Quake** | Sneak + Right Click | Leap up; on landing a 10x10 field erupts as falling blocks — launch and crush. |
| **Death Meteor** (passive) | 3-hit combo | A 5x5 burning meteor drops on your victim; stays 10s, then restores terrain. |
| **Soul Harvest** (passive) | Kill with the hammer | Heals you (default 4 HP). |
| **Undying Grip** (passive) | — | The hammer never drops on death. |

Cooldowns are separate per ability and shown live on the action bar.

### The Soul Dimension
- `/gravefall portal` summons a **dormant Gravefall Portal**.
- Only a player carrying **5 Death Fragments** can right-click to awaken it
  (or `/gravefall portal open` for admins). Once open it **stays open forever**
  and **everyone** can walk through.
- Inside: a floating cyan island in a starry void — warped nylium, sculk,
  sea lanterns, soul fire. The CYAN kingdom: prismarine + diamond + sea lantern
  builds, grand lantern-lit path, castle keep. **One kingdom** for the whole
server; every portal leads to the same place.
- After the hammer is claimed, **6 rift portals** tear open in the kingdom
  that send players back to the overworld.
- `/gravefall removeportal` removes the portal in front of you.

### Custom blocks (resource pack)
The dimension's **Soul blocks** are retextured in the same pack:
**Soul Stone** (purpur block), **Soul Pillar** (purpur pillar) and
**Soul Bricks** (end stone bricks) - glowing cyan, used across the kingdom,
the portal frames and the island surface.

### Dimension mobs
All five have custom 3D models + textures (paper icons on display riders):
- **Frost Frog** — long spectral tongue; damages + freezes players ~2 seconds.
- **Soul Wisp** — glowing orb with orbiting shards, drifts around the kingdom.
- **Grave Sentinel** — armored knight with a glowing visor, guards the walls.
- **Void Moth** — dark moth with glowing cyan wings.
- **Cryo Spider** — icy eight-legged hunter.

### The ritual (inside the dimension's kingdom)
Volcano with a live lava crater + lava channels, prismarine monument,
kingdom wall ring with 8 towers, ruined houses, a 3-tier soul altar with a
rotating floating crystal obelisk, 5 new-style fragment pedestals, dense
animated soul "wires" that flow from the frames to the altar during the
10-minute boss bar awakening — and a 24-second lightning tornado finale
when the hammer is claimed.

### Death system
Killing players grants Death Fragments; dying more than 3 times = corruption
ban, undone with `/gravefall revive`.

---

## Commands (`gravefall.admin`, default op)

| Command | Description |
|---|---|
| `/gravefall give <player>` | Ritual animation + grant the hammer |
| `/gravefall fragment <player> [n]` | Give death fragments (testing) |
| `/gravefall structure [radius]` | Raise the corrupted kingdom (overworld) |
| `/gravefall portal [open]` | Summon the Soul Dimension portal |
| `/gravefall removeportal` | Remove the portal in front of you |
| `/gravefall revive <player>` | Unban a corruption-banned player |
| `/gravefall deaths [player]` | Show death count |
| `/gravefall reload` | Reload config |
| `/gravefall help` | Command list |

## Configuration

See `src/main/resources/config.yml` — ability toggles/cooldowns, portal and
dimension settings, mob counts, portal CMD values (7401/7402/7403) etc.

## Resource packs

`resourcepacks/` contains version-specific packs (also attached as release
artifacts of each Actions build):
custom 3D hammer, swirling portal plane, floating soul crystals.

## Project layout

```
src/main/java/com/gravefall/
├── GravefallPlugin.java        # main class, wiring
├── Items.java                   # hammer / fragments / portal + crystal icons
├── CooldownManager.java         # cooldowns + action bar HUD
├── DeathTracker.java            # death counting / bans
├── ability/                     # SoulSpiral v2, GraveQuake, Meteor
├── ritual/                      # give-ritual, tornado finale
├── structure/                   # kingdom generator + altar ritual
├── portal/                      # portals: dormant/open/travel/persistence
├── world/                       # dimension manager + chunk generator
├── mobs/                        # frost frogs, wisps, sentinels
├── listener/                   # weapon + death listeners
└── command/                     # /gravefall commands
```

## License

MIT
