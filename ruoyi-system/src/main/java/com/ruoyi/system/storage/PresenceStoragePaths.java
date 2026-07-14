package com.ruoyi.system.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.PresenceIngestProperties;

/**
 * 持久化图片目录与 URL 约定。
 * <ul>
 *   <li>{@code log_library/face|body} — 行为日志证据图（永久，按日期分目录）</li>
 *   <li>{@code face_library} / {@code body_library} — 身份档案落盘 + 后台人脸上传（匹配库）</li>
 * </ul>
 */
@Component
public class PresenceStoragePaths
{
    /** 行为日志图访问前缀 */
    public static final String LOG_URL_PREFIX = "/dashboard/storage/file";

    /** 档案库图访问前缀（face_library / body_library） */
    public static final String ARCHIVE_URL_PREFIX = "/dashboard/data-board/file";

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd");

    private final PresenceIngestProperties ingestProperties;

    public PresenceStoragePaths(PresenceIngestProperties ingestProperties)
    {
        this.ingestProperties = ingestProperties;
    }

    public Path storageRoot()
    {
        return ingestProperties.resolveStorageRootPath();
    }

    public Path snapshotLibraryRoot()
    {
        return storageRoot().resolve("snapshot_library");
    }

    public Path snapshotDir(LocalDate date)
    {
        return snapshotLibraryRoot()
                .resolve(YEAR.format(date))
                .resolve(MONTH.format(date))
                .resolve(DAY.format(date));
    }

    public String buildSnapshotUrl(LocalDate date, String fileName)
    {
        return LOG_URL_PREFIX + "/snapshot/"
                + YEAR.format(date) + "/" + MONTH.format(date) + "/" + DAY.format(date) + "/"
                + fileName;
    }

    public Path resolveSnapshotFile(LocalDate date, String fileName)
    {
        return snapshotDir(date).resolve(safeFileName(fileName)).normalize();
    }

    public String promoteToSnapshot(Path sourceFile, LocalDate date, long logId) throws IOException
    {
        if (sourceFile == null || !Files.exists(sourceFile))
        {
            return "";
        }
        String fileName = "snap_" + logId + extensionOf(sourceFile);
        Path targetDir = snapshotDir(date);
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(fileName);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return buildSnapshotUrl(date, fileName);
    }

    public Path logLibraryRoot()
    {
        return storageRoot().resolve("log_library");
    }

    public Path probeLibraryRoot()
    {
        return logLibraryRoot().resolve("probe");
    }

    public String buildProbeFileUrl(String fileName)
    {
        return LOG_URL_PREFIX + "/log/probe/" + safeFileName(fileName);
    }

    public Path resolveProbeFile(String fileName)
    {
        return probeLibraryRoot().resolve(safeFileName(fileName)).normalize();
    }

    public Path faceLibraryRoot()
    {
        return storageRoot().resolve("face_library");
    }

    public Path bodyLibraryRoot()
    {
        return storageRoot().resolve("body_library");
    }

    public Path logFaceDir(LocalDate date)
    {
        return logLibraryRoot()
                .resolve("face")
                .resolve(YEAR.format(date))
                .resolve(MONTH.format(date))
                .resolve(DAY.format(date));
    }

    public Path logBodyDir(LocalDate date)
    {
        return logLibraryRoot()
                .resolve("body")
                .resolve(YEAR.format(date))
                .resolve(MONTH.format(date))
                .resolve(DAY.format(date));
    }

    public Path clipDir(LocalDate date)
    {
        return logLibraryRoot()
                .resolve("clips")
                .resolve(YEAR.format(date))
                .resolve(MONTH.format(date))
                .resolve(DAY.format(date));
    }

    public String buildLogFaceUrl(LocalDate date, String fileName)
    {
        return LOG_URL_PREFIX + "/log/face/"
                + YEAR.format(date) + "/" + MONTH.format(date) + "/" + DAY.format(date) + "/"
                + fileName;
    }

    public String buildLogBodyUrl(LocalDate date, String fileName)
    {
        return LOG_URL_PREFIX + "/log/body/"
                + YEAR.format(date) + "/" + MONTH.format(date) + "/" + DAY.format(date) + "/"
                + fileName;
    }

    public String buildClipUrl(LocalDate date, String fileName)
    {
        return LOG_URL_PREFIX + "/clip/"
                + YEAR.format(date) + "/" + MONTH.format(date) + "/" + DAY.format(date) + "/"
                + fileName;
    }

    public String buildArchiveFaceUrl(String fileName)
    {
        return ARCHIVE_URL_PREFIX + "/face/" + fileName;
    }

    public String buildArchiveBodyUrl(String fileName)
    {
        return ARCHIVE_URL_PREFIX + "/body/" + fileName;
    }

    public Path resolveLogFaceFile(LocalDate date, String fileName)
    {
        return logFaceDir(date).resolve(safeFileName(fileName)).normalize();
    }

    public Path resolveLogBodyFile(LocalDate date, String fileName)
    {
        return logBodyDir(date).resolve(safeFileName(fileName)).normalize();
    }

    public Path resolveClipFile(LocalDate date, String fileName)
    {
        return clipDir(date).resolve(safeFileName(fileName)).normalize();
    }

    public Path resolveArchiveFaceFile(String fileName)
    {
        return faceLibraryRoot().resolve(safeFileName(fileName)).normalize();
    }

    public Path resolveArchiveBodyFile(String fileName)
    {
        return bodyLibraryRoot().resolve(safeFileName(fileName)).normalize();
    }

    /**
     * 将已有图片复制为行为日志正式文件名，返回新 URL。
     */
    public String promoteToLogFace(Path sourceFile, LocalDate date, long logId) throws IOException
    {
        return promoteToLog(sourceFile, date, logId, "face");
    }

    public String promoteToLogBody(Path sourceFile, LocalDate date, long logId) throws IOException
    {
        return promoteToLog(sourceFile, date, logId, "body");
    }

    private String promoteToLog(Path sourceFile, LocalDate date, long logId, String kind) throws IOException
    {
        if (sourceFile == null || !Files.exists(sourceFile))
        {
            return "";
        }
        String fileName = "log_" + logId + "_" + kind + extensionOf(sourceFile);
        Path targetDir = "face".equals(kind) ? logFaceDir(date) : logBodyDir(date);
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(fileName);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return "face".equals(kind) ? buildLogFaceUrl(date, fileName) : buildLogBodyUrl(date, fileName);
    }

    public void ensureBaseDirectories() throws IOException
    {
        Files.createDirectories(logLibraryRoot());
        Files.createDirectories(probeLibraryRoot());
        Files.createDirectories(snapshotLibraryRoot());
        Files.createDirectories(faceLibraryRoot());
        Files.createDirectories(bodyLibraryRoot());
    }

    public void ensureLogDirectories(LocalDate date) throws IOException
    {
        Files.createDirectories(logFaceDir(date));
        Files.createDirectories(logBodyDir(date));
        Files.createDirectories(clipDir(date));
    }

    public boolean isUnderRoot(Path file, Path root)
    {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        return normalizedFile.startsWith(normalizedRoot);
    }

    /**
     * 将图片 URL 解析为本地文件路径。
     */
    public Optional<Path> resolveImageUrlToFile(String imageUrl)
    {
        if (StringUtils.isEmpty(imageUrl))
        {
            return Optional.empty();
        }
        String path = imageUrl.trim().replace("\\", "/");
        int q = path.indexOf('?');
        if (q >= 0)
        {
            path = path.substring(0, q);
        }
        String archiveFacePrefix = ARCHIVE_URL_PREFIX + "/face/";
        String archiveBodyPrefix = ARCHIVE_URL_PREFIX + "/body/";
        if (path.startsWith(archiveFacePrefix))
        {
            return optionalExisting(resolveArchiveFaceFile(path.substring(archiveFacePrefix.length())));
        }
        if (path.startsWith(archiveBodyPrefix))
        {
            return optionalExisting(resolveArchiveBodyFile(path.substring(archiveBodyPrefix.length())));
        }
        String logFacePrefix = LOG_URL_PREFIX + "/log/face/";
        String logBodyPrefix = LOG_URL_PREFIX + "/log/body/";
        if (path.startsWith(logFacePrefix))
        {
            return optionalExisting(resolveLogFaceUrlPath(path.substring(logFacePrefix.length())));
        }
        if (path.startsWith(logBodyPrefix))
        {
            return optionalExisting(resolveLogBodyUrlPath(path.substring(logBodyPrefix.length())));
        }
        String snapshotPrefix = LOG_URL_PREFIX + "/snapshot/";
        if (path.startsWith(snapshotPrefix))
        {
            return optionalExisting(resolveSnapshotUrlPath(path.substring(snapshotPrefix.length())));
        }
        return Optional.empty();
    }

    /**
     * 将 clip URL 解析为本地文件（OSS 上传等）。
     */
    public Optional<Path> resolveClipUrlToFile(String clipUrl)
    {
        if (StringUtils.isEmpty(clipUrl))
        {
            return Optional.empty();
        }
        String path = clipUrl.trim().replace("\\", "/");
        int q = path.indexOf('?');
        if (q >= 0)
        {
            path = path.substring(0, q);
        }
        String clipPrefix = LOG_URL_PREFIX + "/clip/";
        if (!path.startsWith(clipPrefix))
        {
            return Optional.empty();
        }
        String[] parts = path.substring(clipPrefix.length()).split("/");
        if (parts.length < 4)
        {
            return Optional.empty();
        }
        LocalDate date = LocalDate.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        return optionalExisting(resolveClipFile(date, parts[3]));
    }

    private Path resolveSnapshotUrlPath(String relative)
    {
        String[] parts = relative.split("/");
        if (parts.length < 4)
        {
            throw new IllegalArgumentException("非法 snapshot URL: " + relative);
        }
        LocalDate date = LocalDate.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        return resolveSnapshotFile(date, parts[3]);
    }

    private Optional<Path> optionalExisting(Path file)
    {
        return Files.exists(file) ? Optional.of(file) : Optional.empty();
    }

    private Path resolveLogFaceUrlPath(String relative)
    {
        String[] parts = relative.split("/");
        if (parts.length < 4)
        {
            throw new IllegalArgumentException("非法 log face URL: " + relative);
        }
        LocalDate date = LocalDate.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        return resolveLogFaceFile(date, parts[3]);
    }

    private Path resolveLogBodyUrlPath(String relative)
    {
        String[] parts = relative.split("/");
        if (parts.length < 4)
        {
            throw new IllegalArgumentException("非法 log body URL: " + relative);
        }
        LocalDate date = LocalDate.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]));
        return resolveLogBodyFile(date, parts[3]);
    }

    private String extensionOf(Path file)
    {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0)
        {
            return ".jpg";
        }
        return name.substring(dot);
    }

    private String safeFileName(String fileName)
    {
        if (fileName == null || fileName.isBlank())
        {
            throw new IllegalArgumentException("fileName 不能为空");
        }
        String normalized = fileName.replace("\\", "/");
        if (normalized.contains("..") || normalized.contains("/"))
        {
            throw new IllegalArgumentException("非法文件名: " + fileName);
        }
        return normalized;
    }
}
