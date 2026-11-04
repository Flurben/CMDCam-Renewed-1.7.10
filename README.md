# CMDCam Renewed 1.7.10

Camera paths and cinematic shots by command for Minecraft 1.7.10 (Forge 10.13.4.1614). This is a port of the features of modern [CMDCam](https://github.com/CreativeMD/CMDCam) by CreativeMD (the `1.21` branch) to 1.7.10, with additional features and bug fixes.

## Status

**Plays in singleplayer in the dev client.** The dedicated server, `/cam-server` and multiplayer are untested.

| Part | Checked by |
| --- | --- |
| Math, timers, stage planning, duration parsing | `MathSelfTest`: 348 checks pass, run by `./gradlew build` |
| All code against Minecraft 1.7.10 and Forge | `./gradlew build` passes, including reobfuscation |
| Coremod hook targets | Checked against the decompiled source, the client logs `Patched EntityRenderer` |
| Mod loading | `runClient` reaches the end of Forge loading in the deobfuscated dev client |
| Release jar | Loads in a Forge client and logs `Patched EntityRenderer`, scenes not played with it yet |
| Dedicated server | Not run yet, `runServer` asks you to accept the EULA |
| Playing scenes in singleplayer | Camera playback looked right in a manual test, commands and saving responded |

To run the tests without Gradle (no Minecraft needed), from the project root:

```bash
mkdir -p build/selftest && javac --release 8 -Xlint:-options -encoding UTF-8 -d build/selftest $(find src/main/java/team/creative/cmdcam/common/math src/main/java/team/creative/cmdcam/common/scene/attribute src/main/java/team/creative/cmdcam/common/scene/timer src/main/java/team/creative/cmdcam/common/scene/run -name '*.java') src/main/java/team/creative/cmdcam/common/util/DurationParser.java src/test/java/team/creative/cmdcam/MathSelfTest.java && java -cp build/selftest team.creative.cmdcam.MathSelfTest
```

To build the mod, which also runs these tests:

```bash
./gradlew build
```

`runObfClient` looks for mods in `run/client/mods`, but the task puts the jar in `run/obfuscated/mods`. Copy it over to test the release jar, and remove the copy afterwards, because `runClient` would otherwise load the mod twice.

## Usage

1. Fly to where the shot should begin and press `P` (or `/cam add`). Repeat for each point.
2. Press `U` (or `/cam start 10s`) to play. `U` or `/cam stop` ends it.

| Key | Action |
| --- | --- |
| `P` | Add a point |
| `U` | Start or stop |
| `Delete` | Clear all points |
| `V` / `B` / `N` | Zoom in / reset / out, hold sneak to go faster |
| `G` / `H` / `J` | Roll left / reset / right |
| Jump | Pause or resume, default mode only |

`/cam` without arguments lists every sub command. The most useful ones:

| Command | What it does |
| --- | --- |
| `/cam add [x y z [yaw pitch [roll [fov]]]]` | Add a point, `~` makes a value relative |
| `/cam insert\|set\|remove\|goto <index>` | Edit a point or teleport to it |
| `/cam start [duration] [loops]` | Play. `-1` loops forever, durations look like `10s`, `500ms`, `1m30s` |
| `/cam mode [default\|outside]` | `default` moves the player, `outside` uses a separate camera and leaves you in control |
| `/cam interpolation [linear\|cubic\|hermite\|cosine\|circular\|invcircular]` | Shape of the path |
| `/cam target\|follow [none\|self\|select\|player <name>\|entity <selector>\|pos <x y z>]` | Look at or move with a target |
| `/cam pitch\|yaw\|pos step <value>` | How fast targets are followed, `1` is instantly |
| `/cam show\|hide [all\|<interpolation>]` | Preview paths in the world |
| `/cam save\|load <name>` | Store scenes, on the server if it has the mod |
| `/cam info` | Everything about the current scene |

`/cam-server` (permission level 2, usable from command blocks) stores scenes in the world and plays them for others: `start <scene> <players> [duration] [loops]`, `stop|pause|resume <players>`, `get <scene> <setting> ...`, `create|remove <scene>`, `list`, `clear`.

## Differences to the old 1.7.10 version

New from modern CMDCam: scenes as objects with saved settings, stage based looping, smooth start, distance based timing, spinning fixes, position follow targets, tick timer, follow markers and `/cam-server get`.

New in this port, in neither upstream version:

- `hide_gui`, `return_start` and `timer realtime|ticks` scene settings
- `/cam info`, `/cam delete <name>`, tab completion and `@e[type=...,name=...,r=...,c=...]` selectors
- Client scenes survive a restart (`config/cmdcam_scenes.dat`)
- `~` relative coordinates and rotation in points
- Target following does not depend on the frame rate
- A config screen with the defaults for new scenes

## Fixed upstream issues

Numbers refer to the [upstream issues](https://github.com/CreativeMD/CMDCam/issues). Only a singleplayer playback test has been done so far, so most have not been checked individually.

| Issue | Problem | Fix |
| --- | --- | --- |
| #13, #51, #107 | Cursor flickers while playing | A mouse helper discards the movement, instead of releasing the cursor each frame |
| #13 | Camera a block or two above its points | Uses `setPositionAndRotation`, since `setLocationAndAngles` adds `yOffset` |
| #32, #35, #45, #64 | Outside mode is offset or does not work | A dedicated camera entity with the correct `yOffset` |
| #60 | HUD setting is not restored | Restored from the value saved at the start |
| #67, #98 | Zoom changes the FOV in the options | A coremod hook on `getFOVModifier`, the option is never written |
| #69 | Crash when selecting a target | Null guards and client check, the selecting click is cancelled |
| #89 | Crash printing a target | Null guard |
| #102 | Scene continues after death or leaving the world | Stops without crashing |
| #108 | No way to hide the HUD | `hide_gui` setting |
| #109 | Circular center off, `P` and `/cam add` disagree | Both use the same relative calculation |
| #111 | No relative points | `~` for all values |

Other bugs found while porting:

- A number of loops played one time too many, and endless loops got stuck at the end
- Zoom and roll ran at double speed, because the render tick handler lacked a phase check
- The camera jumped after the pause menu, because the timer kept running
- Time from before a pause carried over into the next stage
- Distance estimates sampled a single spot of each segment, which skewed distance based timing
- The circular path added a point twice and used height in its angle calculation
- Zero length paths produced invalid times
- Printing a duration of 0 crashed
- `/cam-server start` with a duration changed the saved scene
- Previewing or playing changed the rotation of the scene's points

## Known limitations

- **Minema is not supported.** The upstream integration is a stub, so nothing was ported.
- **Scene titles** do not exist as a concept in 1.7.10.
- **Far paths (#110):** chunks along the path only load as far as the server allows.
- **Default mode on servers:** the client moves the player, so a server's anti-cheat or `allow-flight=false` can kick them. Use outside mode or allow flight.
- **OptiFine** is untested.

## Credits and license

Based on [CMDCam](https://github.com/CreativeMD/CMDCam) by CreativeMD. Licensed under LGPL-3.0 like the original, see [LICENSE](LICENSE). Built on the [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) template.
