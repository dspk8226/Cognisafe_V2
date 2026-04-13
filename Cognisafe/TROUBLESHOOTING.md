# Troubleshooting: No GuardianIME Logs

## Problem
You're not seeing any `GuardianIME` logs in Logcat, which means the IME service isn't being invoked.

## Root Cause
Guardian Keyboard service isn't being activated. This happens when:
1. Guardian Keyboard isn't enabled in Android Settings
2. Guardian Keyboard isn't set as the default keyboard
3. Another keyboard is being used instead

## Solution Steps

### Step 1: Verify Keyboard is Enabled

1. **Open Android Settings**
   - Go to **Settings** > **System** > **Languages & Input** > **Virtual Keyboard**
   - Or: **Settings** > **System** > **Keyboard** > **On-screen keyboard**

2. **Enable Guardian Keyboard**
   - Look for "Guardian Keyboard" in the list
   - Toggle it **ON**
   - If you see a warning about "This input method may be able to collect all the text you type", tap **OK**

3. **Set as Default Keyboard**
   - After enabling, go back to **Languages & Input**
   - Tap **Default keyboard** or **Current keyboard**
   - Select **Guardian Keyboard**

### Step 2: Verify Service is Running

After enabling, check Logcat:
```bash
adb logcat | grep GuardianIME
```

You should see:
```
D/GuardianIME: ========== GuardianImeService.onCreate() ==========
D/GuardianIME: ========== onCreateInputView called - keyboard view being created ==========
D/GuardianIME: Loaded config - Threshold: 1, Guardian: +918919641422
```

### Step 3: Test Keyboard Activation

1. **Open any app** (Notes, Messages, etc.)
2. **Tap a text field** to bring up the keyboard
3. **Check Logcat** - you should see:
   ```
   D/GuardianIME: ========== onStartInputView - Keyboard started ==========
   D/GuardianIME: Threshold: 1, Guardian: +918919641422
   ```

### Step 4: Test Typing

1. **Type a word** from NBOW (e.g., "sex", "kill", "die")
2. **Press space**
3. **Check Logcat** - you should see:
   ```
   D/GuardianIME: onKeyDown called: keyCode=62, unicodeChar=115, char=s
   D/GuardianIME: onKeyDown called: keyCode=33, unicodeChar=101, char=e
   D/GuardianIME: onKeyDown called: keyCode=54, unicodeChar=120, char=x
   D/GuardianIME: Processing word: 'sex'
   D/GuardianIME: Match found! Word: 'sex' matches 'sex' in category 'pornography' (weight: 2)
   D/GuardianIME: Score updated: 2/1 (added 2)
   W/GuardianIME: THRESHOLD EXCEEDED! Triggering alert...
   ```

## Important Notes

### System Keyboard Fallback
If Android shows the **system keyboard** instead of Guardian Keyboard:
- Guardian Keyboard might not be fully enabled
- The placeholder keyboard view might not be sufficient
- Android might be using the default keyboard

**Solution**: Ensure Guardian Keyboard is set as default and restart the app you're typing in.

### Keyboard View Issue
Our current implementation uses a **placeholder keyboard view**. This means:
- If Guardian Keyboard is active, `onKeyDown` will be called
- If system keyboard is used, `onKeyDown` won't be called

**To verify which keyboard is active:**
- Look at the keyboard that appears when you tap a text field
- If you see "Guardian Keyboard" text, our keyboard is active
- If you see the standard Android keyboard, Guardian Keyboard isn't active

### Manual Verification

1. **Check if service exists:**
   ```bash
   adb shell dumpsys input_method | grep Guardian
   ```
   Should show Guardian Keyboard in the list

2. **Check current IME:**
   ```bash
   adb shell settings get secure default_input_method
   ```
   Should show: `com.guardian.keyboard/.GuardianImeService`

3. **Force enable (if needed):**
   ```bash
   adb shell ime enable com.guardian.keyboard/.GuardianImeService
   adb shell ime set com.guardian.keyboard/.GuardianImeService
   ```

## Expected Logcat Output

When everything works correctly, you should see:

```
D/GuardianIME: ========== GuardianImeService.onCreate() ==========
D/GuardianIME: ========== onCreateInputView called - keyboard view being created ==========
D/GuardianIME: Loaded config - Threshold: 1, Guardian: +918919641422
D/GuardianIME: NBOW loaded: pornography, violence, selfharm
D/GuardianIME: Input view created successfully
D/GuardianIME: ========== onStartInputView - Keyboard started ==========
D/GuardianIME: Threshold: 1, Guardian: +918919641422
D/GuardianIME: Current score: 0
D/GuardianIME: onKeyDown called: keyCode=62, unicodeChar=115, char=s
D/GuardianIME: onKeyDown called: keyCode=33, unicodeChar=101, char=e
D/GuardianIME: onKeyDown called: keyCode=54, unicodeChar=120, char=x
D/GuardianIME: onKeyDown called: keyCode=62, unicodeChar=32, char= 
D/GuardianIME: Processing word: 'sex'
D/GuardianIME: Checking word: 'sex' against NBOW
D/GuardianIME: Match found! Word: 'sex' matches 'sex' in category 'pornography' (weight: 2)
D/GuardianIME: Score updated: 2/1 (added 2)
W/GuardianIME: THRESHOLD EXCEEDED! Triggering alert...
W/GuardianIME: ========== TRIGGERING SAFETY ALERT! ==========
D/GuardianIME: BlockingActivity started
D/GuardianIME: Sending SMS to: +918919641422
D/GuardianIME: Message: Alert: unsafe typing detected. Score: 2
```

## Next Steps

If you still don't see logs:
1. Rebuild and reinstall the app
2. Restart the device/emulator
3. Check AndroidManifest.xml to ensure the service is properly declared
4. Verify the package name matches: `com.guardian.keyboard`

