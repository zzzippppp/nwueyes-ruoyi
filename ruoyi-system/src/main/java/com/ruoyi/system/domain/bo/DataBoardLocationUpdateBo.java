package com.ruoyi.system.domain.bo;

/**
 * 点位编辑请求
 */
public class DataBoardLocationUpdateBo
{
    private String locationName;

    private Boolean isActive;

    public String getLocationName()
    {
        return locationName;
    }

    public void setLocationName(String locationName)
    {
        this.locationName = locationName;
    }

    public Boolean getIsActive()
    {
        return isActive;
    }

    public void setIsActive(Boolean isActive)
    {
        this.isActive = isActive;
    }
}
