import io
import os
import time
from typing import List

import torch
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from torchvision import transforms

import config
from models.mobilenet_v2 import get_model
from leaf_presence import predict_leaf_presence  # 新增：叶片存在性预测
from utils.misc import safe_torch_load


app = FastAPI(title="Plant Disease FastAPI", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


def ensure_dual_class_names() -> tuple[list, list]:
    import json
    # Species
    if getattr(config, "SPECIES_CLASS_NAMES", None) is None:
        if os.path.isfile(config.SPECIES_CLASS_NAMES_JSON):
            with open(config.SPECIES_CLASS_NAMES_JSON, "r", encoding="utf-8") as f:
                config.SPECIES_CLASS_NAMES = json.load(f)
        else:
            from torchvision import datasets
            ds = datasets.ImageFolder(root=config.DATA_ROOT)
            config.SPECIES_CLASS_NAMES = sorted({c.split("___")[0] if "___" in c else c for c in ds.classes})
    # Disease
    if getattr(config, "DISEASE_CLASS_NAMES", None) is None:
        if os.path.isfile(config.DISEASE_CLASS_NAMES_JSON):
            with open(config.DISEASE_CLASS_NAMES_JSON, "r", encoding="utf-8") as f:
                config.DISEASE_CLASS_NAMES = json.load(f)
        else:
            from torchvision import datasets
            ds = datasets.ImageFolder(root=config.DATA_ROOT)
            config.DISEASE_CLASS_NAMES = sorted({(c.split("___")[1] if "___" in c else c) for c in ds.classes})
    return config.SPECIES_CLASS_NAMES, config.DISEASE_CLASS_NAMES


# Lazy init for dual models
SPECIES_CLASS_NAMES, DISEASE_CLASS_NAMES = ensure_dual_class_names()

SPECIES_MODEL = get_model(num_classes=len(SPECIES_CLASS_NAMES))
DISEASE_MODEL = get_model(num_classes=len(DISEASE_CLASS_NAMES))

if not os.path.isfile(config.SPECIES_MODEL_SAVE_PATH):
    raise RuntimeError(f"未找到物种模型权重: {config.SPECIES_MODEL_SAVE_PATH}")
if not os.path.isfile(config.DISEASE_MODEL_SAVE_PATH):
    raise RuntimeError(f"未找到病害模型权重: {config.DISEASE_MODEL_SAVE_PATH}")

SPECIES_STATE = safe_torch_load(
    config.SPECIES_MODEL_SAVE_PATH,
    map_location=config.DEVICE,
)
DISEASE_STATE = safe_torch_load(
    config.DISEASE_MODEL_SAVE_PATH,
    map_location=config.DEVICE,
)

SPECIES_MODEL.load_state_dict(SPECIES_STATE)
DISEASE_MODEL.load_state_dict(DISEASE_STATE)

SPECIES_MODEL.eval()
DISEASE_MODEL.eval()

TRANSFORM = transforms.Compose([
    transforms.Resize(int(config.IMAGE_SIZE * 1.14)),
    transforms.CenterCrop(config.IMAGE_SIZE),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])


@app.get("/health")
def health():
    return {
        "status": "ok",
        "speciesClasses": len(SPECIES_CLASS_NAMES),
        "diseaseClasses": len(DISEASE_CLASS_NAMES),
        "device": str(config.DEVICE),
        "models_loaded": True,
    }


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    try:
        data = await file.read()
        if not data:
            raise HTTPException(status_code=400, detail="空文件")
        img = Image.open(io.BytesIO(data)).convert("RGB")

        # 阶段1：叶片存在性拦截（双阈值）
        no_leaf_prob, leaf_prob = predict_leaf_presence(img)
        if no_leaf_prob >= config.LEAF_PRESENCE_NO_LEAF_THRESHOLD:
            # 强拦截：纯背景
            return {
                "predictedClass": "Background_without_leaves",
                "confidence": float(no_leaf_prob),
                "probabilities": [
                    {"class": "Background_without_leaves", "prob": float(no_leaf_prob)},
                    {"class": "Leaf_present", "prob": float(leaf_prob)},
                ],
                "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
                "gate": "no_leaf_strict",
            }
        if no_leaf_prob >= config.LEAF_PRESENCE_THRESHOLD:
            # 普通拦截：疑似背景，提示重拍
            return {
                "predictedClass": "Suspected_background_no_leaf",
                "confidence": float(no_leaf_prob),
                "probabilities": [
                    {"class": "Background_without_leaves", "prob": float(no_leaf_prob)},
                    {"class": "Leaf_present", "prob": float(leaf_prob)},
                ],
                "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
                "gate": "no_leaf_soft",
            }

        # 阶段2：物种+病害分类（双模型）
        x = TRANSFORM(img).unsqueeze(0).to(config.DEVICE)
        with torch.no_grad():
            # species
            s_logits = SPECIES_MODEL(x)
            s_probs = torch.softmax(s_logits, dim=1)[0]
            s_conf, s_idx = torch.max(s_probs, dim=0)
            # disease
            d_logits = DISEASE_MODEL(x)
            d_probs = torch.softmax(d_logits, dim=1)[0]
            d_conf, d_idx = torch.max(d_probs, dim=0)

        species = SPECIES_CLASS_NAMES[s_idx.item()]
        disease = DISEASE_CLASS_NAMES[d_idx.item()]
        s_conf = float(s_conf.item())
        d_conf = float(d_conf.item())

        # Top-5 lists
        s_topk = torch.topk(s_probs, k=min(5, len(SPECIES_CLASS_NAMES)))
        d_topk = torch.topk(d_probs, k=min(5, len(DISEASE_CLASS_NAMES)))
        s_top5 = [{"class": SPECIES_CLASS_NAMES[i.item()], "prob": float(p.item())} for p, i in zip(s_topk.values, s_topk.indices)]
        d_top5 = [{"class": DISEASE_CLASS_NAMES[i.item()], "prob": float(p.item())} for p, i in zip(d_topk.values, d_topk.indices)]

        return {
            "species": {"predicted": species, "confidence": s_conf, "top5": s_top5},
            "disease": {"predicted": disease, "confidence": d_conf, "top5": d_top5},
            "createdAt": time.strftime("%Y-%m-%d %H:%M:%S"),
            "gate": "dual_classification",
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"预测失败: {str(e)}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api_server:app", host="0.0.0.0", port=8001, reload=False)


