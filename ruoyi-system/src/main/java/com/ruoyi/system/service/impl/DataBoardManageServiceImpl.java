package com.ruoyi.system.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.mapper.AttendanceDailyMapper;
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

    @Override
    public boolean updatePerson(Long personId, DataBoardPersonUpdateBo bo)
    {
        return dataBoardMapper.updatePerson(personId, bo.getDisplayName(), normalizePersonType(bo.getPersonType()),
                normalizeEmployeeNo(bo.getEmployeeNo()), bo.getNote()) > 0;
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
        Long strangerId = dataBoardMapper.selectPersonIdByTrackKey(trackKey);
        if (strangerId == null)
        {
            return false;
        }
        String personType = normalizeRegistryType(bo.getPersonType());
        String employeeNo = normalizeEmployeeNo(bo.getEmployeeNo());
        String displayName = defaultName(bo.getDisplayName());

        if (!StringUtils.isEmpty(employeeNo))
        {
            Long existingId = dataBoardMapper.selectPersonByEmployeeNo(employeeNo);
            if (existingId != null && !existingId.equals(strangerId))
            {
                mergePerson(strangerId, existingId);
                return true;
            }
        }

        return dataBoardMapper.updatePerson(strangerId, displayName, personType, employeeNo, "") > 0;
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
    public boolean updateCamera(Long cameraId, DataBoardCameraUpdateBo bo)
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
        dataBoardMapper.insertPerson(defaultName(displayName), normalizePersonType(personKind), null, note);
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
        profileMatchMapper.insertFaceProfile(personId, VectorLiteralUtil.toLiteral(faceEmbed.getEmbedding()), imageUrl);
        return imageUrl;
    }

    private void mergePerson(Long fromPersonId, Long toPersonId)
    {
        attendanceDailyMapper.deletePersonDailyByPerson(fromPersonId);
        dataBoardMapper.reassignSessionsPerson(fromPersonId, toPersonId);
        dataBoardMapper.reassignBehaviorLogsPerson(fromPersonId, toPersonId);
        dataBoardMapper.reassignFaceProfilesPerson(fromPersonId, toPersonId);
        dataBoardMapper.reassignBodyProfilesPerson(fromPersonId, toPersonId);
        dataBoardMapper.deletePerson(fromPersonId);
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
}
