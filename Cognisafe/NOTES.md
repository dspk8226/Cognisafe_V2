# Project Notes

## App Icons

The AndroidManifest.xml references app icons (`ic_launcher` and `ic_launcher_round`) in the mipmap directories. These need to be added for a complete build:

- `app/src/main/res/mipmap-mdpi/ic_launcher.png` (48x48)
- `app/src/main/res/mipmap-hdpi/ic_launcher.png` (72x72)
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png` (96x96)
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` (144x144)
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192x192)

And corresponding `ic_launcher_round.png` files.

**Quick Fix**: Android Studio can generate these automatically, or you can use placeholder icons temporarily for development.

## Keyboard Input Handling

The current implementation handles text input through `onKeyDown()` events. For a production keyboard, you would typically:

1. Create a full keyboard UI with buttons/keys
2. Each key commits text via `InputConnection.commitText()`
3. Track committed text for NBOW detection

The current implementation works for detection purposes and can be extended with a full keyboard UI later.

## Testing Considerations

- Test on a physical device or emulator with SMS capability
- Ensure Guardian Keyboard is set as the default keyboard
- Test with various apps (messaging, notes, browsers)
- Verify SMS permissions are granted
- Test PIN protection functionality

## Configuration File Locations

- Config: `/data/data/com.guardian.keyboard/files/config.json`
- NBOW: `/data/data/com.guardian.keyboard/files/nbow.json`
- Reset flag: `/data/data/com.guardian.keyboard/files/reset_score.flag`

These are accessible only to the app (internal storage).

