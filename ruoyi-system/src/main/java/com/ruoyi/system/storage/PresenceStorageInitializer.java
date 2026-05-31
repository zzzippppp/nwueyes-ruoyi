package com.ruoyi.system.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时创建 log_library / face_library / body_library 基础目录。
 */
@Component
public class PresenceStorageInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(PresenceStorageInitializer.class);

    private final PresenceStoragePaths storagePaths;

    public PresenceStorageInitializer(PresenceStoragePaths storagePaths)
    {
        this.storagePaths = storagePaths;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception
    {
        storagePaths.ensureBaseDirectories();
        log.info("Presence storage ready at {}", storagePaths.storageRoot());
    }
}
