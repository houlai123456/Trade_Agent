package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.ConditionOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConditionOrderMapper extends BaseMapper<ConditionOrder> {

    @Select("SELECT * FROM condition_order WHERE user_id = #{userId} AND status = 'PENDING' ORDER BY create_time ASC")
    List<ConditionOrder> selectPendingOrders(@Param("userId") Long userId);

    @Select("SELECT * FROM condition_order WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<ConditionOrder> selectByUserId(@Param("userId") Long userId);
}
