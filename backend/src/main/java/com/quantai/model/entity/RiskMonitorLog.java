package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("risk_monitor_log")
public class RiskMonitorLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adviceId;
    private String stockCode;
    private LocalDate checkDate;

    // 当日市场数据
    private BigDecimal currentPrice;
    private BigDecimal ma5;
    private BigDecimal ma20;
    private Long volume;

    // 风险检查结果
    private Boolean riskTriggered;
    private String triggeredRules; // JSON数组
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    // 通知状态
    private Boolean alertSent;
    private String alertChannel;

    private LocalDateTime createTime;
}
