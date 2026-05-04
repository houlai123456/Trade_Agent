package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {

    @Select("SELECT * FROM alert_record WHERE create_time >= #{since} ORDER BY create_time DESC")
    List<AlertRecord> selectRecentAlerts(@Param("since") LocalDateTime since);

    @Select("SELECT * FROM alert_record ORDER BY create_time DESC LIMIT #{limit}")
    List<AlertRecord> selectLatestAlerts(@Param("limit") int limit);
}
