# 业务库 migration

按顺序执行（需已启用 `vector` 扩展，见 `sql/ry_20260417.sql`）：

```bash
psql "postgresql://postgres:root123@localhost:5432/nwueyes" -f ruoyi/sql/migration/002_storage_and_vectors.sql
```

## 002_storage_and_vectors.sql

- `behavior_logs`：匹配分、质量标记
- `presence_sessions`：进门 face/body 512 维向量（出门体态比对）
- `body_profiles`：补充 `embedding vector(512)` + HNSW 索引

## 存储目录约定

| 目录 | 用途 |
|------|------|
| `log_library/face|body/{yyyy}/{MM}/{dd}/` | 行为日志证据图（永久） |
| `face_library` / `body_library` | 身份档案落盘 + 后台人脸上传（匹配库） |

## Step 2: embedding 脚本

```bash
pip install -r ruoyi/scripts/requirements-embedding.txt
python ruoyi/scripts/embed_features.py --kind face --image E:/nwueyes/log_library/face/2026/05/29/analyze_xxx.jpg
python ruoyi/scripts/embed_features.py --kind body --image E:/nwueyes/log_library/body/2026/05/29/analyze_xxx.jpg
```

## 图片 URL 约定

| 用途 | URL 示例 |
|------|----------|
| 日志人脸 | `/dashboard/storage/file/log/face/2026/05/29/log_123_face.jpg` |
| 日志体态 | `/dashboard/storage/file/log/body/2026/05/29/log_123_body.jpg` |
| 档案人脸 | `/dashboard/data-board/file/face/face_xxx.jpg` |
| 档案体态 | `/dashboard/data-board/file/body/body_xxx.jpg` |
