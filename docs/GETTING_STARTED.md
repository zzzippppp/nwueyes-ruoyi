# nwueyes 启动指南

完整文档见工作区根目录 [README.md](../../README.md)（`ruoyi` 与 `RuoYi-Vue3` 同级）。

## 本目录关键文件

| 文件 | 用途 |
|------|------|
| `ruoyi-admin/src/main/resources/application-local.example.yml` | 复制为 `application-local.yml` 填写私密配置 |
| `.env.example` | 环境变量清单 |
| `scripts/requirements.txt` | YOLO / OpenCV |
| `scripts/requirements-embedding.txt` | InsightFace / torchreid |
| `sql/ry_20260417.sql` | 若依 sys_* + pgvector |
| `sql/migration/001_core_business.sql` | 业务表（含 behavior_logs） |
| `sql/behavior_log.sql` | 行为日志菜单 |
