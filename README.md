# CMDCam Renewed 1.7.10

Camera paths and cinematic shots by command for Minecraft 1.7.10 (Forge 10.13.4.1614). This is a port of the features of modern [CMDCam](https://github.com/CreativeMD/CMDCam) by CreativeMD (the `1.21` branch) to 1.7.10, with additional features and bug fixes.

## Download

Get the jar from the [releases page](https://github.com/Flurben/CMDCam-Renewed-1.7.10/releases) and put it in the `mods` folder of a Minecraft 1.7.10 client with Forge 10.13.4.1614. Take the `cmdcam-<version>.jar`, without `dev` or `sources` in its name.

## Status

**Tested in singleplayer, not yet on a dedicated server or with a second player.**

| Part | Checked by |
| --- | --- |
| Math, timers, stage planning, duration parsing, path names and statistics | `MathSelfTest`: 449 checks pass, run by `./gradlew build` |
| All code against Minecraft 1.7.10 and Forge | `./gradlew build` passes, including reobfuscation |
| Playing scenes and saved paths in plain Forge | In-game test, 44 of 44 groups pass: Prism, reobfuscated jar, Java 8, LWJGL 2.9.4 |
| Playing scenes and saved paths in a modpack | In-game test, 44 of 44 groups pass: GT New Horizons 2.8.4 with 230 mods, Angelica, lwjgl3ify, Java 25, LWJGL 3.3.3 |
| Preview, outside mode, hidden HUD, roll and zoom | Screenshots from the in-game test, checked by eye in both setups |
| Screens for saved paths | In-game test with clicks and keys sent to the screens, screenshots checked by eye. The folder button opened Explorer on Windows, macOS and Linux have not been tried |
| Saved paths from other players, operators on a server | Only simulated in singleplayer: paths with other creators, and the buttons without permission. The server refusing someone who is not an operator has not been run |
| `/cam-server` with the world owner as the only player | In-game test |
| Holding the zoom and roll keys | In-game test with LWJGL 2 only. It is skipped under lwjgl3ify, where the keyboard cannot be simulated |
| Dedicated server | **Not run**, `runServer` asks you to accept the EULA. A static check found no client classes in server code |
| A second player, LAN, joining a real server | **Not tested** |
| Real mouse and keyboard input | Manual test in the dev client only, the in-game test simulates key presses |

The in-game test lives in `src/ingametest` and only becomes part of the jar with `-PingameTest`, a normal build does not contain it. It runs when the game is started with `-Dcmdcam.ingametest=true` (add `-Dcmdcam.ingametest.slow=true` for a large modpack, and `-Dcmdcam.ingametest.only=<parts of names, separated by commas>` for selected tests, which can also be set in a `cmdcam-ingametest.properties` file in the game directory), writes `cmdcam-ingametest.txt` and screenshots to the game directory, and closes the game. If nothing happens for a minute (three in slow mode) or a single test takes more than two minutes (eight in slow mode), it writes the state of all threads to the report and ends the game. Use a world and instance you do not need: it creates test worlds, moves and kills the player, and adds and removes paths named `t_...` in `cmdcam-paths`.

```bash
./gradlew jar reobfJar -PingameTest
```

To run the tests without Gradle (no Minecraft needed), from the project root:

```bash
mkdir -p build/selftest && javac --release 8 -Xlint:-options -encoding UTF-8 -d build/selftest $(find src/main/java/team/creative/cmdcam/common/math src/main/java/team/creative/cmdcam/common/scene/attribute src/main/java/team/creative/cmdcam/common/scene/timer src/main/java/team/creative/cmdcam/common/scene/run src/main/java/team/creative/cmdcam/common/scene/path -name '*.java') src/main/java/team/creative/cmdcam/common/util/DurationParser.java src/test/java/team/creative/cmdcam/MathSelfTest.java && java -cp build/selftest team.creative.cmdcam.MathSelfTest
```

To build the mod, which also runs these tests:

```bash
./gradlew build
```

`runObfClient` looks for mods in `run/client/mods`, but the task puts the jar in `run/obfuscated/mods`. Copy it over to test the release jar, and remove the copy afterwards, because `runClient` would otherwise load the mod twice. On Windows, `runObfClient` crashed about 40 seconds after launch in both runs so far, with an `UnsatisfiedLinkError` in the sound system. One of those runs did not have this mod loaded, so the crash is not caused by it, and it did not happen in `runClient`.

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
| `/cam save [name]` | Store the path in the `cmdcam-paths` folder, and on the server if it has the mod. Without a name it opens the [save screen](#saved-paths) |
| `/cam load [name]` | Load a path. Without a name it opens the [list of paths](#saved-paths) |
| `/cam list`, `/cam delete <name>` | The paths in the `cmdcam-paths` folder |
| `/cam info` | Everything about the current scene |

`/cam-server` (permission level 2, usable from command blocks) stores scenes in the world and plays them for others: `start <scene> <players> [duration] [loops]`, `stop|pause|resume <players>`, `get <scene> <setting> ...`, `create|remove <scene>`, `list`, `clear`.

## Saved paths

Saved paths live in the `cmdcam-paths` folder of the Minecraft instance, one `.nbt` file per path, so they work in every world and on every server. Files can be copied in and out of the folder while the game runs. Scenes saved by 0.1.0 are moved there on the first start, and the old file stays as `config/cmdcam_scenes.dat.migrated`.

If the server has the mod, `/cam save` stores the path on the server as well. Only operators can do that, for everyone else the path is only stored in the folder. `/cam load <name>` looks on the server first and then in the folder. In singleplayer the world counts as the server, which keeps `/cam-server` working.

**`/cam load`** opens a list of all paths, newest first. Each line has a preview of the curve `/cam show` would draw, the number of points, the interpolation, duration, length, average speed, start, creator, and when it was saved and last loaded. Point at a preview to turn it and to see everything else.

- **Sort** by last saved or last loaded, **filter** by creator and by where a path is stored.
- **Load** uses the path as it was saved.
- **Advanced** loads it somewhere else: enter coordinates, `~ ~ ~` for your position or `~5 ~ ~-2` for an offset from it. The whole path moves, and so does a look or follow target at a fixed block. This is not available for a path following an entity or a player, since it has no location of its own.
- **Duplicate** always creates a copy in the folder, which is how to keep a path from a server. You are the creator of the copy.
- **Rename** and **Delete** change the copy this line stands for. On a server only operators can use them, for all paths there.
- **Open Path Folder** works like the button in the resource pack screen.

A path stored in the same way in the folder and on the server takes one line, marked `Folder + Server`. Its buttons act on the copy in the folder, choose `Stored: Server` to reach the other one.

**`/cam save`** opens the same list below a name and a start location, which defaults to the current start. A different start moves the saved path, not the one you are editing. **Overwrite** asks first. In the folder it only works for paths by the same creator, on a server operators can overwrite all paths. `/cam save <name>` overwrites without asking.

Limits of the game: a client can only send about 32 kB to a server, so a path of several hundred points or more only reaches the folder, and you are told so. Names can be up to 64 characters long, without `< > : " / \ | ? *`, since they are file names.

## Differences to the old 1.7.10 version

New from modern CMDCam: scenes as objects with saved settings, stage based looping, smooth start, distance based timing, spinning fixes, position follow targets, tick timer, follow markers and `/cam-server get`.

New in this port, in neither upstream version:

- `hide_gui`, `return_start` and `timer realtime|ticks` scene settings
- `/cam info`, `/cam delete <name>`, tab completion and `@e[type=...,name=...,r=...,c=...]` selectors
- Saved paths are files that work in all worlds, with [screens to manage them](#saved-paths)
- `~` relative coordinates and rotation in points
- Target following does not depend on the frame rate
- A config screen with the defaults for new scenes

## Fixed upstream issues

Numbers refer to the [upstream issues](https://github.com/CreativeMD/CMDCam/issues). The in-game test covers most of these in singleplayer. Anything that depends on another player or a dedicated server has not been checked.

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
- In singleplayer, a very long jump of the camera (`/cam goto`, a fast path, `return_start`) could freeze the server for good, because it scanned every block between the old and the new position for collisions
- `/cam-server` crashed with an invalid selector such as `@r[c=-5]`
- A following scene whose target was already gone sent the camera to its offsets near the world origin, it now waits where it is
- A scene too large for Minecraft's packet format could not be sent to players, for example with `/cam-server start` on a long path

## Known limitations

- **Key conflicts in modpacks:** default keys can clash with other mods, and 1.7.10 gives a key press to only one binding. Change them in the Controls menu. In GT New Horizons 2.8.4:

  | Key | CMDCam | Also used by | Effect |
  | --- | --- | --- | --- |
  | `P` | Add point | Hardcore Ender Expansion: open Compendium | Adds the point **and opens the book** |
  | `Delete` | Clear points | Navigator: action | CMDCam's binding gets the press |
  | `V` | Zoom in | Place item (as configured in that pack), `key.mm-paste` | Another binding gets the press event |
  | `B` | Reset zoom | JourneyMap: create waypoint | Another binding gets the press event |
  | `J` | Roll right | JourneyMap: toggle map | Another binding gets the press event |

  CMDCam reads zoom and roll keys as held state, so these should still work on `V`, `B` and `J`, with the other mod reacting as well. That has not been tested in the pack.

- **Minema is not supported.** The upstream integration is a stub, so nothing was ported.
- **Scene titles** do not exist as a concept in 1.7.10.
- **Far paths (#110):** chunks along the path only load as far as the server allows.
- **Default mode on servers:** the client moves the player, so a server's anti-cheat or `allow-flight=false` can kick them. Use outside mode or allow flight.
- **OptiFine** is untested.

## Credits and license

Based on [CMDCam](https://github.com/CreativeMD/CMDCam) by CreativeMD. Licensed under LGPL-3.0 like the original, see [LICENSE](LICENSE). Built on the [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) template.
