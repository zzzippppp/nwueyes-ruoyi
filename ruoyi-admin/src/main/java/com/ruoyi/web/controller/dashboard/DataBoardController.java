package com.ruoyi.web.controller.dashboard;

import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.bo.DataBoardSessionFilterBo;
import com.ruoyi.system.domain.bo.DataBoardCameraUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardPersonUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardSessionUpdateBo;
import com.ruoyi.system.domain.bo.DataBoardStrangerUpdateBo;
import com.ruoyi.system.domain.vo.DataBoardSummaryVo;
import com.ruoyi.system.storage.PresenceStoragePaths;
import com.ruoyi.system.service.IDataBoardService;
import com.ruoyi.system.service.IDataBoardManageService;

/**
 * 数据看板
 */
@RestController
@RequestMapping("/dashboard/data-board")
public class DataBoardController
{
    @Autowired
    private IDataBoardService dataBoardService;

    @Autowired
    private IDataBoardManageService dataBoardManageService;

    @Autowired
    private PresenceStoragePaths storagePaths;

    /**
     * 看板汇总（卡片 + 图表 + 最近记录）
     *
     * @param statDate 统计日期 yyyy-MM-dd，默认当天
     * @param cameraId 地点 ID，可选
     * @param recentLimit 最近记录条数，默认 10
     */
    @GetMapping("/summary")
    public AjaxResult summary(
            @RequestParam(value = "statDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate statDate,
            @RequestParam(value = "beginDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate beginDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "cameraId", required = false) Long cameraId,
            @RequestParam(value = "recentLimit", required = false, defaultValue = "10") Integer recentLimit,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "employeeNo", required = false) String employeeNo,
            @RequestParam(value = "personType", required = false) String personType,
            @RequestParam(value = "sessionStatus", required = false) String sessionStatus,
            @RequestParam(value = "beginTime", required = false) String beginTime,
            @RequestParam(value = "endTime", required = false) String endTime)
    {
        DataBoardSessionFilterBo filter = new DataBoardSessionFilterBo();
        if (!StringUtils.isEmpty(displayName))
        {
            filter.setDisplayName(displayName);
        }
        if (!StringUtils.isEmpty(employeeNo))
        {
            filter.setEmployeeNo(employeeNo);
        }
        if (!StringUtils.isEmpty(personType))
        {
            filter.setPersonType(personType);
        }
        if (!StringUtils.isEmpty(sessionStatus))
        {
            filter.setSessionStatus(sessionStatus);
        }
        if (!StringUtils.isEmpty(beginTime))
        {
            filter.setBeginTime(beginTime);
        }
        if (!StringUtils.isEmpty(endTime))
        {
            filter.setEndTime(endTime);
        }
        DataBoardSummaryVo data = dataBoardService.getSummary(statDate, beginDate, endDate, cameraId,
                recentLimit == null ? 10 : recentLimit, filter);
        return AjaxResult.success(data);
    }

    @PutMapping("/persons/{personId}")
    public AjaxResult updatePerson(@PathVariable("personId") Long personId, @RequestBody DataBoardPersonUpdateBo bo)
    {
        return dataBoardManageService.updatePerson(personId, bo) ? AjaxResult.success() : AjaxResult.error("更新失败");
    }

    @DeleteMapping("/persons/{personId}")
    public AjaxResult deletePerson(@PathVariable("personId") Long personId)
    {
        return dataBoardManageService.deletePerson(personId) ? AjaxResult.success() : AjaxResult.error("删除失败");
    }

    @PutMapping("/sessions/{sessionId}")
    public AjaxResult updateSession(@PathVariable("sessionId") Long sessionId, @RequestBody DataBoardSessionUpdateBo bo)
    {
        return dataBoardManageService.updateSession(sessionId, bo) ? AjaxResult.success() : AjaxResult.error("更新失败");
    }

    @DeleteMapping("/sessions/{sessionId}")
    public AjaxResult deleteSession(@PathVariable("sessionId") Long sessionId)
    {
        return dataBoardManageService.deleteSession(sessionId) ? AjaxResult.success() : AjaxResult.error("删除失败");
    }

    @PutMapping("/strangers/{trackKey}")
    public AjaxResult updateStranger(@PathVariable("trackKey") String trackKey, @RequestBody DataBoardStrangerUpdateBo bo)
    {
        return dataBoardManageService.updateStranger(trackKey, bo) ? AjaxResult.success() : AjaxResult.error("更新失败");
    }

    @DeleteMapping("/strangers/{trackKey}")
    public AjaxResult deleteStranger(@PathVariable("trackKey") String trackKey)
    {
        return dataBoardManageService.deleteStranger(trackKey) ? AjaxResult.success() : AjaxResult.error("删除失败");
    }

    @PutMapping("/cameras/{cameraId}")
    public AjaxResult updateCamera(@PathVariable("cameraId") Long cameraId, @RequestBody DataBoardCameraUpdateBo bo)
    {
        return dataBoardManageService.updateCamera(cameraId, bo) ? AjaxResult.success() : AjaxResult.error("更新失败");
    }

    @PostMapping("/persons/upload-face")
    public AjaxResult uploadFace(@RequestParam("displayName") String displayName,
            @RequestParam(value = "personKind", required = false, defaultValue = "known") String personKind,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam("avatarfile") MultipartFile avatarFile) throws Exception
    {
        String imgUrl = dataBoardManageService.uploadFaceAndCreatePerson(displayName, personKind, note, avatarFile);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("imgUrl", imgUrl);
        return ajax;
    }

    @GetMapping("/file/{category}/{fileName:.+}")
    @Anonymous
    public ResponseEntity<Resource> serveFile(@PathVariable("category") String category,
            @PathVariable("fileName") String fileName) throws Exception
    {
        Path filePath = "body".equals(category)
                ? storagePaths.resolveArchiveBodyFile(fileName)
                : storagePaths.resolveArchiveFaceFile(fileName);
        Path allowedRoot = "body".equals(category)
                ? storagePaths.bodyLibraryRoot()
                : storagePaths.faceLibraryRoot();
        if (!storagePaths.isUnderRoot(filePath, allowedRoot) || !Files.exists(filePath))
        {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(filePath.toUri());
        return ResponseEntity.ok().contentType(resolveImageMediaType(fileName)).body(resource);
    }

    private MediaType resolveImageMediaType(String fileName)
    {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".png"))
        {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif"))
        {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp"))
        {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
