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
import com.ruoyi.system.domain.vo.BodySessionMatchVo;
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.domain.vo.FaceMatchCandidateVo;
import com.ruoyi.system.domain.vo.PresenceOpenSessionVo;
import com.ruoyi.system.domain.vo.PresenceTrackMatchPreviewVo;
import com.ruoyi.system.domain.vo.PresenceTrackProcessResultVo;
import com.ruoyi.system.domain.vo.VirtualOpenSessionVo;
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

    private static final String PERSON_TYPE_STUDENT = "student";
    private static final String PERSON_TYPE_STAFF = "staff";
    private static final String PERSON_TYPE_STRANGER = "stranger";
    private static final String PERSON_TYPE_UNKNOWN = "unknown";
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

    @Autowired
    private com.ruoyi.system.service.IAttendanceDailyService attendanceDailyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresenceTrackProcessResultVo processEnter(Long cameraId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag)
    {
        validateLocation(cameraId);
        String normalizedQuality = normalizeQuality(qualityFlag, faceImageUrl, bodyImageUrl);

        PresenceOpenSessionVo existingByTrack = presenceIngestMapper.selectOpenByTrack(cameraId, trackKey);
        if (existingByTrack != null)
        {
            return buildSkippedEnterResult(existingByTrack, null, null, null, null, normalizedQuality);
        }

        // 只做人脸：无可用人脸则不建档、不开 session（体态已彻底废除）
        EmbeddingVectorVo faceEmbed = presenceEmbedService.embedImage("face", faceImageUrl);
        if (!Boolean.TRUE.equals(faceEmbed.getOk()) || faceEmbed.getEmbedding() == null)
        {
            log.info("enter skipped: no usable face track={} cameraId={}", trackKey, cameraId);
            return buildResult(null, "skipped", null, defaultDisplayName(trackKey), PERSON_TYPE_UNKNOWN, null, null,
                    QUALITY_MISSING.equals(normalizedQuality) ? normalizedQuality : QUALITY_LOW, false);
        }

        Long personId = null;
        String displayName = defaultDisplayName(trackKey);
        String personKind = PERSON_TYPE_UNKNOWN;
        Float faceMatchScore = null;
        boolean strangerRegistered = false;

        FaceMatchCandidateVo match = searchFace(faceEmbed.getEmbedding());
        if (match != null)
        {
            faceMatchScore = match.getScore();
            if (match.getScore() != null && match.getScore() >= faceMatchThreshold())
            {
                personId = match.getPersonId();
                displayName = StringUtils.nvl(match.getDisplayName(), displayName);
                personKind = StringUtils.nvl(match.getPersonKind(), PERSON_TYPE_STUDENT);
            }
        }

        if (personId != null)
        {
            PresenceOpenSessionVo openByPerson = presenceIngestMapper.selectLatestOpenByPerson(cameraId, personId);
            if (openByPerson != null)
            {
                return buildSkippedEnterResult(openByPerson, personId, displayName, personKind, faceMatchScore,
                        normalizedQuality);
            }
        }

        // 有脸但未匹配在案人员时建 stranger 档案（只写人脸）
        if (personId == null)
        {
            displayName = "未登记-" + IdUtils.fastSimpleUUID().substring(0, 8);
            personId = registerStranger(displayName, faceImageUrl, faceEmbed);
            personKind = PERSON_TYPE_STRANGER;
            strangerRegistered = true;
        }

        Long sessionId = presenceIngestMapper.insertOpenSession(cameraId, personId, trackKey, eventTime,
                faceMatchScore, null);

        PresenceTrackProcessResultVo result = buildResult(sessionId, "open", personId, displayName, personKind, faceMatchScore, null,
                normalizedQuality, strangerRegistered);
        if (!result.isSkippedDuplicateEnter())
        {
            attendanceDailyService.onEnter(personId, cameraId, sessionId, eventTime);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresenceTrackProcessResultVo processExit(Long cameraId, String trackKey, Date eventTime,
            String faceImageUrl, String bodyImageUrl, String qualityFlag)
    {
        validateLocation(cameraId);
        // 体态出门已彻底废除：离场只走 processExitByFace（门外人脸比对）
        String normalizedQuality = normalizeQuality(qualityFlag, faceImageUrl, bodyImageUrl);
        log.info("orphan exit log-only track={} cameraId={} (body exit abolished; use exterior face exit)",
                trackKey, cameraId);
        return buildSkippedOrphanExitResult(trackKey, normalizedQuality);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PresenceTrackProcessResultVo processExitByFace(Long cameraId, List<Double> faceEmbedding, Date eventTime)
    {
        validateLocation(cameraId);
        if (faceEmbedding == null || faceEmbedding.isEmpty())
        {
            return buildFaceExitOrphan();
        }
        FaceMatchCandidateVo match = searchFace(faceEmbedding);
        if (match == null || match.getScore() == null || match.getScore() < faceMatchThreshold())
        {
            // 未命中人脸库：门外只做离场，不建档、不记录
            return buildFaceExitOrphan();
        }
        Long personId = match.getPersonId();
        // 命中但当前不在场（无 open session）→ 不处理（可能是进门途中/仅路过门外）
        PresenceOpenSessionVo open = presenceIngestMapper.selectAnyOpenByPerson(personId);
        if (open == null)
        {
            return buildFaceExitOrphan();
        }
        if (eventTime != null && open.getArrivalAt() != null && eventTime.before(open.getArrivalAt()))
        {
            return buildFaceExitOrphan();
        }
        int updated = presenceIngestMapper.closeSession(open.getSessionId(), eventTime, personId, match.getScore());
        if (updated <= 0)
        {
            return buildFaceExitOrphan();
        }
        attendanceDailyService.onExit(personId, open.getSessionId(), eventTime,
                computeDwellSeconds(open.getArrivalAt(), eventTime));
        String displayName = StringUtils.nvl(match.getDisplayName(), defaultDisplayName(open.getTrackKey()));
        String personKind = StringUtils.nvl(match.getPersonKind(), PERSON_TYPE_STUDENT);
        log.info("exterior face-exit closed session personId={} sessionId={} score={}",
                personId, open.getSessionId(), match.getScore());
        return buildResult(open.getSessionId(), "closed", personId, displayName, personKind,
                match.getScore(), null, QUALITY_NORMAL, false);
    }

    private PresenceTrackProcessResultVo buildFaceExitOrphan()
    {
        PresenceTrackProcessResultVo vo = buildResult(null, "orphan", null, null, PERSON_TYPE_UNKNOWN,
                null, null, QUALITY_NORMAL, false);
        vo.setSkippedOrphanExit(true);
        return vo;
    }

    @Override
    public PresenceTrackMatchPreviewVo previewEnterMatch(String trackKey, String faceImageUrl, String bodyImageUrl)
    {
        PresenceTrackMatchPreviewVo vo = new PresenceTrackMatchPreviewVo();
        vo.setDisplayName(defaultDisplayName(trackKey));
        vo.setPersonKind(PERSON_TYPE_UNKNOWN);
        vo.setMatched(false);

        EmbeddingVectorVo faceEmbed = presenceEmbedService.embedImage("face", faceImageUrl);
        if (!Boolean.TRUE.equals(faceEmbed.getOk()) || faceEmbed.getEmbedding() == null)
        {
            return vo;
        }

        FaceMatchCandidateVo match = searchFace(faceEmbed.getEmbedding());
        if (match == null || match.getScore() == null)
        {
            return vo;
        }
        vo.setFaceMatchScore(match.getScore());
        if (match.getScore() >= faceMatchThreshold())
        {
            vo.setMatched(true);
            vo.setPersonId(match.getPersonId());
            vo.setDisplayName(StringUtils.nvl(match.getDisplayName(), vo.getDisplayName()));
            vo.setPersonKind(StringUtils.nvl(match.getPersonKind(), PERSON_TYPE_STUDENT));
        }
        return vo;
    }

    @Override
    public PresenceTrackMatchPreviewVo previewExitMatch(String exitTrackKey, String bodyImageUrl,
            List<VirtualOpenSessionVo> openSessions)
    {
        // 体态出门已废除：预览不再做 body ReID
        PresenceTrackMatchPreviewVo vo = new PresenceTrackMatchPreviewVo();
        vo.setDisplayName(defaultDisplayName(exitTrackKey));
        vo.setPersonKind(PERSON_TYPE_UNKNOWN);
        vo.setMatched(false);
        return vo;
    }

    private float cosineSimilarity(List<Double> a, List<Double> b)
    {
        if (a == null || b == null || a.isEmpty() || b.size() != a.size())
        {
            return -1f;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++)
        {
            double av = a.get(i);
            double bv = b.get(i);
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA <= 0.0 || normB <= 0.0)
        {
            return -1f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static final class ExitBodyMatch
    {
        private PresenceOpenSessionVo session;

        private Float score;
    }

    /**
     * 出门关 session：exit 体态向量 vs open session 的 enter_body_embedding，
     * 仅当相似度达到 bodyMatchThreshold 时才返回对应 session（非 trackKey 兜底）。
     * 仅匹配 arrival_at &lt;= eventTime 的会话，避免乱序入库把早出门配到晚进门。
     */
    private ExitBodyMatch resolveExitOpenSession(Long cameraId, String trackKey, Date eventTime,
            EmbeddingVectorVo bodyEmbed)
    {
        if (!Boolean.TRUE.equals(bodyEmbed.getOk()) || bodyEmbed.getEmbedding() == null)
        {
            log.debug("exit body embed unavailable track={}", trackKey);
            return null;
        }
        String literal = toLiteral(bodyEmbed);
        if (literal == null)
        {
            return null;
        }
        BodySessionMatchVo match = searchOpenSessionByBody(cameraId, literal, eventTime);
        if (match == null || match.getSessionId() == null)
        {
            return null;
        }
        float threshold = bodyMatchThreshold();
        if (match.getScore() == null || match.getScore().floatValue() < threshold)
        {
            return null;
        }
        PresenceOpenSessionVo session = presenceIngestMapper.selectOpenBySessionId(match.getSessionId());
        if (session == null)
        {
            return null;
        }
        if (eventTime != null && session.getArrivalAt() != null && eventTime.before(session.getArrivalAt()))
        {
            return null;
        }
        ExitBodyMatch result = new ExitBodyMatch();
        result.session = session;
        result.score = match.getScore();
        return result;
    }

    private BodySessionMatchVo searchOpenSessionByBody(Long cameraId, String embeddingLiteral, Date eventTime)
    {
        return profileMatchMapper.searchTopOpenSessionByBody(cameraId, embeddingLiteral,
                maxDistance(bodyMatchThreshold()), eventTime);
    }

    private float bodyMatchThreshold()
    {
        Double value = ingestProperties.getBodyMatchThreshold();
        return value == null ? 0.50f : value.floatValue();
    }

    private PresenceTrackProcessResultVo buildSkippedOrphanExitResult(String trackKey, String qualityFlag)
    {
        PresenceTrackProcessResultVo vo = buildResult(null, "orphan", null, defaultDisplayName(trackKey),
                PERSON_TYPE_UNKNOWN, null, null, qualityFlag, false);
        vo.setSkippedOrphanExit(true);
        return vo;
    }

    private Long registerStranger(String displayName, String faceImageUrl, EmbeddingVectorVo faceEmbed)
    {
        // 体态(body ReID)已废除，陌生人建档只保留人脸档案
        dataBoardMapper.insertPerson(displayName, PERSON_TYPE_STRANGER, null, "");
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
                        archiveFaceUrl, faceEmbed.getDetScore());
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
        return value == null ? 0.35f : value.floatValue();
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

    private void validateLocation(Long cameraId)
    {
        if (cameraId == null)
        {
            throw new IllegalArgumentException("cameraId 不能为空");
        }
        if (presenceIngestMapper.existsLocation(cameraId) <= 0)
        {
            throw new IllegalArgumentException("cameraId 不存在: " + cameraId);
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
        // 体态已废除：只按人脸图判定质量
        if (StringUtils.isEmpty(faceImageUrl))
        {
            return QUALITY_MISSING;
        }
        return QUALITY_NORMAL;
    }

    private int computeDwellSeconds(java.util.Date arrivalAt, java.util.Date departureAt)
    {
        if (arrivalAt == null || departureAt == null)
        {
            return 0;
        }
        long seconds = (departureAt.getTime() - arrivalAt.getTime()) / 1000L;
        return (int) Math.max(0L, seconds);
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
                ? (resolvedPersonId == null ? PERSON_TYPE_UNKNOWN : PERSON_TYPE_STRANGER)
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
