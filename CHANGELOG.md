# Changelog

## 0.2.0 (2026-11-05)

Saved paths now work in all worlds and on all servers. See [Saved paths](README.md#saved-paths).

- Paths are stored as files in the `cmdcam-paths` folder of the instance. Scenes from `config/cmdcam_scenes.dat` move there on the first start
- `/cam save` also stores the path on the client if the server has the mod
- `/cam load` without a name opens a list with previews and statistics of all paths, to sort, filter, load, duplicate, rename and delete them, and with a button to open the folder
- Advanced load moves the whole path to given coordinates or to your position, with `~` offsets
- `/cam save` without a name opens a screen for the name and the start location. It can overwrite your own paths in the folder and, for operators, all paths on the server
- A duplicate belongs to whoever made it
- Operators of any level can now save, rename, delete and overwrite all paths on the server, this used to need level 4
- Saved paths remember their creator, when they were saved and when they were last loaded. Paths saved earlier show these as unknown
- `/cam list` and `/cam delete` now always work on the folder, also on servers with the mod
- Names on the server now have the same rules as file names

Fixed:

- A scene too large for Minecraft's packet format could not be sent to players, for example with `/cam-server start` on a long path
- A client could not send a large path without an error, it now gets a message and keeps the copy in the folder
- Typo in the message for a scene which could not be parsed

## 0.1.0 (2026-11-04)

First test release.

Initial port of modern CMDCam (`1.21` branch) to Minecraft 1.7.10. Tested in singleplayer in plain Forge and in GT New Horizons 2.8.4; the dedicated server and multiplayer are untested. See the [README](README.md) for features, fixed issues and known limitations.

Fixed during in-game testing:

- A very long camera jump in singleplayer could freeze the server for good
- `/cam-server` crashed with an invalid selector
- A following scene whose target was already gone sent the camera near the world origin
- The hand showed in outside mode
- Typo in the message for an existing scene name
