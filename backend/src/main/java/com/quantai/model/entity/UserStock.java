package com.quantai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自选股
 */
@Data
@TableName("user_stock")
public class UserStock {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（单用户模式，固定为1） */
    private Long userId;

    /** 股票代码 */
    private String code;

    /** 添加时的备注 */
    private String remark;

    /** 排序序号 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
