# Testing Guide for Guardian Keyboard

## Important Notes

### 1. Emulator SMS Testing
**The Android emulator does NOT support real SMS sending by default.** To test SMS functionality:

**Option A: Use a Physical Device**
- SMS will work on a real Android device
- Ensure the device has a SIM card and SMS capability

**Option B: Test SMS on Emulator (Limited)**
- Emulators can simulate SMS but won't actually send
- Check Logcat for SMS logs: `adb logcat | grep GuardianIME`
- Look for messages like "Sending SMS to: +918919641422"

### 2. Keyboard Setup Verification

To ensure Guardian Keyboard is working:

1. **Enable the Keyboard:**
   - Go to Settings > System > Languages & Input > Virtual Keyboard
   - Enable "Guardian Keyboard"
   - Set it as the default keyboard

2. **Verify It's Active:**
   - Open any app (Notes, Messages, etc.)
   - When you tap a text field, Guardian Keyboard should appear
   - You should see "Guardian Keyboard" text in the keyboard area

3. **Check Logs:**
   ```bash
   adb logcat | grep GuardianIME
   ```
   You should see logs when:
   - Keyboard starts: "Keyboard started. Threshold: X, Guardian: +91..."
   - Text is typed: "Text committed: 'word'"
   - Words are processed: "Processing word: 'word'"
   - Matches found: "Match found! Word: 'sex' matches..."
   - Threshold exceeded: "THRESHOLD EXCEEDED! Triggering alert..."

### 3. Testing Steps

1. **Configure Settings:**
   - Open Guardian Keyboard app
   - Enter guardian number: `+918919641422`
   - Set threshold: `1`
   - Set PIN: `1234`
   - Save configuration

2. **Test Word Detection:**
   - Open any app (e.g., Notes app)
   - Type a word from NBOW (e.g., "sex", "porn", "kill")
   - Press space or enter
   - You should see:
     - Logcat shows word processing
     - BlockingActivity appears (full-screen overlay)
     - SMS attempt is logged (even if not sent on emulator)

3. **Test Blocking Overlay:**
   - When threshold is exceeded, BlockingActivity should appear
   - Enter PIN: `1234`
   - Tap "Dismiss"
   - Keyboard should be usable again

### 4. Troubleshooting

**Problem: No blocking overlay appears**
- Check Logcat for errors
- Verify threshold is set to 1
- Ensure you typed a word from NBOW (check `assets/nbow.json`)
- Make sure Guardian Keyboard is the active keyboard

**Problem: SMS not sending**
- Check SMS permission is granted
- On emulator: SMS won't actually send (check logs instead)
- On physical device: Ensure SIM card is inserted
- Check Logcat for permission errors

**Problem: Words not detected**
- Check Logcat: Look for "Processing word" messages
- Verify NBOW is loaded correctly
- Check if word matches exactly (case-insensitive)
- Try typing exact words from NBOW: "sex", "porn", "kill", "die"

**Problem: Keyboard not appearing**
- Ensure Guardian Keyboard is enabled in Settings
- Set it as default keyboard
- Restart the app you're typing in
- Check if other keyboards are interfering

### 5. Expected Logcat Output

When typing "sex" with threshold=1, you should see:
```
D/GuardianIME: Keyboard started. Threshold: 1, Guardian: +918919641422
D/GuardianIME: Text committed: 's'
D/GuardianIME: Text committed: 'e'
D/GuardianIME: Text committed: 'x'
D/GuardianIME: Text committed: ' '
D/GuardianIME: Processing word (from commit): 'sex'
D/GuardianIME: Checking word: 'sex' against NBOW
D/GuardianIME: Match found! Word: 'sex' matches 'sex' in category 'pornography' (weight: 2)
D/GuardianIME: Score updated: 2/1 (added 2)
W/GuardianIME: THRESHOLD EXCEEDED! Triggering alert...
W/GuardianIME: TRIGGERING SAFETY ALERT!
D/GuardianIME: BlockingActivity started
D/GuardianIME: Sending SMS to: +918919641422
D/GuardianIME: Message: Alert: unsafe typing detected. Score: 2
```

### 6. Testing on Emulator vs Physical Device

**Emulator:**
- ✅ Word detection works
- ✅ Blocking overlay works
- ✅ Logging works
- ❌ SMS won't actually send (logs will show attempt)

**Physical Device:**
- ✅ Everything works including real SMS

### 7. Quick Test Words

Use these words from the default NBOW to test:
- "sex" (weight: 2)
- "porn" (weight: 2)
- "kill" (weight: 1)
- "die" (weight: 3)
- "suicide" (weight: 3)

With threshold=1, typing any of these should trigger the alert immediately.

