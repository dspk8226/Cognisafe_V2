import os
from typing import List, Optional

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoModelForSequenceClassification, AutoTokenizer


MODEL_NAME = os.getenv("MODEL_NAME", "mental/mental-bert-base-uncased")
MODEL_PT_PATH = os.getenv(
    "MODEL_PT_PATH",
    os.path.join(os.path.dirname(__file__), "..", "Minor", "best_model.pt"),
)

# Labels in alphabetical order (from your notebook / training)
LABELS: List[str] = [
    "anxiety",
    "bpd",
    "bipolar",
    "depression",
    "mentalillness",
    "schizophrenia",
]


class PredictRequest(BaseModel):
    text: str = Field(..., min_length=1, description="Input text formatted for the model.")


class PredictResponse(BaseModel):
    labels: List[str]
    probabilities: List[float]


app = FastAPI(title="CogniSafe v2 Backend", version="1.0.0")

_tokenizer: Optional[AutoTokenizer] = None
_model: Optional[AutoModelForSequenceClassification] = None
_device = torch.device("cpu")


def _load_checkpoint(path: str):
    # Some torch versions support weights_only; keep a safe fallback.
    try:
        return torch.load(path, map_location=_device, weights_only=True)
    except TypeError:
        return torch.load(path, map_location=_device)


@app.on_event("startup")
def _startup_load_model():
    global _tokenizer, _model

    hf_token = os.getenv("HF_TOKEN")  # optional (do NOT hardcode tokens)

    _tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, token=hf_token)
    _model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_NAME, num_labels=len(LABELS), token=hf_token
    )

    if not os.path.isfile(MODEL_PT_PATH):
        raise RuntimeError(
            f"Model checkpoint not found at MODEL_PT_PATH='{MODEL_PT_PATH}'. "
            f"Set MODEL_PT_PATH env var to your best_model.pt."
        )

    checkpoint = _load_checkpoint(MODEL_PT_PATH)
    state_dict = checkpoint.get("model_state_dict", checkpoint)
    _model.load_state_dict(state_dict, strict=False)
    _model.to(_device)
    _model.eval()


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    if _tokenizer is None or _model is None:
        raise HTTPException(status_code=503, detail="Model not loaded yet.")

    text = req.text
    if not text.startswith("User recent thoughts: "):
        # Enforce the exact prefix contract to avoid subtle mismatch.
        raise HTTPException(
            status_code=400,
            detail="Text must start with: 'User recent thoughts: '",
        )

    inputs = _tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        max_length=512,
        padding="max_length",
    )
    inputs = {k: v.to(_device) for k, v in inputs.items()}

    with torch.no_grad():
        outputs = _model(**inputs)
        logits = outputs.logits
        probs = torch.nn.functional.softmax(logits, dim=1).squeeze(0).cpu().tolist()

    if len(probs) != len(LABELS):
        raise HTTPException(
            status_code=500,
            detail=f"Model returned {len(probs)} probs, expected {len(LABELS)}.",
        )

    return PredictResponse(labels=LABELS, probabilities=[float(p) for p in probs])

