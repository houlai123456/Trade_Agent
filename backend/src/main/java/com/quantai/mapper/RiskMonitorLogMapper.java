package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.RiskMonitorLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RiskMonitorLogMapper extends BaseMapper<RiskMonitorLog> {

    /**
     * 查询某个建议的监控历史
     */
    @Select("SELECT * FROM risk_monitor_log WHERE advice_id = #{adviceId} ORDER BY check_date DESC LIMIT #{limit}")
    List<RiskMonitorLog> selectByAdviceId(Long adviceId, int limit);

    /**
     * 查询今天已触发风险但未发送告警的记录
     */
    @Select("SELECT * FROM risk_monitor_log WHERE check_date = #{today} AND risk_triggered = TRUE AND alert_sent = FALSE")
    List<RiskMonitorLog> selectUnsent(LocalDate today);

    /**
     * 查询某只股票的最近监控记录
     */
    @Select("SELECT * FROM risk_monitor_log WHERE stock_code = #{stockCode} ORDER BY check_date DESC LIMIT 1")
    RiskMonitorLog selectLatestByStockCode(String stockCode);
}
