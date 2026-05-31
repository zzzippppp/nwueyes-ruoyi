# 业务库 migration

## 全新安装（推荐顺序）

需 PostgreSQL 15+ 与 **pgvector** 扩展。

```bash
createdb nwueyes

psql -U postgres -d nwueyes -f ruoyi/sql/ry_20260417.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/001_core_business.sql
psql -U postgres -d nwueyes -f ruoyi/sql/behavior_log.sql
# 002 仅旧库升级需要，新装可跳过
```

可选演示数据：

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/seed_data_board_demo.sql
psql -U postgres -d nwueyes -f ruoyi/sql/seed_behavior_log_demo.sql
```

安装后修改 `locations` 表 id=1 的 `device_serial` 为你的萤石设备序列号。

## 001_core_business.sql

| 表 | 用途 |
|----|------|
| `locations` | 监控点位（设备序列号、通道） |
| `persons` | 人员档案 |
| `face_profiles` / `body_profiles` | 人脸/体态向量匹配库 |
| `presence_sessions` | 停留会话（看板「在场中 / 已离开」） |
| `behavior_logs` | **行为日志**：每次进/出门一条流水 + 证据图 URL |

### behavior_logs 有什么用？

- **行为日志页**只读展示：谁、何时、从哪扇门 enter/exit、人脸/体态抓拍图。
- 与 `presence_sessions` 分工：session 管当前是否在场、停留时长；behavior_logs 管**完整事件审计**（含重复 enter、orphan exit）。
- 直播/回放 ingest 每次 enter/exit 都会写入（`source=live` 等）。

## 002_storage_and_vectors.sql

仅用于 **001 之前的老库** 补 `enter_body_embedding`、`quality_flag` 等字段；新装跑完 001 即可。

## 存储目录约定

| 目录 | 用途 |
|------|------|
| `log_library/face\|body/{yyyy}/{MM}/{dd}/` | 行为日志证据图（永久） |
| `face_library` / `body_library` | 身份档案落盘 + 后台人脸上传（匹配库） |

## Step 2: embedding 脚本

```bash
pip install -r ruoyi/scripts/requirements.txt
pip install -r ruoyi/scripts/requirements-embedding.txt
python ruoyi/scripts/embed_features.py --kind face --image ./data/log_library/face/sample.jpg
python ruoyi/scripts/embed_features.py --kind body --image ./data/log_library/body/sample.jpg
```
