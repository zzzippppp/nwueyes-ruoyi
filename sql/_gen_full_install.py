"""生成 nwueyes_full_install.sql"""
from pathlib import Path

root = Path(__file__).resolve().parent
ruoyi = (root / "ry_20260417.sql").read_text(encoding="utf-8")

ruoyi_lines = ruoyi.splitlines()
if ruoyi_lines[0].strip().startswith("CREATE EXTENSION"):
    ruoyi_body = "\n".join(ruoyi_lines[1:])
else:
    ruoyi_body = ruoyi
ruoyi_body = ruoyi_body.rstrip()
if ruoyi_body.endswith("COMMIT;"):
    ruoyi_body = ruoyi_body[: -len("COMMIT;")].rstrip()

ruoyi_body = ruoyi_body.replace(
    "  remark            VARCHAR(500) DEFAULT NULL\n);",
    "  remark            VARCHAR(500) DEFAULT NULL,\n  work_no           VARCHAR(30) NOT NULL DEFAULT ''\n);",
    1,
)

business = (root / "_business_schema_fragment.sql").read_text(encoding="utf-8")

header = """-- =============================================================================
-- nwueyes 全量数据库安装脚本（若依基础表 + 业务表）
-- =============================================================================
-- 前置: PostgreSQL 15+，已安装 pgvector 扩展
--
-- 用法:
--   createdb nwueyes
--   psql -U postgres -d nwueyes -f ruoyi/sql/nwueyes_full_install.sql
--
-- 说明:
--   - 合并 ry_20260417.sql 与 migration 001~011 的最终表结构
--   - 含若依初始数据（admin 账号、菜单、字典等）及默认摄像头 id=1
--   - 重复执行会先 DROP 业务表再重建；若依 sys_* 表也会先 DROP 再建
--   - 不包含 quartz 定时任务表（需定时任务时请另执行 sql/quartz.sql）
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- 第一部分：若依 RuoYi 基础表 + 初始数据
-- =============================================================================

"""

out = (
    header
    + ruoyi_body
    + "\n\n"
    + business
    + "\nCOMMIT;\n"
)

out_path = root / "nwueyes_full_install.sql"
out_path.write_text(out, encoding="utf-8", newline="\n")
print(f"written {out_path} ({out_path.stat().st_size} bytes)")
