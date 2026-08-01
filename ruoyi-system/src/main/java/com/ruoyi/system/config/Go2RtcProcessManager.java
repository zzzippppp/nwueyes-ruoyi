package com.ruoyi.system.config;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ILanPreviewService;

/**
 * 随若依后端一起启动 go2rtc：后端就绪后拉起本机 go2rtc 子进程，关机时销毁。
 * 若目标端口已有 go2rtc 在跑（例如手动启动过），则跳过，避免端口冲突。
 * 运行中通过 API 探活；不可达时自动拉起，并通知重新注册已缓存的摄像头流。
 */
@Component
public class Go2RtcProcessManager
{
    private static final Logger log = LoggerFactory.getLogger(Go2RtcProcessManager.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final Object startLock = new Object();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final AtomicInteger failCount = new AtomicInteger(0);

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private ILanPreviewService lanPreviewService;

    /** 是否随后端自启动 go2rtc */
    @Value("${presence.go2rtc.autoStart:true}")
    private boolean autoStart;

    /** 运行中 API 不可达时是否自动拉起 */
    @Value("${presence.go2rtc.autoRestart:true}")
    private boolean autoRestart;

    /** 探活间隔（秒） */
    @Value("${presence.go2rtc.watchdogIntervalSec:15}")
    private int watchdogIntervalSec;

    /** 连续探活失败多少次后触发重启 */
    @Value("${presence.go2rtc.failThreshold:2}")
    private int failThreshold;

    /** go2rtc 可执行文件路径（相对 workspaceRoot 或绝对路径） */
    @Value("${presence.go2rtc.exePath:tools/go2rtc/go2rtc.exe}")
    private String exePath;

    /** go2rtc 配置文件路径（相对 workspaceRoot 或绝对路径） */
    @Value("${presence.go2rtc.configPath:tools/go2rtc/go2rtc.yaml}")
    private String configPath;

    private volatile Process process;

    private ScheduledExecutorService watchdog;

    @EventListener(ApplicationReadyEvent.class)
    public void startOnReady()
    {
        if (!autoStart)
        {
            log.info("go2rtc 自启动已关闭（presence.go2rtc.autoStart=false）");
            return;
        }

        ensureRunning("boot");
        if (autoRestart)
        {
            startWatchdog();
        }
        else
        {
            log.info("go2rtc 自动重启已关闭（presence.go2rtc.autoRestart=false）");
        }
    }

    @PreDestroy
    public void stopOnShutdown()
    {
        shuttingDown.set(true);
        stopWatchdog();
        destroyManagedProcess();
    }

    private void startWatchdog()
    {
        if (watchdog != null)
        {
            return;
        }
        int interval = Math.max(5, watchdogIntervalSec);
        watchdog = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "go2rtc-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleWithFixedDelay(this::watchdogTick, interval, interval, TimeUnit.SECONDS);
        log.info("go2rtc 探活已启动 intervalSec={} failThreshold={}", interval, Math.max(1, failThreshold));
    }

    private void stopWatchdog()
    {
        ScheduledExecutorService executor = watchdog;
        watchdog = null;
        if (executor != null)
        {
            executor.shutdownNow();
        }
    }

    private void watchdogTick()
    {
        if (shuttingDown.get() || !autoStart || !autoRestart)
        {
            return;
        }
        try
        {
            String apiUrl = resolveApiUrl();
            if (isApiReachable(apiUrl))
            {
                failCount.set(0);
                return;
            }
            int fails = failCount.incrementAndGet();
            int threshold = Math.max(1, failThreshold);
            log.warn("go2rtc API 不可达 ({}/{}) {}", fails, threshold, apiUrl);
            if (fails < threshold)
            {
                return;
            }
            ensureRunning("watchdog");
        }
        catch (Exception ex)
        {
            log.debug("go2rtc watchdog tick error: {}", ex.getMessage());
        }
    }

    /**
     * 确保 go2rtc API 可用；必要时拉起本机进程并重新注册流。
     */
    private void ensureRunning(String reason)
    {
        synchronized (startLock)
        {
            if (shuttingDown.get())
            {
                return;
            }
            String apiUrl = resolveApiUrl();
            if (isApiReachable(apiUrl))
            {
                failCount.set(0);
                if ("boot".equals(reason))
                {
                    log.info("go2rtc 已在运行（{}），跳过自启动", apiUrl);
                }
                return;
            }

            Process current = process;
            if (current != null && current.isAlive())
            {
                log.warn("go2rtc 进程仍在但 API 不可达，强制重启 pid={} reason={}", current.pid(), reason);
                destroyManagedProcess();
            }
            else if (current != null)
            {
                process = null;
            }

            File workDir = new File(ingestProperties.getWorkspaceRoot());
            File exe = resolveFile(workDir, exePath);
            File config = resolveFile(workDir, configPath);
            if (!exe.isFile())
            {
                log.warn("未找到 go2rtc 可执行文件，跳过拉起: {}", exe.getAbsolutePath());
                return;
            }

            try
            {
                ProcessBuilder pb = new ProcessBuilder();
                if (config.isFile())
                {
                    pb.command(exe.getAbsolutePath(), "-config", config.getAbsolutePath());
                }
                else
                {
                    log.warn("未找到 go2rtc 配置文件，使用内置默认配置启动: {}", config.getAbsolutePath());
                    pb.command(exe.getAbsolutePath());
                }
                pb.directory(exe.getParentFile());
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                process = pb.start();
                log.info("go2rtc 已拉起 pid={} exe={} reason={}", process.pid(), exe.getAbsolutePath(), reason);
                waitUntilReady(apiUrl);
                failCount.set(0);
                reregisterStreamsQuietly();
            }
            catch (Exception ex)
            {
                log.error("go2rtc 拉起失败 reason={}: {}", reason, ex.getMessage(), ex);
            }
        }
    }

    private void reregisterStreamsQuietly()
    {
        try
        {
            int n = lanPreviewService.reregisterCachedStreams();
            if (n > 0)
            {
                log.info("go2rtc 恢复后已重新注册 {} 路摄像头流", n);
            }
        }
        catch (Exception ex)
        {
            log.warn("go2rtc 恢复后重新注册流失败: {}", ex.getMessage());
        }
    }

    private void destroyManagedProcess()
    {
        Process current = process;
        process = null;
        if (current == null || !current.isAlive())
        {
            return;
        }
        log.info("正在停止 go2rtc pid={}", current.pid());
        current.destroy();
        try
        {
            if (!current.waitFor(5, TimeUnit.SECONDS))
            {
                current.destroyForcibly();
            }
        }
        catch (InterruptedException ex)
        {
            current.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private String resolveApiUrl()
    {
        String base = ingestProperties.getLive().getGo2rtcBaseUrl();
        if (StringUtils.isEmpty(base))
        {
            base = "http://127.0.0.1:1984";
        }
        base = base.trim();
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api";
    }

    private boolean isApiReachable(String apiUrl)
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 500;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private void waitUntilReady(String apiUrl)
    {
        for (int i = 0; i < 20; i++)
        {
            if (isApiReachable(apiUrl))
            {
                log.info("go2rtc 就绪: {}", apiUrl);
                return;
            }
            try
            {
                Thread.sleep(500);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("go2rtc 启动后 {} 未在预期时间内就绪，请检查日志", apiUrl);
    }

    private File resolveFile(File workDir, String path)
    {
        File file = new File(path);
        if (file.isAbsolute())
        {
            return file;
        }
        return new File(workDir, path);
    }
}
