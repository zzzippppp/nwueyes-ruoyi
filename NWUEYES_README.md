# nwueyes — 门口人员识别与停留管理

基于 **若依 RuoYi-Vue3 + Spring Boot + PostgreSQL(pgvector) + Python(YOLO/InsightFace)** 的单门单摄像头 MVP：萤石拉流/录像回放 → 过线检测 → 抓拍入库 → 行为日志与停留会话。

> 后端仓库：`ruoyi/` · 前端仓库：`RuoYi-Vue3/`（两个独立 Git 目录，clone 后放在同一父目录下即可）

---

## 项目是干什么的

| 能力 | 说明 |
|------|------|
| **实时监控** | 萤石公网 FLV / 局域网 RTSP，YOLOv8 + ByteTrack 检测人员 |
| **过线判定** | 轨迹线段穿门槛线 + 方向（向下=进门，向上=出门） |
| **行为日志** | 每次进/出门一条流水，含人脸/体态证据图（`log_library`） |
| **停留会话** | 进门开 session；出门用 **exit 体态向量** 与 `enter_body_embedding` 比对，**≥ bodyMatchThreshold 才关 session** |
| **进门抓拍** | 穿线后 **持续追脸**（`enterFaceHuntMaxSec`），检出后再择优；无脸可记 enter 但不建陌生人档案 |
| **数据看板** | 停留记录、人员档案、陌生人研判、点位管理 |
| **视频测试** | 上传 MP4 离线分析，可写入行为日志（不走真人反复进出） |

### 数据流（简图）

```
摄像头/录像 → Python Worker(YOLO+过线) → HTTP Ingest → Java 入库
                ↓ 抓拍 JPG              ↓              ├ behavior_logs
                log_library/            向量匹配       └ presence_sessions + persons
```

### 存储目录（`storageRoot`，默认 `./data`）

```
data/
├── uploadPath/              # 若依上传、测试视频
├── log_library/face|body/   # 行为日志证据图（按日期）
├── face_library/            # 人脸档案（匹配库）
└── body_library/            # 体态档案
```

---

## 技术栈

- **Java**：Spring Boot 3、MyBatis、JWT
- **DB**：PostgreSQL 15+，扩展 `vector`（pgvector）
- **缓存**：Redis
- **前端**：Vue 3 + Element Plus + Vite
- **Python 3.10/3.11**：ultralytics、OpenCV、InsightFace、torchreid

---

## 从 GitHub 拿下来后怎么启动

### 0. 目录结构

```text
your-workspace/
├── ruoyi/           # 本仓库后端
└── RuoYi-Vue3/      # 本仓库前端（与 ruoyi 同级）
```

### 1. 环境准备

| 依赖 | 版本建议 |
|------|----------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| PostgreSQL | 15+（需能安装 `vector` 扩展） |
| Redis | 6+ |
| Python | 3.10 或 3.11 |

### 2. 初始化数据库

```bash
# 创建库
createdb nwueyes

# 按顺序执行（路径在 sql/）
psql -d nwueyes -f sql/ry_20260417.sql
psql -d nwueyes -f sql/migration/001_core_business.sql
psql -d nwueyes -f sql/behavior_log.sql
# 002 仅旧库升级需要，新装可跳过
```

初始化后默认管理员：**`admin` / `admin123`**（登录后请修改密码）。

需在 `locations` 表配置至少一个监控点位（id=1），可在「数据看板 → 监控信息」维护，或自行 INSERT。

### 3. Python 虚拟环境（必做，venv 不随仓库分发）

```bash
cd ruoyi/scripts
python -m venv .venv

# Windows
.venv\Scripts\activate
# Linux/macOS
source .venv/bin/activate

pip install -U pip
pip install -r requirements.txt
pip install -r requirements-embedding.txt
```

首次运行 YOLO / InsightFace 会自动下载模型（`yolov8n.pt`、`~/.insightface/models/`），需联网。

自检：

```bash
python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"
python embed_features.py --kind face --image /path/to/any/face.jpg
```

### 4. 后端配置（不要提交私密文件）

```bash
cd ruoyi/ruoyi-admin/src/main/resources
cp application-local.example.yml application-local.yml   # Windows 用 copy
```

编辑 `application-local.yml`，至少填写：

- `ezviz.appKey` / `appSecret`（[萤石开放平台](https://open.ys7.com)）
- `presence.ingest.apiKey`（自定义，与 Python worker 一致）
- `presence.ingest.replayPythonCommand` → 指向 **上一步 venv 的 python**
- `ruoyi.profile`、`storageRoot` → 本机可写目录（如 `./data`）
- `replayLineY` / `replayRoi` → 门区标定（1920×1080 参考）

> **`application-local.yml` 已在 `.gitignore` 中，只存在于本机，不会影响其他开发者。**

数据库连接默认 `localhost:5432/nwueyes`（见 `application-druid.yml`），可通过环境变量覆盖：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/nwueyes
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=你的密码
```

完整变量清单见 `ruoyi/.env.example`。

### 5. 启动 Redis

```bash
redis-server
```

### 6. 启动后端

```bash
cd ruoyi
mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
# 或在 IDE 中运行 RuoYiApplication
```

默认端口：**8080**

### 7. 启动前端

```bash
cd RuoYi-Vue3
npm install
npm run dev
```

浏览器访问控制台输出的地址（默认 **http://localhost:80**），使用 `admin/admin123` 登录。

开发模式下 `/dev-api` 会代理到 `http://localhost:8080`。

### 8. 开始识别（拉流）

1. 菜单进入 **监控大屏**，选择设备
2. 拉流模式选 **局域网 RTSP**（识别用，画质高）；预览仍可走萤石云 FLV
3. 点击 **开始识别**（可只开识别不预览）
4. 日志中应出现 `size=2880x1620`（RTSP）、`[cross] confirmed`、`[capture-hunt]`、`async ingest ok`
5. **行为日志**页选当天日期并 **刷新**（不自动刷）
6. **数据看板** 查看停留记录 / 人员档案

门线标定：Worker 启动时会写 `log_library/_probe.jpg`（带 ROI/过线），对照后改 `replayLineY` / `replayRoi`。

---

## 服务器部署要点

1. **数据盘**：将 `NWUEYES_DATA_DIR` / `PRESENCE_STORAGE_ROOT` 指到持久化目录（如 `/data/nwueyes`）
2. **Python**：在服务器上单独建 venv，**不要**从 Windows 拷贝 venv
3. **Nginx**：前端 `npm run build:prod` 后托管 `dist/`；`/prod-api` 反代 Java 8080
4. **安全**：修改 `TOKEN_SECRET`、数据库密码、ingest `apiKey`；生产关闭 Swagger
5. **萤石**：设备编码 H.264；公网 FLV 首帧可能较慢，可配置 `lanRtspUrl`

---

## 业务规则摘要

| 场景 | 行为 |
|------|------|
| 进门 | 轨迹 **穿门槛线** 触发；**只认人脸**匹配/建陌生人；进门 session 写入 `enter_body_embedding`（有 body 图且 embed 成功时） |
| 进门抓拍 | 穿线后 **追脸窗口**（默认最长 10s + 1.5s 择优），非固定 2.5s 即入库 |
| 重复进门（同 track / 同人人脸已有 open session） | 写行为日志，不新建 session |
| 出门 | **exit 体态向量** vs 各 open session 的 `enter_body_embedding`，**相似度 ≥ bodyMatchThreshold（默认 0.50）** 才关 session；**不用 trackKey 兜底** |
| 出门未过阈值 / 无 body 向量 | 写 exit 行为日志（orphan），**不关 session** |
| 多人先后进门 | 不再因「已有人在场」静默第二人；仍防 **门内深处首检换 ID** 假进门 |
| 陌生人研判转熟人 | **合并已有档案**，不新建空人员 |

关键配置（`application-local.yml` → `presence.ingest.live`）：

```yaml
enterFaceHuntMaxSec: 10    # 进门追脸最长等待
enterFaceGraceSec: 1.5     # 首次检出脸后继续择优
bodyMatchThreshold: 0.50   # 出门体态匹配阈值
lanRtspUrl: rtsp://...     # 局域网识别主码流（推荐）
```

---

## 常见问题

**Q: clone 后没有 `application-local.yml`？**  
A: 正常。从 `application-local.example.yml` 复制一份。

**Q: 识别无日志？**  
A: 查 Worker `[cross] confirmed`、Java `async ingest`；多人场景看是否 `[gate] silent-inside`；确认选 RTSP 且 `ingest.apiKey` 一致。

**Q: 出了门还在「在场中」？**  
A: 出门需 **exit 体态向量** 与进门 session 的 `enter_body_embedding` 过阈值；track 换了 id 也能关，但 **进门须有 body 向量**。查 `presence_sessions.enter_body_embedding IS NOT NULL`。

**Q: 抓拍图很糊？**  
A: 拉流可能是 2K，但入库是 **YOLO 人框裁剪**，远景人只占几十像素就会糊；尽量让人在门区占画面更大。

**Q: `yolov8n.pt` 在哪？**  
A: 不提交 Git，首次跑 YOLO 自动下载。

**Q: 两个 Git 仓库怎么开源？**  
A: 可建两个 GitHub repo，或合并为 monorepo；README 保持同级目录结构说明即可。

---

## 基于若依

后端基于 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue) 二次开发，遵循原项目 MIT 协议。业务扩展：行为日志、停留会话、萤石拉流、Python 识别流水线等。

---

## License

MIT（与 RuoYi 一致）；YOLO、InsightFace 等第三方模型请遵循各自许可证。
