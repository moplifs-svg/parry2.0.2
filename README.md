# ParryMod 2.0.0 (Minecraft 1.20.1, Forge 47.3.0)

Press **F** to parry (rebind in Options > Controls > Parry).

- Window: 8 ticks (0.4s) after pressing; miss = 5s cooldown; a successful parry has NO cooldown.
- Melee hit during the window: damage cancelled, attacker knocked back.
- Sword icon + countdown (10, 9, 8...) under the crosshair while on cooldown.
- Projectiles (arrows etc.) are deflected back but give NO weakness.
- Whoever got melee-parried gets Weakness I for 1 second.
- Pressing F: the player's arm goes into a blocking pose for the parry window (everyone sees it; third person for yourself).
- Plays `parry.ogg` + your parry image (textures/effect/parry_effect.png) drawn in the world facing every camera, with a bloom/glow halo. The parry also lights up the area like a lantern (light level 15) for about 0.35s (everyone sees it, from any camera angle), .
- Window and knockback are in `config/parry-common.toml`. Miss cooldown is fixed at 5s.

## Build (needs JDK 17)
    gradle build           # (Gradle 8.1.1, or use the wrapper from the official Forge 1.20.1 MDK) jar -> build/libs/parrymod-2.0.0.jar
    gradle runClient    # test in dev
Drop the jar into `mods/` of a Forge 1.20.1 install.

Note: vanilla "Swap hands" is also F; the mod suppresses it when F is pressed.
Both will show red in Controls until you rebind one.

## Permission
Nobody can parry by default. An operator (permission level 2, or cheats on in singleplayer/LAN) must allow them:
- `/parry <player>` - allow a player (also accepts selectors like `@a`)
- `/unparry <player>` - remove it again
The list is saved with the world.

Without permission, pressing F does nothing at all (no swing, no cooldown, no message). The player is not
notified when permission is given or removed.
