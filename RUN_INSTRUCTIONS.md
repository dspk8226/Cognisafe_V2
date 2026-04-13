## CogniSafe v2 (End-to-End) Run Instructions

### 1) Start the backend (FastAPI)
Open PowerShell.

```powershell
cd "C:\Users\KBhagyaRekha\Desktop\Cognisafe V2\backend"
pip install -r requirements.txt

$env:MODEL_PT_PATH="C:\Users\KBhagyaRekha\Desktop\Cognisafe V2\Minor\best_model.pt"
# If you get 403 (gated model), you must set a HuggingFace token with access:
# $env:HF_TOKEN="YOUR_TOKEN"

uvicorn app:app --host 0.0.0.0 --port 8000
```

Quick test:

```powershell
curl -Method POST "http://127.0.0.1:8000/predict" `
  -ContentType "application/json" `
  -Body '{"text":"User recent thoughts: I feel hopeless and exhausted. Nothing brings me joy."}'
```

### 2) Run the Android keyboard (IME)
1. Open Android Studio and open the folder:
   - `C:\Users\KBhagyaRekha\Desktop\Cognisafe V2\Cognisafe`
2. Ensure Android SDK is configured (Android Studio sets `local.properties` automatically).
3. Run the app on an emulator or device.

### 3) Enable the keyboard
1. Open the app (launcher icon)
2. Tap **Open Keyboard Settings** and enable **Guardian Keyboard**
3. Tap **Switch to Guardian Keyboard Now**

### 4) Configure settings in the app
In the app settings screen:
- **Guardian phone number**: used for CRITICAL SMS alerts (optional)
- **Keyword threshold**: default `10`
- **Backend base URL**:
  - Emulator (backend running on your PC): `http://10.0.2.2:8000/`
  - Real device on Wi‑Fi: `http://<your-PC-LAN-IP>:8000/` (example: `http://192.168.1.25:8000/`)
- **PIN**: optional, required to dismiss the overlay

### 5) Verify end-to-end behavior
Type in any app using the keyboard:
- Keyword detector runs instantly (e.g. “kill myself”, “hopeless”, “sad”)
- When keyword score crosses threshold, the IME sends the rolling context:
  - `User recent thoughts: ...`
  to the backend `/predict` (throttled)
- Decision engine computes:
  - `risk_score = 0.4 * keyword_score_norm + 0.6 * model_max_probability`
- Interventions:
  - MEDIUM: suggestion popup
  - HIGH: warning overlay
  - CRITICAL: warning overlay + SMS (if guardian configured and SMS permission granted)

