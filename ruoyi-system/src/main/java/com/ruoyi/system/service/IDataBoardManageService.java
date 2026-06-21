package com.ruoyi.system.service;

import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.system.domain.bo.DataBoardCameraUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardPersonUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardSessionUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardStrangerUpdateBo;

public interface IDataBoardManageService
{
    boolean updatePerson(Long personId, DataBoardPersonUpdateBo bo);

    boolean deletePerson(Long personId);

    boolean updateSession(Long sessionId, DataBoardSessionUpdateBo bo);

    boolean deleteSession(Long sessionId);

    boolean updateStranger(String trackKey, DataBoardStrangerUpdateBo bo);

    boolean deleteStranger(String trackKey);

    boolean updateCamera(Long cameraId, DataBoardCameraUpdateBo bo);

    String uploadFaceAndCreatePerson(String displayName, String personKind, String note, MultipartFile avatarFile)
            throws Exception;
}
