# 业务库 migration

## 全新安装

需 PostgreSQL 15+ 与 **pgvector** 扩展。

### 方式一：一键全量（推荐）

单文件包含若依基础表 + 全部业务表（合并 migration 001~011 最终结构）：

```bash
createdb nwueyes
psql -U postgres -d nwueyes -f ruoyi/sql/nwueyes_full_install.sql
```

- 源文件：`ruoyi/sql/nwueyes_full_install.sql`（由 `_gen_full_install.py` 从 `ry_20260417.sql` + `_business_schema_fragment.sql` 生成）
- 含若依初始数据、行为日志菜单、`sys_user.work_no`、默认摄像头 id=1
- **不含** quartz 定时任务表（需要时请另执行 `ruoyi/sql/quartz.sql`）
- 重复执行会 DROP 并重建所有表（含若依 `sys_*`），仅用于空库或开发重置

安装后修改 `camera` 表 id=1 的 `serial_no` 为你的萤石设备序列号。

若需重新生成全量脚本（修改了 ry 或业务 fragment 后）：

```bash
cd ruoyi/sql && python _gen_full_install.py
```

### 方式三：导入本地演示快照（可选）

全量安装完成后，可导入仓库内业务数据 + 图片，便于联调考勤/行为日志 UI：

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/seed_local_snapshot.sql
```

图片与测试视频在 **nwueyes 根仓库**（与 `ruoyi/` 同级）的 `face_library/`、`log_library/`、`snapshot_library/`、`uploadPath/` 等；`storageRoot` 指向该目录即可。

### 方式二：分步 migration（已有流程）

```bash
createdb nwueyes

psql -U postgres -d nwueyes -f ruoyi/sql/ry_20260417.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/001_core_business.sql
psql -U postgres -d nwueyes -f ruoyi/sql/behavior_log.sql
# 002 仅旧库升级需要，新装可跳过
psql -U postgres -d nwueyes -f ruoyi/sql/migration/003_fk_on_delete_set_null.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/004_video_clips_and_ai_analysis.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/005_attendance_extend.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/006_behavior_analysis.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/007_behavior_logs_restore_image_columns.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/008_platform_roster_user.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/008_menu_restructure.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/010_persons_cleanup_merge.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/011_drop_enter_face_embedding.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/012_show_video_test_menu.sql
```

> **关于两个 `008_*.sql`**：编号相同但**互不冲突**——一个改**表结构**，一个改**若依菜单**。执行顺序见下文「008 说明」；与表结构/菜单无先后硬性要求，但 **012 必须在 008_menu 之后**。

已有库若仍使用 `locations` 表，需额外执行：

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/009_camera_replace_locations.sql
```

可选演示数据：

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/seed_data_board_demo.sql
psql -U postgres -d nwueyes -f ruoyi/sql/seed_behavior_log_demo.sql
```

安装后修改 `camera` 表 id=1 的 `serial_no` 为你的萤石设备序列号。

## 001_core_business.sql

| 表 | 用途 |
|----|------|
| `device_type` / `camera` | 设备类型与摄像头（序列号、通道、门线标定） |
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

## 003_fk_on_delete_set_null.sql

修复后台**无法删除人员档案 / 停留记录**（被 `behavior_logs` 外键引用）。新装 001 已含 `ON DELETE SET NULL`，老库需执行本文件。

## 004_video_clips_and_ai_analysis.sql

视频片段表 `presence_video_clips`、AI 分析结果表 `ai_analysis_results`，以及 `behavior_logs` 的 clip/scene 关联字段。

## 005_attendance_extend.sql

考勤模块扩展（在现有表上 ALTER + 新建日汇总表）：

- `persons`：`employee_no`、`person_type`（student/staff/stranger），`known` → `student`
- `camera`：`line_y`、`roi`、参考分辨率
- `presence_sessions`：`attendance_date`，移除 `last_seen_at`
- `behavior_logs`：`snapshot_url`、视频字段；**保留** `face_image_url` / `body_image_url`；移除冗余 `source` 等列
- 新建 `person_daily_attendance`、`attendance_daily_stats`
- 外键改为 `ON DELETE CASCADE`

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/005_attendance_extend.sql
```

## 006_behavior_analysis.sql

为 `behavior_logs` 增加 `behavior_analysis`（TEXT，自然语言行为分析描述）。已有库执行：

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/006_behavior_analysis.sql
```

## 007_behavior_logs_restore_image_columns.sql

为已有库恢复/补全 `behavior_logs` 上的 `face_image_url`、`body_image_url` 等证据图字段（005 重构后部分环境缺失时需执行）。

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/007_behavior_logs_restore_image_columns.sql
```

## 008 说明：两个文件编号相同，职责不同

| 文件 | 改什么 | 何时需要 |
|------|--------|----------|
| **`008_platform_roster_user.sql`** | **数据库表**：`sys_platform_config` 新表；`persons` 人事字段；`sys_user.work_no` | 新装分步 migration 必跑；**前置 005** |
| **`008_menu_restructure.sql`** | **若依菜单** `sys_menu` / `sys_role_menu`：考勤管理、设备管理、智能问答等侧栏结构 | 需要新 UI 菜单时必跑；**不改业务表** |

二者无表级依赖，**顺序任意**；推荐先 platform（表结构）再 menu（菜单），便于联调 API 后再改菜单。

**`008_menu_restructure.sql` 主要变更：**

- 原「数据看板」目录 → **「考勤管理」**（考勤信息 601、考勤日志 602、**新增** 陌生人研判 603）
- **隐藏** 原顶级监控大屏 menu_id=5（视频检测由 **012** 挂到设备管理下）
- **新建「设备管理」** menu_id=8：监控大屏 801、设备信息 802、设备类型 803
- **新建「智能问答」** menu_id=9
- 为管理员角色 role_id=2 授权上述新菜单

**`008_platform_roster_user.sql` 主要变更：**

- 新建 `sys_platform_config`（门户/主题/版权等键值配置）
- `persons` 增加昵称、性别、手机、邮箱、部门、状态、人事头像、介绍视频等（原 `attendance_person` 已合并）
- `sys_user` 增加 `work_no`（学工号）及唯一索引

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/008_platform_roster_user.sql
psql -U postgres -d nwueyes -f ruoyi/sql/migration/008_menu_restructure.sql
```

## 012_show_video_test_menu.sql

将 **「视频检测」**（menu_id=7）从隐藏状态恢复，并挂到 **设备管理**（parent_id=8）下，与 008 菜单结构配套。**必须在 008_menu_restructure 之后执行。**

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/012_show_video_test_menu.sql
```

## 009_camera_replace_locations.sql

将历史 `locations` 表迁移为 `device_type` + `camera`，业务外键 `location_id` 重命名为 `camera_id` 并指向 `camera`。新装（001 已含 camera）可跳过。

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/009_camera_replace_locations.sql
```

## 010_persons_cleanup_merge.sql

- 删除 `persons.tags`、`face/body_profiles.library_file`、`face/body_profiles.is_primary`、`presence_sessions.id_state`
- 删除遗留表 `body_reid_profiles`
- 将 `attendance_person` 人事字段合并进 `persons` 并删除 `attendance_person`

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/010_persons_cleanup_merge.sql
```

## 011_drop_enter_face_embedding.sql

删除 `presence_sessions.enter_face_embedding`（出门人脸比对未启用，仅保留 `enter_body_embedding`）。

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/migration/011_drop_enter_face_embedding.sql
```

## 清空业务数据

```bash
psql -U postgres -d nwueyes -f ruoyi/sql/scripts/cleanup_nwueyes_data.sql
```

并手动删除 `storageRoot` 下 `log_library/`、`face_library/`、`body_library/`（配置见 `application-local.yml`）。

## 存储目录约定

| 目录 | 用途 |
|------|------|
| `log_library/face\|body/{yyyy}/{MM}/{dd}/` | 行为日志证据图（永久） |
| `face_library` / `body_library` | 身份档案落盘 + 后台人脸上传（匹配库） |

## Step 2: embedding 脚本

模型权重说明见 **`ruoyi/scripts/models/README.md`**（含仓库内 `osnet_x0_25_imagenet.pth` 与 YOLO/InsightFace 自动下载说明）。

```bash
pip install -r ruoyi/scripts/requirements.txt
pip install -r ruoyi/scripts/requirements-embedding.txt
python ruoyi/scripts/embed_features.py --kind face --image ./data/log_library/face/sample.jpg
python ruoyi/scripts/embed_features.py --kind body --image ./data/log_library/body/sample.jpg
```
