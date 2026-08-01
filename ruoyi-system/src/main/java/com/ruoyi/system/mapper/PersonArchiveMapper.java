package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.bo.PersonArchiveBo;
import com.ruoyi.system.domain.vo.PersonArchiveVo;

public interface PersonArchiveMapper
{
    List<PersonArchiveVo> selectPersonArchiveList(PersonArchiveBo query);

    PersonArchiveVo selectPersonArchiveById(@Param("personId") Long personId);

    List<String> selectFaceImageUrls(@Param("personId") Long personId);

    int insertPersonArchive(PersonArchiveBo bo);

    Long selectLastPersonId();

    int updatePersonArchive(PersonArchiveBo bo);

    int updateFaceImageUrl(@Param("personId") Long personId, @Param("faceImageUrl") String faceImageUrl);

    int deletePersonArchiveByIds(@Param("personIds") Long[] personIds);

    Long selectPersonIdByEmployeeNo(@Param("employeeNo") String employeeNo);
}
