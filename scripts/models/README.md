# 识别模型权重说明

本目录存放 **随仓库提交** 的机器学习权重；其余模型由首次运行时自动下载或需按下方说明手动放置。

工作目录约定：Java 启动 Python 子进程时 `cwd` 为 **`ruoyi/`**（即 `workspaceRoot`），YOLO 默认在当前目录或 ultralytics 缓存中查找 `yolov8n.pt`。

---

## 仓库内文件（方案 A：已提交 Git）

| 文件 | 大小（约） | 用途 | 配置项 |
|------|-----------|------|--------|
| `osnet_x0_25_imagenet.pth` | 2.8 MB | OSNet x0.25 在 ImageNet 上的预训练权重，供 **体态 ReID**（`bodyEmbedModel: osnet_x0_25`） | `presence.ingest.bodyEmbedModel` |

当前 `body_embedder.py` 通过 **torchreid** 的 `pretrained=True` 加载 OSNet；联网环境下会自动下载到用户目录。本文件作为 **离线/内网部署备份**，与线上下载权重等价，便于无法访问外网时手动拷贝到 torchreid 缓存或后续扩展本地加载逻辑。

---

## 不在仓库内、运行时自动获取

| 模型 | 用途 | 获取方式 |
|------|------|----------|
| **`yolov8n.pt`** | YOLOv8 人体检测（直播/回放/视频分析） | 首次运行 `live_stream_worker_yolo.py` 等时 **ultralytics 自动下载**；已被根目录 `.gitignore` 的 `*.pt` 忽略，**勿提交** |
| **InsightFace `buffalo_l`** | 人脸检测 + 512 维向量 | 安装 `insightface` 后首次调用 `FaceAnalysis(name='buffalo_l')` 下载到 `~/.insightface/models/buffalo_l/` |
| **`w600k_r50.onnx`** | ArcFace 识别（crop 模式，适配 YOLO 人脸小图） | 随 `buffalo_l` 包下载；或放到 `{storageRoot}/models/w600k_r50.onnx`（见 `face_embedder.py`） |

### YOLO 离线准备

若服务器无法访问 GitHub / ultralytics CDN：

```bash
# 在能联网的机器上下载后拷贝到 ruoyi/ 目录
python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"
# 默认缓存 ~/.config/Ultralytics/ 或当前工作目录下的 yolov8n.pt
```

### 人脸模型离线准备

```bash
pip install -r ruoyi/scripts/requirements-embedding.txt
python -c "from insightface.app import FaceAnalysis; FaceAnalysis(name='buffalo_l').prepare(ctx_id=-1)"
# 或将 w600k_r50.onnx 复制到 E:/nwueyes/models/（storageRoot 下 models/）
```

---

## 与 `application-local.yml` 的对应关系

```yaml
presence:
  ingest:
    faceEmbedModel: buffalo_l      # InsightFace 模型包名
    bodyEmbedModel: osnet_x0_25    # 体态 ReID，见本目录 .pth
    faceMinDetScore: 0.45
    faceMatchThreshold: 0.45
    bodyMatchThreshold: 0.50
```

直播/回放 Worker 默认参数 `--model yolov8n.pt`（与上表一致）。

---

## 依赖安装

```bash
pip install -r ruoyi/scripts/requirements.txt
pip install -r ruoyi/scripts/requirements-embedding.txt
```

验证：

```bash
cd ruoyi
python scripts/embed_features.py --kind face --image ../log_library/_probe_raw.jpg
python scripts/embed_features.py --kind body --image ../log_library/_probe_raw.jpg
```

---

## 不要提交的内容

- `*.pt`（含 `yolov8n.pt`）— 已在 `ruoyi/.gitignore`
- `~/.insightface/`、`~/.cache/torch/` 等运行时缓存
- `log_library/`、`face_library/`、`body_library/` 抓拍与档案图
