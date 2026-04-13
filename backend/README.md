# CogniSafe v2 Backend (FastAPI)

## What it does
- Exposes `POST /predict`
- Loads **MentalBERT base** (`mental/mental-bert-base-uncased`) and injects your existing `best_model.pt` weights **as-is**
- Runs CPU inference and returns softmax probabilities for 6 labels

## Files
- `app.py`: FastAPI app
- `requirements.txt`: dependencies

## Setup (Windows)
From `C:\Users\KBhagyaRekha\Desktop\Cognisafe V2\backend`:

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## Run
Your checkpoint lives at:
- `C:\Users\KBhagyaRekha\Desktop\Cognisafe V2\Minor\best_model.pt`

Run the server:

```bash
set MODEL_PT_PATH=C:\Users\KBhagyaRekha\Desktop\Cognisafe V2\Minor\best_model.pt
uvicorn app:app --host 0.0.0.0 --port 8000
```

### HuggingFace access (important)
`mental/mental-bert-base-uncased` is a **restricted / gated** HuggingFace repo in many environments.

If you get a `403 Forbidden` when starting the server, you must set a HuggingFace token that has been granted access:

```bash
set HF_TOKEN=YOUR_TOKEN_HERE
```

## Test
```bash
curl -X POST http://127.0.0.1:8000/predict ^
  -H "Content-Type: application/json" ^
  -d "{\"text\":\"User recent thoughts: I'm so exhausted and nothing brings me joy.\"}"
```

