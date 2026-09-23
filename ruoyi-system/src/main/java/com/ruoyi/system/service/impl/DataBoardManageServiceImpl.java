package com.ruoyi.system.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.bo.DataBoardCameraUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardPersonUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardSessionUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardStrangerUpdateBo;
import com.ruoyi.system.domain.vo.CameraConfigVo;
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.mapper.AttendanceDailyMapper;
import com.ruoyi.system.mapper.CameraMapper;
import com.ruoyi.system.mapper.DataBoardMapper;
import com.ruoyi.system.mapper.ProfileMatchMapper;
import com.ruoyi.system.service.IDataBoardManageService;
import com.ruoyi.system.service.IPresenceEmbedService;
import com.ruoyi.system.storage.PresenceStoragePaths;
import com.ruoyi.system.util.VectorLiteralUtil;

@Service
public class DataBoardManageServiceImpl implements IDataBoardManageService
{
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    private static final String TYPE_STUDENT = "student";

    private static final String TYPE_STAFF = "staff";

    private static final String TYPE_STRANGER = "stranger";

    @Autowired
    private DataBoardMapper dataBoardMapper;

    @Autowired
    private AttendanceDailyMapper attendanceDailyMapper;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Autowired
    private IPresenceEmbedService presenceEmbedService;

    @Autowired
    private ProfileMatchMapper profileMatchMapper;

    @Autowired
    private CameraMapper cameraMapper;

    @Override
    public boolean updatePerson(Long personId, DataBoardPersonUpdateBo bo)
    {
        String personType = normalizePersonType(bo.getPersonType());
        if (TYPE_STRANGER.equals(personType))
        {
            throw new IllegalArgumentException("陌生人请在陌生人研判中处理");
        }
        return dataBoardMapper.updatePerson(personId, bo.getDisplayName(), personType,
                normalizeEmployeeNo(bo.getEmployeeNo()), bo.getNote(), null, null) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePerson(Long personId)
    {
        return dataBoardMapper.deletePerson(personId) > 0;
    }

    @Override
    public boolean updateSession(Long sessionId, DataBoardSessionUpdateBo bo)
    {
        String status = "open".equalsIgnoreCase(bo.getStatus()) ? "open" : "closed";
        return dataBoardMapper.updateSessionStatus(sessionId, status) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSession(Long sessionId)
    {
        return dataBoardMapper.deleteSession(sessionId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStranger(String trackKey, DataBoardStrangerUpdateBo bo)
    {
        Long strangerId = consolidateTracksToPerson(trackKey, bo.getRelatedTrackKeys());
        if (strangerId == null)
        {
            return false;
        }

        String personType = resolveStrangerPersonType(bo);
        String employeeNo = normalizeEmployeeNo(bo.getEmployeeNo());
        String displayName = defaultName(bo.getDisplayName());
        String note = !StringUtils.isEmpty(bo.getNote()) ? bo.getNote()
                : (bo.getTagsText() != null ? bo.getTagsText() : "");
        String phone = bo.getPhone() != null ? bo.getPhone() : "";
        String gender = normalizeGender(bo.getGender());

        if (!StringUtils.isEmpty(employeeNo))
        {
            Long existingId = dataBoardMapper.selectPersonByEmployeeNo(employeeNo);
            if (existingId != null && !existingId.equals(strangerId))
            {
                mergePerson(strangerId, existingId);
                return true;
            }
        }

        return dataBoardMapper.updatePerson(strangerId, displayName, personType, employeeNo, note, phone, gender) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean mergeStrangerToPerson(String trackKey, Long targetPersonId)
    {
        Long strangerId = ensurePersonForTrack(trackKey);
        if (strangerId == null || targetPersonId == null)
        {
            return false;
        }
        if (strangerId.equals(targetPersonId))
        {
            return true;
        }
        mergePerson(strangerId, targetPersonId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteStranger(String trackKey)
    {
        Long personId = dataBoardMapper.selectPersonIdByTrackKey(trackKey);
        int logs = dataBoardMapper.deleteBehaviorLogsByTrackKey(trackKey);
        int sessions = dataBoardMapper.deleteStrangerByTrackKey(trackKey);
        if (personId != null)
        {
            dataBoardMapper.deletePerson(personId);
        }
        return logs > 0 || sessions > 0 || personId != null;
    }

    @Override
    public boolean updateCameraConfig(Long cameraId, DataBoardCameraUpdateBo bo)
    {
        return dataBoardMapper.updateCamera(cameraId, bo.getDeviceName(), bo.getIsActive()) > 0;
    }

    @Override
    public String uploadFaceAndCreatePerson(String displayName, String personKind, String note,
            MultipartFile avatarFile) throws Exception
    {
        Path faceDir = storagePaths.faceLibraryRoot();
        Files.createDirectories(faceDir);

        String ext = extension(avatarFile.getOriginalFilename());
        String fileName = "face_" + IdUtils.fastSimpleUUID() + ext;
        Path savePath = faceDir.resolve(fileName);
        avatarFile.transferTo(savePath.toFile());

        String imageUrl = storagePaths.buildArchiveFaceUrl(fileName);
        dataBoardMapper.insertPerson(defaultName(displayName), normalizeRegistryType(personKind), null, note);
        Long personId = dataBoardMapper.selectLastPersonId();
        if (personId == null)
        {
            throw new RuntimeException("创建人员失败");
        }

        EmbeddingVectorVo faceEmbed = presenceEmbedService.embedImage("face", imageUrl);
        if (!Boolean.TRUE.equals(faceEmbed.getOk()) || faceEmbed.getEmbedding() == null)
        {
            throw new IllegalStateException("人脸向量抽取失败: "
                    + StringUtils.nvl(faceEmbed.getError(), "未知错误"));
        }
        profileMatchMapper.insertFaceProfile(personId, VectorLiteralUtil.toLiteral(faceEmbed.getEmbedding()), imageUrl,
                faceEmbed.getDetScore());
        return imageUrl;
    }

    /**
     * 将主 track 及人脸去重关联的 track 统一绑定到同一 persons 记录
     */
    private Long consolidateTracksToPerson(String primaryTrackKey, List<String> relatedTrackKeys)
    {
        Long primaryPersonId = ensurePersonForTrack(primaryTrackKey);
        if (primaryPersonId == null)
        {
            return null;
        }
        Set<String> trackKeys = new LinkedHashSet<>();
        trackKeys.add(primaryTrackKey);
        if (relatedTrackKeys != null)
        {
            for (String tk : relatedTrackKeys)
            {
                if (!StringUtils.isEmpty(tk))
                {
                    trackKeys.add(tk);
                }
            }
        }
        for (String trackKey : trackKeys)
        {
            if (primaryTrackKey.equals(trackKey))
            {
                dataBoardMapper.bindTrackToPerson(trackKey, primaryPersonId);
                continue;
            }
            Long otherPersonId = dataBoardMapper.selectPersonIdByTrackKey(trackKey);
            if (otherPersonId != null && !otherPersonId.equals(primaryPersonId))
            {
                dataBoardMapper.reassignSessionsPerson(otherPersonId, primaryPersonId);
                dataBoardMapper.reassignBehaviorLogsPerson(otherPersonId, primaryPersonId);
                dataBoardMapper.deletePerson(otherPersonId);
            }
            dataBoardMapper.bindTrackToPerson(trackKey, primaryPersonId);
        }
        return primaryPersonId;
    }

    /**
     * 确保 track 有关联的 person 记录；没有则自动创建 stranger 并绑定
     */
    private Long ensurePersonForTrack(String trackKey)
    {
        Long personId = dataBoardMapper.selectPersonIdByTrackKey(trackKey);
        if (personId != null)
        {
            return personId;
        }
        dataBoardMapper.insertPerson("未知访客", TYPE_STRANGER, null, null);
        Long newId = dataBoardMapper.selectLastPersonId();
        if (newId != null)
        {
            dataBoardMapper.bindTrackToPerson(trackKey, newId);
        }
        return newId;
    }

    private void mergePerson(Long fromPersonId, Long toPersonId)
    {
        attendanceDailyMapper.deletePersonDailyByPerson(fromPersonId);
        dataBoardMapper.reassignSessionsPerson(fromPersonId, toPersonId);
        dataBoardMapper.reassignBehaviorLogsPerson(fromPersonId, toPersonId);
        // 人脸/体态向量保留目标人员原有档案，不合并陌生人向量；删除源人员时 CASCADE 清理
        dataBoardMapper.deletePerson(fromPersonId);
    }

    private String resolveStrangerPersonType(DataBoardStrangerUpdateBo bo)
    {
        if (!StringUtils.isEmpty(bo.getPersonType()))
        {
            return normalizePersonType(bo.getPersonType());
        }
        if ("known".equalsIgnoreCase(bo.getIdentityType()))
        {
            return TYPE_STUDENT;
        }
        return TYPE_STRANGER;
    }

    private String normalizeGender(String gender)
    {
        if (gender == null)
        {
            return "2";
        }
        String value = String.valueOf(gender).trim();
        if ("0".equals(value) || "1".equals(value) || "2".equals(value))
        {
            return value;
        }
        return "2";
    }

    private String normalizePersonType(String raw)
    {
        if (TYPE_STAFF.equalsIgnoreCase(raw))
        {
            return TYPE_STAFF;
        }
        if (TYPE_STRANGER.equalsIgnoreCase(raw))
        {
            return TYPE_STRANGER;
        }
        if ("known".equalsIgnoreCase(raw))
        {
            return TYPE_STUDENT;
        }
        return TYPE_STUDENT;
    }

    private String normalizeRegistryType(String raw)
    {
        if (TYPE_STRANGER.equalsIgnoreCase(raw))
        {
            throw new IllegalArgumentException("陌生人请在陌生人研判中处理");
        }
        if (TYPE_STAFF.equalsIgnoreCase(raw))
        {
            return TYPE_STAFF;
        }
        return TYPE_STUDENT;
    }

    private String normalizeEmployeeNo(String employeeNo)
    {
        if (StringUtils.isEmpty(employeeNo))
        {
            return null;
        }
        String trimmed = employeeNo.trim();
        if (!DIGITS.matcher(trimmed).matches())
        {
            throw new IllegalArgumentException("学工号必须为纯数字");
        }
        return trimmed;
    }

    private String defaultName(String displayName)
    {
        return StringUtils.isEmpty(displayName) ? "未知访客" : displayName;
    }

    private String extension(String original)
    {
        if (StringUtils.isEmpty(original) || !original.contains("."))
        {
            return ".jpg";
        }
        String ext = original.substring(original.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        if (ext.length() > 8)
        {
            return ".jpg";
        }
        return ext;
    }

    @Override
    public List<CameraConfigVo> selectCameraList(CameraConfigVo camera)
    {
        return cameraMapper.selectCameraList(camera);
    }

    @Override
    public CameraConfigVo selectCameraById(Long cameraId)
    {
        return cameraMapper.selectCameraById(cameraId);
    }

    @Override
    public int insertCamera(CameraConfigVo camera)
    {
        return cameraMapper.insertCameraFull(camera);
    }

    @Override
    public int updateCamera(CameraConfigVo camera)
    {
        return cameraMapper.updateCameraFull(camera);
    }

    @Override
    public int deleteCameraByIds(Long[] cameraIds)
    {
        return cameraMapper.deleteCameraByIds(cameraIds);
    }
}
