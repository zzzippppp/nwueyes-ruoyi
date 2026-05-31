package com.ruoyi.web.controller.dashboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.system.storage.PresenceStoragePaths;

/**
 * 行为日志证据图访问（log_library/face|body）。
 */
@RestController
@RequestMapping("/dashboard/storage/file")
public class StorageFileController
{
    @Autowired
    private PresenceStoragePaths storagePaths;

    @GetMapping("/log/face/{year}/{month}/{day}/{fileName:.+}")
    @Anonymous
    public ResponseEntity<Resource> serveLogFace(@PathVariable("year") int year,
            @PathVariable("month") int month,
            @PathVariable("day") int day,
            @PathVariable("fileName") String fileName) throws Exception
    {
        LocalDate date = LocalDate.of(year, month, day);
        return serve(storagePaths.resolveLogFaceFile(date, fileName), storagePaths.logFaceDir(date));
    }

    @GetMapping("/log/body/{year}/{month}/{day}/{fileName:.+}")
    @Anonymous
    public ResponseEntity<Resource> serveLogBody(@PathVariable("year") int year,
            @PathVariable("month") int month,
            @PathVariable("day") int day,
            @PathVariable("fileName") String fileName) throws Exception
    {
        LocalDate date = LocalDate.of(year, month, day);
        return serve(storagePaths.resolveLogBodyFile(date, fileName), storagePaths.logBodyDir(date));
    }

    private ResponseEntity<Resource> serve(Path filePath, Path allowedRoot) throws Exception
    {
        if (!storagePaths.isUnderRoot(filePath, allowedRoot) || !Files.exists(filePath))
        {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(filePath.toUri());
        return ResponseEntity.ok().contentType(resolveImageMediaType(filePath.getFileName().toString())).body(resource);
    }

    private MediaType resolveImageMediaType(String fileName)
    {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png"))
        {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif"))
        {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp"))
        {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
