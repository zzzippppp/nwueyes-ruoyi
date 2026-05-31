package com.ruoyi.system.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.ruoyi.system.config.PresenceIngestProperties.LiveIngest;

/**
 * 识别 ingest 专用有界线程池，避免 Tomcat 线程被 embedding 堵死。
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
}
