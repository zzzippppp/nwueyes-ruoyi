package com.ruoyi.system.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.ruoyi.system.config.PresenceIngestProperties.ClipCapture;
import com.ruoyi.system.config.PresenceIngestProperties.LiveIngest;

/**
 * 识别 ingest / 离线分析专用有界线程池，避免 Tomcat 与公共异步池被长时间任务堵死。
 */
@Configuration
public class PresenceIngestAsyncConfig
{
    @Bean(name = "presenceIngestExecutor")
    public ThreadPoolTaskExecutor presenceIngestExecutor(PresenceIngestProperties ingestProperties)
    {
        LiveIngest live = ingestProperties.getLive();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(live.getIngestCorePoolSize());
        executor.setMaxPoolSize(live.getIngestMaxPoolSize());
        executor.setQueueCapacity(live.getIngestQueueCapacity());
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("presence-ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 离线 YOLO 分析专用池：默认单线程 + 有界队列 + 拒绝新任务。
     * 与 RuoYi threadPoolTaskExecutor 隔离，避免 waitFor 卡死拖垮全站异步。
     */
    @Bean(name = "presenceAnalyzeExecutor")
    public ThreadPoolTaskExecutor presenceAnalyzeExecutor(PresenceIngestProperties ingestProperties)
    {
        ClipCapture clip = ingestProperties.getClip();
        int poolSize = Math.max(1, clip == null ? 1 : clip.getAnalyzePoolSize());
        int queueCapacity = Math.max(1, clip == null ? 20 : clip.getAnalyzeQueueCapacity());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("presence-analyze-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
