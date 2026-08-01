package com.ruoyi.system.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.system.domain.bo.PersonArchiveBo;
import com.ruoyi.system.domain.vo.PersonArchiveVo;

public interface IPersonArchiveService
{
    List<PersonArchiveVo> selectPersonArchiveList(PersonArchiveBo query);

    PersonArchiveVo selectPersonArchiveById(Long personId);

    int insertPersonArchive(PersonArchiveBo bo, MultipartFile faceFile) throws Exception;

    int updatePersonArchive(PersonArchiveBo bo);

    int deletePersonArchiveByIds(Long[] personIds);
}
