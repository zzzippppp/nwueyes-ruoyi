package com.ruoyi.system.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.system.domain.bo.DataBoardCameraUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardPersonUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardSessionUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardStrangerUpdateBo;
import com.ruoyi.system.domain.vo.CameraConfigVo;

public interface IDataBoardManageService
{
    boolean updatePerson(Long personId, DataBoardPersonUpdateBo bo);

    boolean deletePerson(Long personId);

    boolean updateSession(Long sessionId, DataBoardSessionUpdateBo bo);

    boolean deleteSession(Long sessionId);

    boolean updateStranger(String trackKey, DataBoardStrangerUpdateBo bo);

    boolean mergeStrangerToPerson(String trackKey, Long targetPersonId);

    boolean deleteStranger(String trackKey);

    boolean updateCameraConfig(Long cameraId, DataBoardCameraUpdateBo bo);

    String uploadFaceAndCreatePerson(String displayName, String personKind, String note, MultipartFile avatarFile)
            throws Exception;

    List<CameraConfigVo> selectCameraList(CameraConfigVo camera);

    CameraConfigVo selectCameraById(Long cameraId);

    int insertCamera(CameraConfigVo camera);

    int updateCamera(CameraConfigVo camera);

    int deleteCameraByIds(Long[] cameraIds);
}
