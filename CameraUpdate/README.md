# Cube movement

A minimal Java 3D demo using Swing and perspective projection. No dependencies.

See [CLASS_STATS.txt](CLASS_STATS.txt) for class stats, ability details, passives,
and planned class designs. Keep it updated alongside gameplay changes.

Double-click `Run.bat`, or run `javac *.java` and then `java CubeMovement` in this folder.
Requires JDK 11 or newer (JDK 24 is installed on this computer).

- WASD or arrow keys move the cube along the plane.
- Hold Shift while moving to sprint (7 units per second).
- The bottom-left stamina bar drains only while sprinting and actually moving.
- A full bar lasts 5 seconds of sprinting. Walking or standing recovers 15% per second.
- At empty stamina, you walk until stamina recovers to 25%, then sprinting becomes available again.
- In solo play, pausing or opening the class menu freezes stamina. In a LAN match, menus stop your inputs while the shared simulation continues.
- Press Space to jump. Release and press again after landing for another jump.
- Press Space again in the air for a second jump. Press Left Control while airborne to ground pound back to the floor.
- You can move and sprint in the air; the camera follows the jump.
- The third-person camera follows the cube at a desired distance of 8 units.
- Orbit through 360 degrees horizontally and look almost straight up or down.
- The camera stays above the ground and outside the player cube, adjusting its position when blocked.
- Move the mouse to orbit the camera around the cube; no clicking required.
- Scroll the mouse wheel to zoom in and out. Scrolling fully inward switches to first person; scrolling back out switches to third person. Zooming out is limited to 20 units.
- Horizontal mouse movement rotates the view; vertical movement changes its angle.
- During play, the cursor is hidden and recentered for continuous camera rotation.
- Esc opens the pause menu and unlocks the mouse. Click Resume or press Esc to play again.
- The menu also includes Quit. Switching to another window automatically pauses and releases the mouse.
- Movement follows the camera direction, so W / Up always moves away from you.
- The cube smoothly turns left and right around its upright Y axis, trailing the camera rotation slightly even while standing still.
- Press R to open the class menu and unlock the mouse. Choose Gauntlet, Mage, Ranger, or Rogue to preview its name, then click Equip to apply it and return to play.
- The slanted class sidebar slides diagonally down and left along its edges. Selecting a class slides its title up on the right, with the Equip button following shortly afterward.
- The equipped class appears in the top-right corner. Gauntlet is playable; the other classes remain placeholders.
- Press R or Esc, or click Close, to leave the class menu without equipping a new class.
- Close the window to exit.

Movement uses elapsed time and a walking speed of 4 units per second. Diagonal
movement is normalized, and the cube stays within the plane edges. The orbiting
camera renders the cube and grid in perspective through Java2D. Its vertical
angle allows -89 to 89 degrees; ground collision keeps the camera above the plane. Geometry behind the
camera is clipped so the grid renders correctly when viewed up close.

Solid scenery can be registered in `cameraObstacles` using world-space boxes
(`minX, minY, minZ, maxX, maxY, maxZ`). The camera pulls closer when a box blocks
its view and returns to its desired distance when clear. Training dummies do
not block the camera or ordinary player movement.

## Gauntlet training

Equip Gauntlet from the R menu to gain Reinforced (+15 HP, for 115 total) and its moveset.
Four stationary training dummies have 100 HP, take damage and knockback, and respawn at their original positions four seconds after defeat. They do not attack or walk. Blue dummies show their slow percentage and remaining duration.

| Input | Move | Base damage | Cooldown |
| --- | --- | --- | --- |
| Left mouse | Punch | 23 | 1.7 seconds |
| Q | Pummel | 30 across three hits | 45 seconds |
| E | Shockwave | 10 in the inner ring | 25 seconds |
| F | Hay-maker | 45 plus knockback | 50 seconds |

Directional attack hitboxes follow the player's current body facing as it turns toward the camera direction, including during startup and active abilities. Punch reaches 2 units and Pummel reaches 1.8 units, in a forward cone. Specials require you to be on the ground. Pummel grabs one target, lifts and slams it, then punches three times; the grabbed target stays in front as you move and turn. Shockwave slows targets within 2.5 units by 70%, and targets within 5 units by 40%, for 3 seconds. Hay-maker launches up to 3.6 units over 0.3 seconds and steers with your turning, while allowing normal movement alongside the launch. You can walk, sprint, jump, and ground pound during abilities and recovery. Recovery prevents starting another attack; missed Pummel and Hay-maker attempts have 1.1 and 1.2 seconds of recovery respectively. LAN snapshots synchronize attack facing so the hitbox overlay uses the same direction as the host's hit detection.

Kill-Streak increases damage for subsequent attacks by 0.05x per damaging hit, from 1x up to 1.35x (seven hits to reach the cap). Each damaging hit also refreshes a 6.5-second timer. Individual Pummel hits and each damaged Shockwave target grant their own stacks; damage is calculated using the multiplier at the start of the attack. After 6.5 seconds without a hit, the multiplier returns to 1x. Death and leaving Gauntlet also clear the streak. Pausing and the class menu freeze combat timers. Switching classes cannot bypass attack recovery or cooldowns.

The HUD shows HP, speed in world units per second, maximum speed, defense, damage multiplier and its timer, recovery time, and cooldowns. Gauntlet has 0% base defense; its durability passive increases HP. Visuals use colored cubes, animated gauntlets, damage flashes, floating damage numbers, shockwave rings, and world-space particles. Training dummies do not attack. LAN matches support combat between players; damage reduction remains unimplemented.

## Particle effects

Particles occupy 3D world positions and render through the camera with the player and NPC geometry. They move, fade, and expire; cube debris and shards fall and bounce. These effects are cosmetic and use the existing damage, movement, and cooldown rules.

| Action or status | Effect |
| --- | --- |
| Walk / sprint | Ground-contact dust, more frequent at sprinting speed |
| Jump / double jump | Takeoff dust; airborne ring and shards for the second jump |
| Land / ground pound | Landing dust scaled by impact speed; heavier dust, bouncing debris, and a ring for ground pounds |
| Punch | Gold fist trail; directional sparks and shards only when a hit connects |
| Pummel | Grab flash, slam dust and debris, then an impact burst for each confirmed punch |
| Shockwave | Expanding dust ring and scattered cube debris |
| Hay-maker | Air trails during the launch, with a larger spark/debris burst on a successful hit |
| Knockback | Short directional wisps and sliding dust |
| Kill-Streak | Subtle gold gauntlet glow and sparks that strengthen toward 1.35x; drifting sparks when the streak ends |
| Slowed | Sparse blue wisps around the affected entity |
| Defeat / respawn | Colored cube fragments; inward-gathering sparks followed by a delayed outward pulse |

Open **Esc > Particles** to choose **OFF**, **LOW** (160 particles), **MEDIUM** (400, default), or **HIGH** (800). Quality changes emission density and the maximum live count; lowering the setting immediately trims existing effects. In the console, use `particles low`, `particles medium`, `particles high`, `particles off`, or `particles clear`. Detailed `stats on` includes the current count and quality. A full `reset` restores medium quality.

Solo particle motion freezes with pause and follows `timescale`. LAN particles continue while your controls are paused, matching the ongoing shared match. Confirmed gameplay events carry sequence IDs so repeated snapshots do not replay bursts; joining a match does not replay its old effects. Each device chooses its own quality. Copy the updated game folder to every device because the network snapshot format now includes particle events.

## LAN lobbies

Up to eight players can share an arena on the same Wi-Fi or wired local network. Everyone needs the same updated game folder and JDK 11 or newer. Each device runs `Run.bat`; no additional libraries or account are required. This uses a direct connection to the host, with no internet matchmaking or automatic lobby discovery.

1. On the host device, press **F2**, or open **Esc > LAN Lobby**.
2. Enter your player name, leave the port at **24560**, and click **Host Lobby**.
3. Share the local IP shown in the lobby with your friends. If several addresses appear, use the one on the shared Wi-Fi/wired network.
4. On each other device, open the lobby, enter a name, the host IP, and the same port, then click **Join Lobby**. For testing two copies on one computer, use `127.0.0.1` as the host IP.
5. The roster shows everyone connected. The host clicks **Start Match**. Players can also join after a match has started.
6. Press **R** and equip **Gauntlet** to use its moves. Other players appear as colored cubes with names and HP. Attacks can hit other players or the shared training dummies.
7. Press **F2** to view the roster or **Leave Lobby**. Leaving restores a fresh solo arena. If the host leaves or closes the game, all guests disconnect and return to the lobby screen.

The host runs movement, stamina, jumping, combat, cooldowns, damage, grabs, knockback, slows, and respawns. Knocked-out players respawn after three seconds at full class HP with their damage streak cleared. Dummies respawn after four seconds. Shockwave slows reduce other players' movement speed. Menus, window switching, and the console stop your movement inputs but do not pause the match or make you invulnerable. A paused player can still be hit or pushed. Gameplay-changing dev commands are unavailable during a lobby session; `help`, `clear`, `hitboxes`, `stats`, and `particles` remain available.

If Windows asks about Java network access, allow it on your **private** network. The host must accept inbound TCP on the selected port (24560 by default). If a connection fails, check the host IP and port, that both devices are on the same network, and that the network permits device-to-device connections; guest Wi-Fi and some school networks isolate devices. The host computer must keep the game running. A different port can be selected if the default is already in use.

Network checks run real TCP connections on this machine. A physical second-device Wi-Fi test is still needed to verify your router and firewall configuration.

## Developer console

Press backtick (the key above Tab) to open the console. Solo gameplay pauses and the cursor unlocks; in a LAN match, only your controls pause. Enter runs a command; Up and Down recall command history. Backtick or Esc closes it. Opening from the pause or class menu returns to that menu; opening during play resumes play when closed. Commands are local to this running game and do not execute shell commands. Dev settings last until `reset` or restarting the game. Gameplay-changing commands require leaving the LAN lobby first.

| Command | Effect |
| --- | --- |
| `help` | Show all commands and usage examples |
| `clear` | Clear console output |
| `heal` | Restore maximum player HP |
| `hp <amount>` | Set HP between 0 and the current class maximum; 0 defeats the player |
| `stamina <0..100>` | Set stamina; a refill to at least 25 clears exhaustion |
| `god on/off` | Toggle immunity to incoming damage; does not restore HP |
| `cooldowns reset` | Set all ability cooldowns to zero |
| `cooldowns on/off` | Toggle ability cooldowns; turning off clears existing cooldowns |
| `streak <1..1.35>` | Set damage multiplier and give it a fresh 6.5-second timer; 1 clears it |
| `streak reset` | Clear multiplier and timer |
| `damage <amount>` | Deal 0–100000 damage to yourself; respects god mode |
| `tp <x> <z>` | Teleport to ground level within -10..10 on each axis, clamped to fit the player on the plane; cancel the current attack |
| `speed <0..10>` | Set walking and sprinting speed multiplier; 1 is normal |
| `dummy spawn` | Spawn a dummy two units ahead, clamped to the plane |
| `dummy reset` | Restore the original four dummies using the current dummy max HP |
| `dummy clear` | Remove all dummies and release an active grab |
| `dummy hp <1..100000>` | Set max HP for existing and future dummies; refill existing dummies immediately |
| `hitboxes on/off` | Show melee cones, Shockwave circles, Hay-maker range, and dummy bounds |
| `stats on/off` | Toggle detailed position, velocity, speed/time settings, dev flags, and combat state |
| `particles off/low/medium/high` | Set local particle quality and its live-particle limit |
| `particles clear` | Clear local particles and visual tracking |
| `timescale <0..4>` | Scale movement, physics, combat, effects, and timers; 1 is normal, 0 freezes the simulation; camera and menus remain responsive |
| `reset` | Restore player, class selection, arena, camera, and all dev settings to startup defaults; keep the console open |

Cooldown commands do not remove attack animations or recovery. Core HP, stamina, and combat HUD remain visible when detailed stats are off. Customized dummy max HP persists through dummy respawns and `dummy reset`; a full `reset` restores it to 100.

Examples: `cooldowns off`, `dummy hp 500`, `tp 0 -2`, `speed 1.5`, `timescale 0.5`.

Compile checks with `javac *.java`, then run `java ParticleCheck`, `java LobbyCheck`, `java ConsoleCheck`, `java CombatCheck`, `java MovementCheck`, `java StaminaCheck`, `java CameraCheck`, and `java RotationCheck`.

## Versions and restore points

This folder is a local Git repository. `VERSION.txt` records the current release;
`CHANGELOG.md` describes the changes. The initial release is tagged `v0.1.0`.
History stays inside the hidden `.git` folder, so each version uses the same
project folder. Keep `.git` when moving or backing up the development project.
Compiled `.class` files are ignored and rebuilt by `Run.bat`.

Useful commands from this folder:

```text
git status                  Show changes since the last checkpoint
git diff                    Review changes before saving
git log --oneline --decorate Show saved checkpoints and version tags
git tag --list               List named versions
```

To save completed work, run `git add -A`, then `git commit -m "Describe the change"`.
To undo a saved change while retaining history, use `git revert <commit-id>`.
Milestone releases get version tags, such as `v0.2.0` for a feature release and
`v0.2.1` for a fix. Local commits work without a GitHub account or internet access.
No remote repository is configured by this setup.
