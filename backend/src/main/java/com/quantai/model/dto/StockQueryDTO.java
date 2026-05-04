package com.quantai.model.dto;

import lombok.Data;

/**
 * 股票查询参数
 */
@Data
public class StockQueryDTO {
    /** 搜索关键词（代码或名称） */
    private String keyword;

    /** K线周期：DAY-日K，WEEK-周K，MONTH-月K */
    private String period = "DAY";

    /** 数据条数 */
    private Integer limit = 120;
}
