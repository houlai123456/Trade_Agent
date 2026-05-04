package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.TradeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    /**
     * 查询用户交易流水，按时间倒序
     */
    @Select("SELECT * FROM trade_order WHERE user_id = #{userId} ORDER BY trade_time DESC")
    List<TradeOrder> selectByUserId(@Param("userId") Long userId);

    /**
     * 按状态查询订单
     */
    @Select("SELECT * FROM trade_order WHERE user_id = #{userId} AND status = #{status} ORDER BY trade_time DESC")
    List<TradeOrder> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
}
