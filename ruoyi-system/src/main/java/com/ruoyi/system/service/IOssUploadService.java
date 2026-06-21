package com.ruoyi.system.service;

import java.nio.file.Path;

public interface IOssUploadService
{
    String uploadClip(Path localFile, String objectKey);
}
