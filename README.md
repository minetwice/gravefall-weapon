# ☠ GRAVEFALL ☠ — The Corrupted Hammer of the Fallen Kingdom

[![Build](https://github.com/minetwice/gravefall-weapon/actions/workflows/build.yml/badge.svg)](https://github.com/minetwice/gravefall-weapon/actions/workflows/build.yml)

A PaperMC plugin that adds **GRAVEFALL**, a legendary hammer (netherite-axe based)
obtained through a corrupted soul ritual, with three abilities, a death-fragment
economy, a 500x500 procedurally generated corrupted kingdom, and a full
10-minute altar awakening ritual ending in a tornado finale.

> **Download the jar:** go to the **Actions** tab → click the latest
> **Build Gravefall** run → download the **Gravefall-plugin** artifact.

**Built for:** Paper servers, Minecraft **1.21 → 1.21.x line** (compiled against
the 1.21.4 API using stable Bukkit/Paper APIs only, `api-version: 1.21`, no NMS).
**Requires Java 21+** on the server (standard for 1.21+).

---

## The Weapon — GRAVEFALL

Base item: **NETHERITE_AXE** with **CustomModelData 7401** (configurable) so your
resource pack can override its model safely.

| Ability | Trigger | Effect | Cooldown (default) |
|---|---|---|---|
| **Soul Spiral** | Right Click | Rising soul-particle spiral + two spectral wolf-claw swipes (left, then right). Enemies in front take damage + knockback and lose soul particles. | 8s |
| **Grave Quake** | Sneak + Right Click | You leap up; on landing a 10x10 field of ground erupts as falling blocks. Players are launched and can be crushed by falling stone. | 22s |
| **Death Meteor** (passive) | 3-hit melee combo | A 5x5 burning meteor drops onto your victim with heavy AoE damage. The rock stays 10s, then vanishes and restores the terrain. | 25s |
| **Soul Harvest** (passive) | Kill a player with the hammer | The hammer devours the soul and heals you (default 4 HP). | — |
| **Undying Grip** (passive) | — | The hammer never drops from your inventory when you die. | — |

All cooldowns are separate and displayed live on the **action bar** (above the
hotbar) while holding the hammer. Every ability can be enabled/disabled in the
config.

---

## How to obtain GRAVEFALL

### 1) Admin command
`/gravefall give <player>` — full ritual: the earth quakes and nearby blocks
hop, a particle portal opens in the sky, the hammer slowly descends to the
player, a soul circle ignites, lightning strikes — then the hammer is granted.

### 2) The Ritual of Five Fragments
1. Killing a player grants the killer a **Death Fragment** (5 needed).
2. `/gravefall structure` raises a **500x500 Corrupted Kingdom** around you:
   central soul altar, 5 fragment pedestals with item frames, a smoking volcano,
   a prismarine monument, obsidian spikes, ruined deepslate towers, corrupted
   ground, soul fire and wither roses.
3. Place one Death Fragment in each of the **5 item frames** (soul burst +
   progress counter each time).
4. All 5 bound → soul "wires" connect the frames to the altar and a
   **10-minute boss bar** starts.
5. Timer complete → light beam, fragments fly to the hammer, frames vanish,
   and **GRAVEFALL stands on the altar** (rotating, glowing).
6. **Right-click the hammer to claim it** — announcement:
   *"<Name> has obtained GRAVEFALL, the Hammer of the Fallen Kingdom!"*
7. Claim triggers the **tornado finale**: 20 seconds of lightning across the
   structure, blocks ripped up into a growing particle tornado around the new
   owner, and every nearby player except the owner takes damage.

### Death system
- Any player dying more than `deaths.max-deaths` (default 3) times is **banned**
  by the corruption. Unban with `/gravefall revive <player>`.

---

## Commands & Permissions (`gravefall.admin`, default: op)

| Command | Description |
|---|---|
| `/gravefall give <player>` | Ritual animation + grant the hammer |
| `/gravefall fragment <player> [n]` | Give death fragments (testing) |
| `/gravefall structure [radius]` | Raise the corrupted kingdom (radius capped at 250) |
| `/gravefall revive <player>` | Unban a corruption-banned player |
| `/gravefall deaths [player]` | Show death count |
| `/gravefall reload` | Reload `config.yml` without restarting |
| `/gravefall help` | Command list |

---

## Configuration (`plugins/Gravefall/config.yml`)

```yaml
custom-model-data: 7401        # change to match your resource pack
weapon:
  keep-on-death: true          # hammer never drops on death
abilities:
  soul-spiral:  {enabled: true, cooldown: 8,  damage: 5.0,  knockback: 1.5}
  ground-slam:  {enabled: true, cooldown: 22, damage: 4.0,  launch-power: 1.15}
  meteor:       {enabled: true, cooldown: 25, damage: 10.0, lifetime-seconds: 10}
  combo-hits: 3
  soul-harvest: {heal: 4.0}
deaths:
  max-deaths: 3
structure:
  radius: 250                  # 500x500
  blocks-per-tick: 1200        # generation speed
ritual:
  required-fragments: 5
  charge-seconds: 600          # 10 minutes
```

---

## Resource pack (custom model)

The item uses `minecraft:netherite_axe` + **CustomModelData 7401**. To give it a
custom look, add to your resource pack:

`assets/minecraft/models/item/netherite_axe.json`
```json
{
  "parent": "minecraft:item/handheld",
  "textures": { "layer0": "minecraft:item/netherite_axe" },
  "overrides": [
    { "predicate": { "custom_model_data": 7401 }, "model": "grv:item/gravefall" }
  ]
}
```
and create `assets/grv/models/item/gravefall.json` pointing at your hammer texture.
Without a resource pack the weapon simply looks like a netherite axe with the
name, lore and glint.

---

## Building from source

**Locally:** `mvn package` (JDK 21 + internet for the PaperMC repo) — the jar
appears in `target/`.

**Via GitHub Actions:** every push to `main` (or a manual run via
*Run workflow*) builds the plugin and uploads **`Gravefall-plugin`** as an
artifact in the Actions tab.

## Project layout

```
src/main/java/com/gravefall/
├── GravefallPlugin.java        # main class, config, wiring
├── Items.java                  # hammer + death fragment factories
├── CooldownManager.java        # per-ability cooldowns + action bar HUD
├── DeathTracker.java           # death counting for the ban system
├── ability/
│   ├── SoulSpiralTask.java     # right-click ability
│   ├── GroundSlamTask.java     # sneak+right-click ability
│   └── MeteorTask.java         # passive 3-hit combo meteor
├── ritual/
│   ├── RitualGiveTask.java     # admin give ritual animation
│   └── TornadoFinale.java      # 20s post-claim finale
├── structure/
│   ├── StructureBuilder.java   # 500x500 corrupted kingdom generator
│   └── StructureRitual.java    # frames, soul wires, boss bar, claim
├── listener/
│   ├── WeaponListener.java    # ability triggers + combo
│   └── DeathListener.java      # fragments, bans, soul harvest
└── command/
    └── GravefallCommand.java   # /gravefall commands
```

## License

MIT
