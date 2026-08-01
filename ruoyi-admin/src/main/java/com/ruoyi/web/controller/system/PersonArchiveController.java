package com.ruoyi.web.controller.system;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.bo.PersonArchiveBo;
import com.ruoyi.system.domain.vo.PersonArchiveVo;
import com.ruoyi.system.service.IPersonArchiveService;

/**
 * 人员档案（人脸库）
 */
@RestController
@RequestMapping("/system/person")
public class PersonArchiveController extends BaseController
{
    @Autowired
    private IPersonArchiveService personArchiveService;

    @PreAuthorize("@ss.hasPermi('system:person:list')")
    @GetMapping("/list")
    public TableDataInfo list(PersonArchiveBo query)
    {
        startPage();
        List<PersonArchiveVo> list = personArchiveService.selectPersonArchiveList(query);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('system:person:query')")
    @GetMapping("/{personId}")
    public AjaxResult getInfo(@PathVariable Long personId)
    {
        return success(personArchiveService.selectPersonArchiveById(personId));
    }

    @PreAuthorize("@ss.hasPermi('system:person:add')")
    @Log(title = "人员档案", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(
            @RequestParam("displayName") String displayName,
            @RequestParam(value = "personType", required = false) String personType,
            @RequestParam(value = "employeeNo", required = false) String employeeNo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "faceFile", required = false) MultipartFile faceFile) throws Exception
    {
        PersonArchiveBo bo = new PersonArchiveBo();
        bo.setDisplayName(displayName);
        bo.setPersonType(personType);
        bo.setEmployeeNo(employeeNo);
        bo.setStatus(status);
        bo.setNote(note);
        bo.setPhone(phone);
        bo.setGender(gender);
        return toAjax(personArchiveService.insertPersonArchive(bo, faceFile));
    }

    @PreAuthorize("@ss.hasPermi('system:person:edit')")
    @Log(title = "人员档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PersonArchiveBo bo)
    {
        return toAjax(personArchiveService.updatePersonArchive(bo));
    }

    @PreAuthorize("@ss.hasPermi('system:person:remove')")
    @Log(title = "人员档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{personIds}")
    public AjaxResult remove(@PathVariable Long[] personIds)
    {
        return toAjax(personArchiveService.deletePersonArchiveByIds(personIds));
    }
}
