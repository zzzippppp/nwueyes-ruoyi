# nwueyes 后端（ruoyi）

门口识别与考勤 Java + Python 子进程。前端仓库：[nwueyes-vue3](https://github.com/zzzippppp/nwueyes-vue3)（分支 **`local-ruoyi-vue3`**）。总览文档见上级 [nwueyes](https://github.com/zzzippppp/nwueyes) README。

**推荐分支：`local-ruoyi`**

---

## 快速启动（新人 / AI）

### 1. 数据库

```bash
createdb nwueyes
psql -U postgres -d nwueyes -f sql/nwueyes_full_install.sql
```

或分步 migration：见 [`sql/migration/README.md`](sql/migration/README.md)。

### 2. Python

```bash
cd scripts
python -m venv .venv
.venv\Scripts\activate          # Windows
pip install -r requirements.txt -r requirements-embedding.txt
```

模型：[`scripts/models/README.md`](scripts/models/README.md)（含仓库内 `osnet_x0_25_imagenet.pth`）。

### 3. 配置

```bash
cd ruoyi-admin/src/main/resources
copy application-local.example.yml application-local.yml
```

填写萤石 key、`replayPythonCommand`（venv python 绝对路径）、`storageRoot`、`replayLineY`/`replayRoi`。  
**勿提交** `application-local.yml`。

### 4. 运行

```bash
# Redis 先启动
cd ../../../..   # 回到 ruoyi 根
mvn clean package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

默认端口 **8080**。

---

## 关键目录

| 路径 | 说明 |
|------|------|
| `ruoyi-admin/` | Spring Boot 入口、`application*.yml` |
| `ruoyi-system/` | 业务 Service、Mapper |
| `scripts/` | YOLO 直播/回放/分析 Worker |
| `sql/` | 建表、migration、全量安装脚本 |

---

## presence.ingest 配置摘要

```yaml
presence:
  ingest:
    enabled: true
    apiKey: change-me-ingest-key
    workspaceRoot: ./ruoyi          # Python cwd
    storageRoot: ./data
    replayPythonCommand: python     # 改为 venv 绝对路径
    replayLineY: 955                # 1920x1080 参考
    replayRoi: 733,311,1202,904
    clip:
      enabled: false                # 直播识别务必关闭，防 OOM
    live:
      targetDetectFps: 3
      yoloImgsz: 640
      lanRtspUrl: rtsp://...         # 局域网识别推荐
```

---

## 业务规则（简）

- **进门**：穿门槛线 → 追脸抓拍 → 人脸匹配 / 建陌生人 → 开 session  
- **出门**：exit 体态向量 vs `enter_body_embedding`，≥ `bodyMatchThreshold` 才关 session  
- **行为日志**：每次 enter/exit 一条，证据图在 `log_library/`

完整说明见仓库根 [`README.md`](../README.md)（与 monorepo 同级时）或 GitHub nwueyes 总览仓。

---

## License

MIT（与 RuoYi 一致）
