# Changelog

## Unreleased

Initial port of modern CMDCam (`1.21` branch) to Minecraft 1.7.10. Tested in singleplayer in plain Forge and in GT New Horizons 2.8.4; the dedicated server and multiplayer are untested. See the [README](README.md) for features, fixed issues and known limitations.

Fixed during in-game testing:

- A very long camera jump in singleplayer could freeze the server for good
- `/cam-server` crashed with an invalid selector
- A following scene whose target was already gone sent the camera near the world origin
- The hand showed in outside mode
- Typo in the message for an existing scene name
