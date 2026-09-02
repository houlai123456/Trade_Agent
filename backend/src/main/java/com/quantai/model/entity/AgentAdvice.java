package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_advice")
public class AgentAdvice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stockCode;
    private String stockName;
    private String adviceType; // BUY, SELL, HOLD
    private BigDecimal confidenceScore;

    // 价格相关
    private BigDecimal advicePrice;
    private BigDecimal targetPrice;
    private BigDecimal stopLossPrice;
    private BigDecimal expectedReturn;

    // 分析维度得分
    private BigDecimal fundamentalScore;
    private BigDecimal technicalScore;
    private BigDecimal sentimentScore;

    // 风险条件
    private String riskConditions; // JSON格式

    // 状态跟踪
    private String status; // ACTIVE, TRIGGERED, EXPIRED, CLOSED
    private String triggeredCondition;
    private LocalDateTime triggerTime;

    // 回测数据
    private BigDecimal review7dPrice;
    private BigDecimal review7dReturn;
    private Boolean review7dCorrect;
    private BigDecimal review30dPrice;
    private BigDecimal review30dReturn;
    private Boolean review30dCorrect;

    // 完整报告
    private String fullReport;
    private String agentVersion;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
