#!/usr/bin/env python3
"""
抽取人脸 / 体态 512 维 embedding，输出 JSON。

示例:
  python embed_features.py --kind face --image E:/nwueyes/face_library/face_test.jpg
  python embed_features.py --kind body --image E:/nwueyes/body_library/body_test.jpg
  python embed_features.py --kind both --face-image face.jpg --body-image body.jpg --output out.json
"""

from __future__ import annotations

import argparse
import sys

from body_embedder import embed_body_image
from embedding_io import (
    dump_result,
    error_payload,
    main_fail,
    read_image_bgr,
    success_payload,
    validate_dim,
)
from face_embedder import embed_face_image


def parse_args():
    parser = argparse.ArgumentParser(description="抽取 face/body 512 维 embedding")
    parser.add_argument("--kind", choices=["face", "body", "both"], required=True)
    parser.add_argument("--image", default="", help="face 或 body 单图路径")
    parser.add_argument("--face-image", default="", help="kind=both 时的人脸图")
    parser.add_argument("--body-image", default="", help="kind=both 时的体态图")
    parser.add_argument("--output", default="", help="可选，写入 JSON 文件；默认 stdout")
    parser.add_argument("--face-model", default="buffalo_l", help="InsightFace 模型包")
    parser.add_argument("--body-model", default="osnet_x0_25", help="ReID 模型")
    parser.add_argument("--min-det-score", type=float, default=0.45, help="人脸检测最低置信度")
    parser.add_argument(
        "--face-mode",
        choices=["crop", "detect"],
        default="crop",
        help="crop=识别-only(默认，适配 YOLO 人脸 crop)；detect=检测+识别",
    )
    parser.add_argument("--face-model-path", default="", help="w600k_r50.onnx 路径，默认自动查找")
    return parser.parse_args()


def run_face(image_path: str, face_model: str, min_det_score: float, face_mode: str, face_model_path: str):
    img = read_image_bgr(image_path)
    vec, quality = embed_face_image(
        img,
        model_name=face_model,
        min_det_score=min_det_score,
        mode=face_mode,
        model_path=face_model_path,
    )
    vec = validate_dim(vec)
    model_label = f"insightface-arcface-512"
    if face_mode == "detect":
        model_label = f"insightface-{face_model}"
    return success_payload("face", image_path, vec, model=model_label, quality=quality)


def run_body(image_path: str, body_model: str):
    img = read_image_bgr(image_path)
    vec, quality = embed_body_image(img, model_name=body_model)
    vec = validate_dim(vec)
    return success_payload("body", image_path, vec, model=f"torchreid-{body_model}", quality=quality)


def main():
    args = parse_args()
    try:
        if args.kind == "face":
            if not args.image:
                main_fail("--image 不能为空")
            payload = run_face(args.image, args.face_model, args.min_det_score, args.face_mode, args.face_model_path)
        elif args.kind == "body":
            if not args.image:
                main_fail("--image 不能为空")
            payload = run_body(args.image, args.body_model)
        else:
            if not args.face_image or not args.body_image:
                main_fail("--kind both 需要 --face-image 和 --body-image")
            face_payload = run_face(
                args.face_image, args.face_model, args.min_det_score, args.face_mode, args.face_model_path
            )
            body_payload = run_body(args.body_image, args.body_model)
            if not face_payload.get("ok") or not body_payload.get("ok"):
                payload = {
                    "ok": False,
                    "kind": "both",
                    "face": face_payload,
                    "body": body_payload,
                    "error": "face 或 body 抽取失败",
                }
            else:
                payload = {
                    "ok": True,
                    "kind": "both",
                    "dim": 512,
                    "face": face_payload,
                    "body": body_payload,
                    "error": None,
                }
        dump_result(payload, args.output or None)
        if not payload.get("ok", False):
            sys.exit(2)
    except Exception as ex:
        image_path = args.image or args.face_image or args.body_image
        payload = error_payload(args.kind, image_path, str(ex), model="")
        dump_result(payload, args.output or None)
        print(str(ex), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
