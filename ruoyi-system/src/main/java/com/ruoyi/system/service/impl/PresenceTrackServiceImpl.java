package com.ruoyi.system.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.config.PresenceIngestProperties;
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.domain.vo.FaceMatchCandidateVo;
import com.ruoyi.system.domain.vo.PresenceOpenSessionVo;
import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;
import com.ruoyi.system.mapper.DataBoardMapper;
import com.ruoyi.system.mapper.PresenceIngestMapper;
import com.ruoyi.system.mapper.ProfileMatchMapper;
import com.ruoyi.system.service.IPresenceEmbedService;
import com.ruoyi.system.service.IPresenceTrackService;
import com.ruoyi.system.storage.PresenceStoragePaths;
import com.ruoyi.system.util.VectorLiteralUtil;

@Service
public class PresenceTrackServiceImpl implements IPresenceTrackService
{
    private static final Logger log = LoggerFactory.getLogger(PresenceTrackServiceImpl.class);

    private static final String PERSON_KIND_KNOWN = "known";
    private static final String PERSON_KIND_STRANGER = "stranger";
    private static final String PERSON_KIND_UNKNOWN = "unknown";
    private static final String QUALITY_NORMAL = "normal";
    private static final String QUALITY_LOW = "low";
    private static final String QUALITY_MISSING = "missing";

    @Autowired
    private PresenceIngestMapper presenceIngestMapper;

    @Autowired
    private ProfileMatchMapper profileMatchMapper;

    @Autowired
    private DataBoardMapper dataBoardMapper;

    @Autowired
    private IPresenceEmbedService presenceEmbedService;

    @Autowired
    private PresenceIngestProperties ingestProperties;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresenceTrackProcessResultVo processEnter(Long locationId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag)
    {
        validateLocation(locationId);
        String normalizedQuality = normalizeQuality(qualityFlag, faceImageUrl, bodyImageUrl);

        PresenceOpenSessionVo existingByTrack = presenceIngestMapper.selectOpenByTrack(locationId, trackKey);
        if (existingByTrack != null)
        {
            return buildSkippedEnterResult(existingByTrack, null, null, null, null, normalizedQuality);
        }

        EmbeddingVectorVo faceEmbed = presenceEmbedService.embedImage("face", faceImageUrl);
        EmbeddingVectorVo bodyEmbed = presenceEmbedService.embedImage("body", bodyImageUrl);

        Long personId = null;
        String displayName = defaultDisplayName(trackKey);
        String personKind = PERSON_KIND_UNKNOWN;
        Float faceMatchScore = null;
        boolean strangerRegistered = false;

        if (Boolean.TRUE.equals(faceEmbed.getOk()) && faceEmbed.getEmbedding() != null)
        {
            FaceMatchCandidateVo match = searchFace(faceEmbed.getEmbedding());
            if (match != null)
            {
                faceMatchScore = match.getScore();
                if (match.getScore() != null && match.getScore() >= faceMatchThreshold())
                {
                    personId = match.getPersonId();
                    displayName = StringUtils.nvl(match.getDisplayName(), displayName);
                    personKind = StringUtils.nvl(match.getPersonKind(), PERSON_KIND_KNOWN);
                }
            }
        }

        if (personId != null)
        {
            PresenceOpenSessionVo openByPerson = presenceIngestMapper.selectLatestOpenByPerson(locationId, personId);
            if (openByPerson != null)
            {
                return buildSkippedEnterResult(openByPerson, personId, displayName, personKind, faceMatchScore,
                        normalizedQuality);
            }
        }

        // 进门身份/合并 session 只认人脸；不用体态比对 open session，避免不同人误并
        if (personId == null)
        {
            if (Boolean.TRUE.equals(faceEmbed.getOk()) && faceEmbed.getEmbedding() != null)
            {
                personId = registerStranger(trackKey, faceImageUrl, bodyImageUrl, faceEmbed, bodyEmbed);
                personKind = PERSON_KIND_STRANGER;
                strangerRegistered = true;
            }
            else if (Boolean.TRUE.equals(bodyEmbed.getOk()) && bodyEmbed.getEmbedding() != null)
            {
                personId = registerStranger(trackKey, faceImageUrl, bodyImageUrl, faceEmbed, bodyEmbed);
                personKind = PERSON_KIND_STRANGER;
                strangerRegistered = true;
            }
        }

        String faceLiteral = toLiteral(faceEmbed);
        String bodyLiteral = toLiteral(bodyEmbed);
        Long sessionId = presenceIngestMapper.insertOpenSession(locationId, personId, trackKey, eventTime,
                faceImageUrl, bodyImageUrl, faceMatchScore, faceLiteral, bodyLiteral);

        return buildResult(sessionId, "open", personId, displayName, personKind, faceMatchScore, null,
                normalizedQuality, strangerRegistered);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresenceTrackProcessResultVo processExit(Long locationId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag)
    {
        validateLocation(locationId);
        String normalizedQuality = normalizeQuality(qualityFlag, faceImageUrl, bodyImageUrl);

        // 出门只关本 track 的 open session；不做体态/人脸兜底匹配，避免误关他人记录
        PresenceOpenSessionVo open = null;
        if (!StringUtils.isEmpty(trackKey))
        {
            open = presenceIngestMapper.selectOpenByTrack(locationId, trackKey);
        }
        if (open == null)
        {
            log.info("orphan exit log-only track={} locationId={} (no open session for track)", trackKey, locationId);
            return buildSkippedOrphanExitResult(trackKey, normalizedQuality);
        }

        int updated = presenceIngestMapper.closeSession(open.getSessionId(), eventTime, open.getPersonId(),
                faceImageUrl, bodyImageUrl, null);
        if (updated <= 0)
        {
            log.warn("exit session close failed track={} sessionId={}", trackKey, open.getSessionId());
            return buildSkippedOrphanExitResult(trackKey, normalizedQuality);
        }

        String personKind = open.getPersonId() == null ? PERSON_KIND_UNKNOWN : PERSON_KIND_STRANGER;
        String displayName = defaultDisplayName(open.getTrackKey());
        return buildResult(open.getSessionId(), "closed", open.getPersonId(), displayName,
                personKind, null, null, normalizedQuality, false);
    }

    private PresenceTrackProcessResultVo buildSkippedOrphanExitResult(String trackKey, String qualityFlag)
    {
        PresenceTrackProcessResultVo vo = buildResult(null, "orphan", null, defaultDisplayName(trackKey),
                PERSON_KIND_UNKNOWN, null, null, qualityFlag, false);
        vo.setSkippedOrphanExit(true);
        return vo;
    }

    private Long registerStranger(String trackKey, String faceImageUrl, String bodyImageUrl,
            EmbeddingVectorVo faceEmbed, EmbeddingVectorVo bodyEmbed)
    {
        String displayName = defaultDisplayName(trackKey);
        dataBoardMapper.insertPerson(displayName, PERSON_KIND_STRANGER, "", "");
        Long personId = dataBoardMapper.selectLastPersonId();
        if (personId == null)
        {
            throw new IllegalStateException("陌生人建档失败");
        }

        if (Boolean.TRUE.equals(faceEmbed.getOk()) && faceEmbed.getEmbedding() != null)
        {
            String archiveFaceUrl = promoteToArchiveFace(faceImageUrl);
            if (!StringUtils.isEmpty(archiveFaceUrl))
            {
                profileMatchMapper.insertFaceProfile(personId, VectorLiteralUtil.toLiteral(faceEmbed.getEmbedding()),
                        archiveFaceUrl);
            }
        }

        if (Boolean.TRUE.equals(bodyEmbed.getOk()) && bodyEmbed.getEmbedding() != null)
        {
            String archiveBodyUrl = promoteToArchiveBody(bodyImageUrl);
            if (!StringUtils.isEmpty(archiveBodyUrl))
            {
                profileMatchMapper.insertBodyProfile(personId, VectorLiteralUtil.toLiteral(bodyEmbed.getEmbedding()),
                        archiveBodyUrl);
            }
        }

        return personId;
    }

    private String promoteToArchiveFace(String imageUrl)
    {
        if (StringUtils.isEmpty(imageUrl))
        {
            return "";
        }
        if (imageUrl.contains("/data-board/file/face/"))
        {
            return imageUrl;
        }
        try
        {
            Optional<Path> sourceOpt = storagePaths.resolveImageUrlToFile(imageUrl);
            if (sourceOpt.isEmpty())
            {
                return imageUrl;
            }
            Path source = sourceOpt.get();
            String fileName = "face_" + IdUtils.fastSimpleUUID() + extensionOf(source);
            Path target = storagePaths.faceLibraryRoot().resolve(fileName);
            Files.createDirectories(storagePaths.faceLibraryRoot());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return storagePaths.buildArchiveFaceUrl(fileName);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("归档人脸图失败: " + ex.getMessage(), ex);
        }
    }

    private String promoteToArchiveBody(String imageUrl)
    {
        if (StringUtils.isEmpty(imageUrl))
        {
            return "";
        }
        if (imageUrl.contains("/data-board/file/body/"))
        {
            return imageUrl;
        }
        try
        {
            Optional<Path> sourceOpt = storagePaths.resolveImageUrlToFile(imageUrl);
            if (sourceOpt.isEmpty())
            {
                return imageUrl;
            }
            Path source = sourceOpt.get();
            String fileName = "body_" + IdUtils.fastSimpleUUID() + extensionOf(source);
            Path target = storagePaths.bodyLibraryRoot().resolve(fileName);
            Files.createDirectories(storagePaths.bodyLibraryRoot());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return storagePaths.buildArchiveBodyUrl(fileName);
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("归档体态图失败: " + ex.getMessage(), ex);
        }
    }

    private FaceMatchCandidateVo searchFace(List<Double> embedding)
    {
        String literal = VectorLiteralUtil.toLiteral(embedding);
        if (literal == null)
        {
            return null;
        }
        return profileMatchMapper.searchTopFaceMatch(literal, maxDistance(faceMatchThreshold()));
    }

    private float faceMatchThreshold()
    {
        Double value = ingestProperties.getFaceMatchThreshold();
        return value == null ? 0.45f : value.floatValue();
    }

    private float maxDistance(float similarityThreshold)
    {
        return Math.max(0.01f, 1.0f - similarityThreshold);
    }

    private String toLiteral(EmbeddingVectorVo embed)
    {
        if (embed == null || !Boolean.TRUE.equals(embed.getOk()) || embed.getEmbedding() == null)
        {
            return null;
        }
        return VectorLiteralUtil.toLiteral(embed.getEmbedding());
    }

    private void validateLocation(Long locationId)
    {
        if (locationId == null)
        {
            throw new IllegalArgumentException("locationId 不能为空");
        }
        if (presenceIngestMapper.existsLocation(locationId) <= 0)
        {
            throw new IllegalArgumentException("locationId 不存在: " + locationId);
        }
    }

    private String defaultDisplayName(String trackKey)
    {
        return "未登记-" + StringUtils.nvl(trackKey, "unknown");
    }

    private String normalizeQuality(String qualityFlag, String faceImageUrl, String bodyImageUrl)
    {
        if (!StringUtils.isEmpty(qualityFlag))
        {
            String normalized = qualityFlag.toLowerCase(Locale.ROOT);
            if (QUALITY_NORMAL.equals(normalized) || QUALITY_LOW.equals(normalized)
                    || QUALITY_MISSING.equals(normalized))
            {
                return normalized;
            }
        }
        if (StringUtils.isEmpty(faceImageUrl) && StringUtils.isEmpty(bodyImageUrl))
        {
            return QUALITY_MISSING;
        }
        if (StringUtils.isEmpty(faceImageUrl) && !StringUtils.isEmpty(bodyImageUrl))
        {
            return QUALITY_LOW;
        }
        return QUALITY_NORMAL;
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

    private PresenceTrackProcessResultVo buildSkippedEnterResult(PresenceOpenSessionVo open, Long personId,
            String displayName, String personKind, Float faceMatchScore, String qualityFlag)
    {
        Long resolvedPersonId = personId != null ? personId : open.getPersonId();
        String resolvedName = StringUtils.isEmpty(displayName) ? defaultDisplayName(open.getTrackKey()) : displayName;
        String resolvedKind = StringUtils.isEmpty(personKind)
                ? (resolvedPersonId == null ? PERSON_KIND_UNKNOWN : PERSON_KIND_STRANGER)
                : personKind;
        PresenceTrackProcessResultVo vo = buildResult(open.getSessionId(), "open", resolvedPersonId, resolvedName,
                resolvedKind, faceMatchScore, null, qualityFlag, false);
        vo.setSkippedDuplicateEnter(true);
        return vo;
    }

    private PresenceTrackProcessResultVo buildResult(Long sessionId, String sessionStatus, Long personId,
            String displayName, String personKind, Float faceMatchScore, Float bodyMatchScore, String qualityFlag,
            boolean strangerRegistered)
    {
        PresenceTrackProcessResultVo vo = new PresenceTrackProcessResultVo();
        vo.setSessionId(sessionId);
        vo.setSessionStatus(sessionStatus);
        vo.setPersonId(personId);
        vo.setDisplayName(displayName);
        vo.setPersonKind(personKind);
        vo.setFaceMatchScore(faceMatchScore);
        vo.setBodyMatchScore(bodyMatchScore);
        vo.setQualityFlag(qualityFlag);
        vo.setStrangerRegistered(strangerRegistered);
        return vo;
    }
}
