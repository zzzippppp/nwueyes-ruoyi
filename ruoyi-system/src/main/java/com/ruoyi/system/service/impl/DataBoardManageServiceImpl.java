package com.ruoyi.system.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.bo.DataBoardLocationUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardPersonUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardSessionUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardStrangerUpdateBo;
import com.ruoyi.system.mapper.DataBoardMapper;
import com.ruoyi.system.service.IDataBoardManageService;

@Service
public class DataBoardManageServiceImpl implements IDataBoardManageService
{
    private static final String IDENTITY_KNOWN = "known";

    private static final String IDENTITY_STRANGER = "stranger";

    @Autowired
    private DataBoardMapper dataBoardMapper;

    @Override
    public boolean updatePerson(Long personId, DataBoardPersonUpdateBo bo)
    {
        return dataBoardMapper.updatePerson(personId, bo.getDisplayName(), normalizePersonKind(bo.getPersonKind()),
                bo.getTagsText(), bo.getNote()) > 0;
    }

    @Override
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
    public boolean deleteSession(Long sessionId)
    {
        return dataBoardMapper.deleteSession(sessionId) > 0;
    }

    @Override
    public boolean updateStranger(String trackKey, DataBoardStrangerUpdateBo bo)
    {
        if (IDENTITY_KNOWN.equalsIgnoreCase(bo.getIdentityType()))
        {
            dataBoardMapper.insertPerson(defaultName(bo.getDisplayName()), IDENTITY_KNOWN, bo.getTagsText(), "");
            Long personId = dataBoardMapper.selectLastPersonId();
            if (personId == null)
            {
                return false;
            }
            return dataBoardMapper.bindTrackToPerson(trackKey, personId) > 0;
        }
        return dataBoardMapper.clearStrangerPerson(trackKey) >= 0;
    }

    @Override
    public boolean deleteStranger(String trackKey)
    {
        return dataBoardMapper.deleteStrangerByTrackKey(trackKey) >= 0;
    }

    @Override
    public boolean updateLocation(Long locationId, DataBoardLocationUpdateBo bo)
    {
        return dataBoardMapper.updateLocation(locationId, bo.getLocationName(), bo.getIsActive()) > 0;
    }

    @Override
    public String uploadFaceAndCreatePerson(String displayName, String personKind, String tagsText, String note, MultipartFile avatarFile)
            throws Exception
    {
        Path projectDir = resolveStorageRoot();
        Path faceDir = projectDir.resolve("face_library");
        Path bodyDir = projectDir.resolve("body_library");
        Files.createDirectories(faceDir);
        Files.createDirectories(bodyDir);

        String ext = extension(avatarFile.getOriginalFilename());
        String fileName = "face_" + IdUtils.fastSimpleUUID() + ext;
        Path savePath = faceDir.resolve(fileName);
        avatarFile.transferTo(savePath.toFile());

        String imageUrl = "/dashboard/data-board/file/face/" + fileName;
        dataBoardMapper.insertPerson(defaultName(displayName), normalizePersonKind(personKind), tagsText, note);
        Long personId = dataBoardMapper.selectLastPersonId();
        if (personId == null)
        {
            throw new RuntimeException("创建人员失败");
        }
        dataBoardMapper.insertFaceProfile(personId, imageUrl);
        return imageUrl;
    }

    private String normalizePersonKind(String personKind)
    {
        if (IDENTITY_KNOWN.equalsIgnoreCase(personKind))
        {
            return IDENTITY_KNOWN;
        }
        return IDENTITY_STRANGER;
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

    private Path resolveStorageRoot()
    {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path cursor = cwd;
        for (int i = 0; i < 6 && cursor != null; i++)
        {
            if (Files.exists(cursor.resolve("face_library")) || Files.exists(cursor.resolve("body_library")))
            {
                return cursor;
            }
            if ("ruoyi".equalsIgnoreCase(cursor.getFileName().toString()) && cursor.getParent() != null)
            {
                return cursor.getParent();
            }
            if ("ruoyi-admin".equalsIgnoreCase(cursor.getFileName().toString()) && cursor.getParent() != null
                    && cursor.getParent().getParent() != null)
            {
                return cursor.getParent().getParent();
            }
            cursor = cursor.getParent();
        }
        return cwd;
    }
}
