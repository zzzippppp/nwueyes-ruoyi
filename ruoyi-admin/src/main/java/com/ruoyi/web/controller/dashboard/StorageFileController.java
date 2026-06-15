package com.ruoyi.web.controller.dashboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping("/clip/{year}/{month}/{day}/{fileName:.+}")
    @Anonymous
    public ResponseEntity<Resource> serveClip(@PathVariable("year") int year,
            @PathVariable("month") int month,
            @PathVariable("day") int day,
            @PathVariable("fileName") String fileName,
            @RequestHeader HttpHeaders headers) throws Exception
    {
        LocalDate date = LocalDate.of(year, month, day);
        return serveVideo(storagePaths.resolveClipFile(date, fileName), storagePaths.clipDir(date), headers);
    }

    private ResponseEntity<Resource> serve(Path filePath, Path allowedRoot) throws Exception
    {
        if (!storagePaths.isUnderRoot(filePath, allowedRoot) || !Files.exists(filePath))
        {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(filePath.toUri());
        return ResponseEntity.ok().contentType(resolveMediaType(filePath.getFileName().toString())).body(resource);
    }

    private ResponseEntity<Resource> serveVideo(Path filePath, Path allowedRoot, HttpHeaders headers) throws Exception
    {
        if (!storagePaths.isUnderRoot(filePath, allowedRoot) || !Files.exists(filePath))
        {
            return ResponseEntity.notFound().build();
        }

        long fileSize = Files.size(filePath);
        MediaType mediaType = resolveMediaType(filePath.getFileName().toString());
        if (headers.getRange().isEmpty())
        {
            Resource resource = new UrlResource(filePath.toUri());
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(fileSize)
                    .contentType(mediaType)
                    .body(resource);
        }

        // Chrome video preview sends byte ranges; responding with 206 keeps playback and seeking reliable.
        HttpRange range = headers.getRange().get(0);
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        long length = end - start + 1;
        java.io.InputStream inputStream = Files.newInputStream(filePath);
        inputStream.skip(start);
        InputStreamResource resource = new InputStreamResource(inputStream);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .contentLength(length)
                .contentType(mediaType)
                .body(resource);
    }

    private MediaType resolveMediaType(String fileName)
    {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".mp4"))
        {
            return MediaType.parseMediaType("video/mp4");
        }
        if (lower.endsWith(".webm"))
        {
            return MediaType.parseMediaType("video/webm");
        }
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
