package com.ruoyi.system.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.bo.PersonArchiveBo;
import com.ruoyi.system.domain.vo.EmbeddingVectorVo;
import com.ruoyi.system.domain.vo.PersonArchiveVo;
import com.ruoyi.system.mapper.PersonArchiveMapper;
import com.ruoyi.system.mapper.ProfileMatchMapper;
import com.ruoyi.system.service.IPersonArchiveService;
import com.ruoyi.system.service.IPresenceEmbedService;
import com.ruoyi.system.storage.PresenceStoragePaths;
import com.ruoyi.system.util.VectorLiteralUtil;

@Service
public class PersonArchiveServiceImpl implements IPersonArchiveService
{
    @Autowired
    private PersonArchiveMapper personArchiveMapper;

    @Autowired
    private ProfileMatchMapper profileMatchMapper;

    @Autowired
    private IPresenceEmbedService presenceEmbedService;

    @Autowired
    private PresenceStoragePaths storagePaths;

    @Override
    public List<PersonArchiveVo> selectPersonArchiveList(PersonArchiveBo query)
    {
        return personArchiveMapper.selectPersonArchiveList(query);
    }

    @Override
    public PersonArchiveVo selectPersonArchiveById(Long personId)
    {
        PersonArchiveVo vo = personArchiveMapper.selectPersonArchiveById(personId);
        if (vo != null)
        {
            vo.setFaceImageUrls(personArchiveMapper.selectFaceImageUrls(personId));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPersonArchive(PersonArchiveBo bo, MultipartFile faceFile) throws Exception
    {
        normalizeBo(bo);
        if (StringUtils.isEmpty(bo.getDisplayName()))
        {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (StringUtils.isEmpty(bo.getEmployeeNo()))
        {
            throw new IllegalArgumentException("学工号不能为空");
        }
        Long exists = personArchiveMapper.selectPersonIdByEmployeeNo(bo.getEmployeeNo());
        if (exists != null)
        {
            throw new IllegalArgumentException("学工号已存在: " + bo.getEmployeeNo());
        }
        if (faceFile == null || faceFile.isEmpty())
        {
            throw new IllegalArgumentException("人脸照片不能为空");
        }
        int rows = personArchiveMapper.insertPersonArchive(bo);
        Long personId = personArchiveMapper.selectLastPersonId();
        if (rows <= 0 || personId == null)
        {
            throw new IllegalStateException("创建人员失败");
        }
        bo.setPersonId(personId);
        attachFace(personId, faceFile);
        return rows;
    }

    @Override
    public int updatePersonArchive(PersonArchiveBo bo)
    {
        if (bo.getPersonId() == null)
        {
            throw new IllegalArgumentException("personId 不能为空");
        }
        normalizeBo(bo);
        if (StringUtils.isEmpty(bo.getDisplayName()))
        {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (StringUtils.isEmpty(bo.getEmployeeNo()))
        {
            throw new IllegalArgumentException("学工号不能为空");
        }
        Long exists = personArchiveMapper.selectPersonIdByEmployeeNo(bo.getEmployeeNo());
        if (exists != null && !exists.equals(bo.getPersonId()))
        {
            throw new IllegalArgumentException("学工号已存在: " + bo.getEmployeeNo());
        }
        return personArchiveMapper.updatePersonArchive(bo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePersonArchiveByIds(Long[] personIds)
    {
        if (personIds == null || personIds.length == 0)
        {
            return 0;
        }
        return personArchiveMapper.deletePersonArchiveByIds(personIds);
    }

    private void attachFace(Long personId, MultipartFile faceFile) throws Exception
    {
        Path faceDir = storagePaths.faceLibraryRoot();
        Files.createDirectories(faceDir);
        String ext = extension(faceFile.getOriginalFilename());
        String fileName = "face_" + IdUtils.fastSimpleUUID() + ext;
        Path savePath = faceDir.resolve(fileName);
        faceFile.transferTo(savePath.toFile());
        String imageUrl = storagePaths.buildArchiveFaceUrl(fileName);

        EmbeddingVectorVo faceEmbed = presenceEmbedService.embedImage("face", imageUrl);
        if (!Boolean.TRUE.equals(faceEmbed.getOk()) || faceEmbed.getEmbedding() == null)
        {
            throw new IllegalStateException("人脸向量抽取失败: "
                    + (faceEmbed == null ? "null" : faceEmbed.getError()));
        }
        profileMatchMapper.insertFaceProfile(personId, VectorLiteralUtil.toLiteral(faceEmbed.getEmbedding()), imageUrl);
        personArchiveMapper.updateFaceImageUrl(personId, imageUrl);
    }

    private void normalizeBo(PersonArchiveBo bo)
    {
        if (bo == null)
        {
            return;
        }
        String type = StringUtils.nvl(bo.getPersonType(), "student").toLowerCase();
        if ("known".equals(type))
        {
            type = "student";
        }
        if (!"student".equals(type) && !"staff".equals(type) && !"stranger".equals(type))
        {
            type = "student";
        }
        bo.setPersonType(type);
        if (StringUtils.isEmpty(bo.getStatus()))
        {
            bo.setStatus("0");
        }
        if (StringUtils.isEmpty(bo.getGender()))
        {
            bo.setGender("0");
        }
        if (bo.getEmployeeNo() != null)
        {
            bo.setEmployeeNo(bo.getEmployeeNo().trim());
            if (bo.getEmployeeNo().isEmpty())
            {
                bo.setEmployeeNo(null);
            }
        }
    }

    private String extension(String name)
    {
        if (name == null)
        {
            return ".jpg";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0)
        {
            return ".jpg";
        }
        return name.substring(dot).toLowerCase();
    }
}
