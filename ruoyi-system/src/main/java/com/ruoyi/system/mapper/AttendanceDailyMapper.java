package com.ruoyi.system.mapper;

import java.sql.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.AttendanceDailyStatsVo;
import com.ruoyi.system.domain.vo.PersonDailyAttendanceVo;

public interface AttendanceDailyMapper
{
    int upsertOnEnter(@Param("statDate") Date statDate, @Param("personId") Long personId,
            @Param("locationId") Long locationId, @Param("eventTime") java.util.Date eventTime,
            @Param("sessionId") Long sessionId);

    int updateOnExit(@Param("statDate") Date statDate, @Param("personId") Long personId,
            @Param("eventTime") java.util.Date eventTime, @Param("dwellSeconds") Integer dwellSeconds);

    int updateStatus(@Param("statDate") Date statDate, @Param("personId") Long personId,
            @Param("attendanceStatus") String attendanceStatus, @Param("sessionId") Long sessionId);

    List<PersonDailyAttendanceVo> selectDailyList(@Param("statDate") Date statDate,
            @Param("locationId") Long locationId, @Param("personType") String personType,
            @Param("displayName") String displayName, @Param("employeeNo") String employeeNo,
            @Param("attendanceStatus") String attendanceStatus, @Param("limit") int limit);

    int countRegistryPersons();

    int countAttendedToday(@Param("statDate") Date statDate, @Param("includeStranger") boolean includeStranger);

    int countPresentPersons(@Param("personTypeFilter") String personTypeFilter);

    int countStrangerPersons();

    AttendanceDailyStatsVo selectDailyStats(@Param("statDate") Date statDate);

    int upsertDailyStats(@Param("statDate") Date statDate, @Param("totalRegistry") int totalRegistry,
            @Param("attendedCount") int attendedCount, @Param("attendanceRate") java.math.BigDecimal rate,
            @Param("strangerTotal") int strangerTotal);

    int reassignPersonDaily(@Param("fromPersonId") Long fromPersonId, @Param("toPersonId") Long toPersonId);

    int deletePersonDailyByPerson(@Param("personId") Long personId);
}
