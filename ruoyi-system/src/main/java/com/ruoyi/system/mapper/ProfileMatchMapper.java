package com.ruoyi.system.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.BodySessionMatchVo;
import com.ruoyi.system.domain.vo.FaceMatchCandidateVo;

/**
 * 人脸/体态向量匹配与档案写入。
 */
public interface ProfileMatchMapper
{
  FaceMatchCandidateVo searchTopFaceMatch(@Param("embedding") String embeddingLiteral,
      @Param("maxDistance") float maxDistance);

  BodySessionMatchVo searchTopOpenSessionByBody(@Param("locationId") Long locationId,
      @Param("embedding") String embeddingLiteral, @Param("maxDistance") float maxDistance);

  int insertFaceProfile(@Param("personId") Long personId, @Param("embedding") String embeddingLiteral,
      @Param("imageUrl") String imageUrl);

  int insertBodyProfile(@Param("personId") Long personId, @Param("embedding") String embeddingLiteral,
      @Param("imageUrl") String imageUrl);
}
