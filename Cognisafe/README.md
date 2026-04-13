# CogniSafe v2 Keyboard (Android IME)

An Android-first Kotlin IME that detects mental health risk in real-time using:
- Rule-based weighted keyword/phrase detection (on-device)
- A backend MentalBERT inference service (FastAPI) that loads your pretrained `.pt` weights **as-is**

## Features

- **Custom Keyboard (IME)**: Fully functional Android keyboard that captures all typed text in any app
- **Rolling buffer (in-memory only)**: keeps ~30 recent sentences/messages (privacy-friendly)
- **Weighted keyword detection**: phrase-based scoring with safe-phrase filtering (e.g. "kill time" ignored)
- **Backend inference (async)**: calls `/predict` with context formatted as `"User recent thoughts: ..."` (throttled)
- **Decision engine**: combines keyword score + model confidence
- **Interventions**: suggestion popup, warning overlay, optional SMS to guardian
- **Configurable Settings**: guardian phone, keyword threshold, backend URL, PIN
- **PIN Protection**: Optional PIN requirement to dismiss blocking overlay

## Project Structure

```
GuardianKeyboard/
├── app/
│   ├── src/main/
│   │   ├── java/com/guardian/keyboard/
│   │   │   ├── GuardianImeService.kt      # Custom keyboard IME service
│   │   │   ├── BlockingActivity.kt        # Full-screen blocking overlay
│   │   │   └── MainActivity.kt            # Settings/configuration UI
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── input_view.xml         # Keyboard input view
│   │   │   │   ├── activity_blocking.xml  # Blocking overlay layout
│   │   │   │   └── activity_main.xml      # Settings activity layout
│   │   │   ├── xml/
│   │   │   │   └── method.xml             # IME configuration
│   │   │   └── values/                    # Strings, colors, themes
│   │   ├── assets/
│   │   │   └── nbow.json                  # Sample NBOW dictionary
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Setup Instructions

### Prerequisites

- Android Studio (latest version recommended)
- Android SDK 24+ (Android 7.0 Nougat)
- Kotlin support enabled

### Building the Project

1. **Open the project in Android Studio**
   ```bash
   # Navigate to project directory
   cd GuardianKeyboard
   ```

2. **Sync Gradle dependencies**
   - Android Studio should automatically sync Gradle files
   - If not, click "Sync Now" or go to File > Sync Project with Gradle Files

3. **Build the APK**
   - Go to Build > Make Project
   - Or use: `./gradlew assembleDebug` from terminal

### Installing and Enabling the Keyboard

1. **Install the app**
   - Build and install the APK on your Android device or emulator
   - Use: `./gradlew installDebug` or install via Android Studio

2. **Grant Permissions**
   - When first launched, the app will request SMS permission
   - Grant SMS permission for alert functionality

3. **Enable Guardian Keyboard**
   - Open the Guardian Keyboard app
   - Tap "Enable Keyboard in Settings" button
   - Or manually go to: Settings > System > Languages & Input > Virtual Keyboard
   - Enable "Guardian Keyboard"
   - Select "Guardian Keyboard" as your default keyboard

4. **Configure Settings**
   - Open the app
   - Enter guardian phone number (e.g., +1234567890)
   - Set **keyword threshold** (default: 10)
   - Set **backend base URL** (default: `http://10.0.2.2:8000/` for emulator)
   - Optionally set PIN for dismissing the blocking overlay
   - Tap "Save Configuration"

## Configuration

### Guardian Phone Number
Enter the phone number (with country code) where SMS alerts should be sent when unsafe content is detected.

### Keyword Threshold
When the on-device keyword score reaches this threshold, the IME triggers the AI pipeline (subject to a cooldown) and calls the backend.

### PIN (Optional)
Set a PIN that must be entered to dismiss the blocking overlay. If left empty, the blocking overlay can be dismissed without PIN.

### Backend Base URL
The FastAPI server base URL (must end with `/` for Retrofit, the app will auto-fix if missing).

See `..\backend\README.md` for backend setup and `/predict` API.

## Notes
- The backend model repo (`mental/mental-bert-base-uncased`) can be **gated**; you may need `HF_TOKEN` set on the backend machine.

```json
{
  "category_name": {
    "words": ["word1", "word2", "phrase"],
    "weight": 2
  }
}
```

- **words**: Array of words/phrases to detect (case-insensitive, partial matching supported)
- **weight**: Score weight added when a match is found

Example:
```json
{
  "pornography": {
    "words": ["sex", "porn", "nude", "xxx"],
    "weight": 2
  },
  "violence": {
    "words": ["kill", "bomb", "stab"],
    "weight": 1
  },
  "selfharm": {
    "words": ["suicide", "die", "end it all"],
    "weight": 3
  }
}
```

## How It Works

1. **Text Input Detection**
   - Keyboard captures all typed characters
   - Words are finalized on space, punctuation, or Enter key
   - Each completed word is checked against the NBOW

2. **Scoring System**
   - Matched words add their category weight to a running score
   - Matching is case-insensitive and supports partial matches
   - Score accumulates during the typing session

3. **Safety Trigger**
   - When running score ≥ threshold:
     - Full-screen blocking overlay appears
     - SMS alert sent to guardian (if configured)
     - Score resets after triggering

4. **Blocking Overlay**
   - Full-screen Activity prevents further typing
   - Shows message: "Unsafe typing detected. Guardian notified."
   - Requires PIN (if configured) to dismiss
   - Can be dismissed by entering correct PIN

## Testing

### Test Scenarios

1. **Basic Detection Test**
   - Type words from NBOW (e.g., "sex", "porn", "kill")
   - Verify score accumulates correctly
   - Confirm blocking triggers at threshold

2. **SMS Alert Test**
   - Configure guardian phone number
   - Trigger blocking by typing unsafe words
   - Verify SMS is received

3. **PIN Protection Test**
   - Set a PIN in settings
   - Trigger blocking overlay
   - Try dismissing with wrong PIN (should fail)
   - Try dismissing with correct PIN (should succeed)

4. **Case Insensitivity Test**
   - Type uppercase versions of NBOW words (e.g., "SEX", "KILL")
   - Verify detection still works

5. **Partial Match Test**
   - Type words containing NBOW entries (e.g., "sexuality", "killer")
   - Verify partial matching works

### Testing Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run tests (if test files are added)
./gradlew test
```

## Permissions

- **BIND_INPUT_METHOD**: Required for custom keyboard functionality
- **SEND_SMS**: Required for sending SMS alerts (runtime permission)
- **SYSTEM_ALERT_WINDOW**: Optional, for overlay functionality (already handled via Activity)

## Troubleshooting

### Keyboard Not Appearing
- Ensure Guardian Keyboard is enabled in Android Settings
- Set it as the default keyboard
- Restart the device if necessary

### SMS Not Sending
- Verify SMS permission is granted
- Check guardian phone number format (include country code)
- Ensure device has SMS capability

### Blocking Overlay Not Showing
- Verify threshold is set correctly
- Check that NBOW words are being typed
- Ensure score is reaching threshold

### Configuration Not Saving
- Check app storage permissions
- Verify JSON format is valid (for NBOW)
- Restart app after saving

## Technical Details

### Detection Algorithm
- **Word Boundary Detection**: Spaces, punctuation, Enter key
- **Matching**: Case-insensitive, supports exact and partial matches
- **Scoring**: Weighted accumulation per category

### Storage
- Configuration: `filesDir/config.json`
- NBOW: `filesDir/nbow.json` (or assets/nbow.json as default)
- Reset flag: `filesDir/reset_score.flag`

### IME Service Lifecycle
- Configuration and NBOW loaded on `onCreateInputView()`
- Score reset flag checked on `onStartInputView()`
- Text processing occurs on key events

## Future Enhancements

- Full keyboard UI implementation (currently uses placeholder view)
- Advanced word boundary detection (handling contractions, apostrophes)
- Configurable reset behavior (session-based vs. persistent scoring)
- Multiple guardian numbers support
- Logging and analytics
- Customizable blocking overlay messages

## License

This project is provided as-is for educational and development purposes.

## Support

For issues or questions, please refer to the Android documentation on:
- [Input Method Service](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [Custom Keyboard Development](https://developer.android.com/guide/topics/text/creating-input-method)

