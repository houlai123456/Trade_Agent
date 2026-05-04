package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 股票基本信息
 */
@Data
@TableName("stock_info")
public class StockInfo {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 股票代码，如 sh600519 */
    private String code;

    /** 股票名称 */
    private String name;

    /** 所属行业 */
    private String industry;

    /** 上市地：SH-上海，SZ-深圳 */
    private String exchange;

    /** 总市值 */
    private Double totalMarketCap;

    /** 流通市值 */
    private Double circulatingMarketCap;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
