# Ubuntu 部署

本目录将 nwueyes 部署为：

- Nginx 提供 Vue 静态文件，并将 `/prod-api/` 代理至后端；
- systemd 运行 Spring Boot、go2rtc、备份任务，以及每日 `_ring` 环形缓冲清理；
- PostgreSQL、Redis 仅开放给本机；
- Python Worker 由 Spring Boot 使用 `/opt/nwueyes/ruoyi/.venv/bin/python` 启动。

## 目录约定

```text
/opt/nwueyes/
├── ruoyi/                 # 后端仓库，分支 local-ruoyi
├── RuoYi-Vue3/            # 前端仓库，分支 local-ruoyi-vue3
├── data/                  # 上传、抓拍、人脸/体态库、视频等持久化数据
└── backups/               # 数据库和数据目录备份

/etc/nwueyes/
├── nwueyes.env            # 私密环境变量，权限 600
└── application-local.yml  # 私密覆盖配置，权限 600
```

## 首次部署

1. 以 root 执行 `sudo bash install-dependencies.sh`。
2. 创建系统用户与目录：

   ```bash
   sudo useradd --system --home /opt/nwueyes --shell /usr/sbin/nologin nwueyes
   sudo install -d -o nwueyes -g nwueyes /opt/nwueyes/{data,backups}
   sudo install -d -m 700 /etc/nwueyes
   ```

3. 将两个仓库克隆到 `/opt/nwueyes/`，并切换至 `local-ruoyi` / `local-ruoyi-vue3`。
4. 从 go2rtc 官方 release 下载 Linux 对应架构的二进制到 `/opt/nwueyes/go2rtc/go2rtc`；复制 `go2rtc.yaml.example` 为 `/opt/nwueyes/go2rtc/go2rtc.yaml`，填入服务器局域网 IP 并执行 `sudo chown -R nwueyes:nwueyes /opt/nwueyes/go2rtc && sudo chmod 755 /opt/nwueyes/go2rtc/go2rtc`。
5. 复制 `nwueyes.env.example` 为 `/etc/nwueyes/nwueyes.env`，复制 `application-local.yml.example` 为 `/etc/nwueyes/application-local.yml`，填写真实值并执行：

   ```bash
   sudo chown root:nwueyes /etc/nwueyes/nwueyes.env /etc/nwueyes/application-local.yml
   sudo chmod 640 /etc/nwueyes/nwueyes.env /etc/nwueyes/application-local.yml
   ```

6. 创建应用数据库和 pgvector 扩展：`sudo bash bootstrap-database.sh`。
7. 从源机迁移数据：参见 `migrate-from-windows.ps1` 和 `restore-on-ubuntu.sh`。
8. 以 root 运行 `sudo bash deploy.sh`。它构建前后端、安装 systemd/Nginx 配置并启动服务。

## 磁盘清理（直播 `_ring`）

直播场景录像会在 `data/log_library/clips/_ring/<taskId>/` 写临时分段。worker 异常退出时可能残留。
已提供每日定时清理（默认 04:15，保留当前正在跑的 task，其余删除）：

```bash
sudo cp deploy/ubuntu/cleanup-ring.sh /opt/nwueyes/ruoyi/deploy/ubuntu/
sudo cp deploy/ubuntu/nwueyes-cleanup-ring.service deploy/ubuntu/nwueyes-cleanup-ring.timer /etc/systemd/system/
sudo chmod 755 /opt/nwueyes/ruoyi/deploy/ubuntu/cleanup-ring.sh
sudo systemctl daemon-reload
sudo systemctl enable --now nwueyes-cleanup-ring.timer
sudo systemctl list-timers 'nwueyes-cleanup-ring*'
# 立刻跑一次：
sudo systemctl start nwueyes-cleanup-ring.service
```

可选：在 `/etc/nwueyes/nwueyes.env` 增加 `CLIP_MAX_AGE_DAYS=14`，同时删除 14 天前的导出 mp4。

## 生产检查

```bash
systemctl status nwueyes go2rtc nginx redis-server
systemctl list-timers 'nwueyes-*'
curl -fsS http://127.0.0.1:8080/
curl -I http://127.0.0.1/
journalctl -u nwueyes -f
```

浏览器登录后，先在“设备信息”确认摄像头 IP、验证码、RTSP 和门线/ROI；然后在监控大屏启动识别并验证行为日志。

## 安全

- 只在公网防火墙放行 SSH、HTTP、HTTPS；不要公开 5432、6379、8080、1984、8554。若使用 WebRTC 预览，只允许可信局域网访问 UDP 8555。
- 修改默认 `admin/admin123`，并轮换任何曾存在于本机配置、仓库历史或聊天中的密钥。
- CPU 服务器应保留 `clip.enabled: false`、`targetDetectFps: 3`、`yoloImgsz: 640`。首次运行会下载 YOLO 和 InsightFace 模型，预留磁盘空间与网络出口。
- `go2rtc` 使用 Linux 二进制；将其放在 `/opt/nwueyes/go2rtc/go2rtc`，并执行 `chmod 755`。
