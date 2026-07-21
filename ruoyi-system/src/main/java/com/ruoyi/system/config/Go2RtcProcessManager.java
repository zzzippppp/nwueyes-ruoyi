package com.ruoyi.system.config;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;

/**
 * 随若依后端一起启动 go2rtc：后端就绪后拉起本机 go2rtc 子进程，关机时销毁。
 * 若目标端口已有 go2rtc 在跑（例如手动启动过），则跳过，避免端口冲突。
 */
@Component
public class Go2RtcProcessManager
{
    private static final Logger log = LoggerFactory.getLogger(Go2RtcProcessManager.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Autowired
    private PresenceIngestProperties ingestProperties;

    /** 是否随后端自启动 go2rtc */
    @Value("${presence.go2rtc.autoStart:true}")
    private boolean autoStart;

    /** go2rtc 可执行文件路径（相对 workspaceRoot 或绝对路径） */
    @Value("${presence.go2rtc.exePath:tools/go2rtc/go2rtc.exe}")
    private String exePath;

    /** go2rtc 配置文件路径（相对 workspaceRoot 或绝对路径） */
    @Value("${presence.go2rtc.configPath:tools/go2rtc/go2rtc.yaml}")
    private String configPath;

    private volatile Process process;

    @EventListener(ApplicationReadyEvent.class)
    public void startOnReady()
    {
        if (!autoStart)
        {
            log.info("go2rtc 自启动已关闭（presence.go2rtc.autoStart=false）");
            return;
        }

        String apiUrl = resolveApiUrl();
        if (isApiReachable(apiUrl))
        {
            log.info("go2rtc 已在运行（{}），跳过自启动", apiUrl);
            return;
        }

        File workDir = new File(ingestProperties.getWorkspaceRoot());
        File exe = resolveFile(workDir, exePath);
        File config = resolveFile(workDir, configPath);

        if (!exe.isFile())
        {
            log.warn("未找到 go2rtc 可执行文件，跳过自启动: {}", exe.getAbsolutePath());
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
            log.info("go2rtc 已自启动 pid={} exe={}", process.pid(), exe.getAbsolutePath());
            waitUntilReady(apiUrl);
        }
        catch (Exception ex)
        {
            log.error("go2rtc 自启动失败: {}", ex.getMessage(), ex);
        }
    }

    @PreDestroy
    public void stopOnShutdown()
    {
        Process current = process;
        if (current == null || !current.isAlive())
        {
            return;
        }
        log.info("正在停止 go2rtc pid={}", current.pid());
        current.destroy();
        try
        {
            if (!current.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
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
