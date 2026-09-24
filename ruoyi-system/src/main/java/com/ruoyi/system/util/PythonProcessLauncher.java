package com.ruoyi.system.util;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动 Python 子进程。JDK 的 jspawnhelper 偶发退出码 1 时，按间隔重试，避免一次失败就把任务标死。
 */
public final class PythonProcessLauncher
{
    private static final Logger log = LoggerFactory.getLogger(PythonProcessLauncher.class);

    private static final int MAX_ATTEMPTS = 5;

    private PythonProcessLauncher()
    {
    }

    public static Process start(ProcessBuilder pb) throws IOException
    {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            try
            {
                return pb.start();
            }
            catch (IOException ex)
            {
                last = ex;
                log.warn("python start failed attempt {}/{}: {}", attempt, MAX_ATTEMPTS, ex.getMessage());
                if (attempt >= MAX_ATTEMPTS)
                {
                    break;
                }
                try
                {
                    Thread.sleep(2000L * attempt);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last;
    }
}
