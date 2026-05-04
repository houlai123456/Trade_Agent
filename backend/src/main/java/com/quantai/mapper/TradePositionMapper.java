package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.TradePosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TradePositionMapper extends BaseMapper<TradePosition> {

    /**
     * 查询用户指定股票的持仓
     */
    @Select("SELECT * FROM trade_position WHERE user_id = #{userId} AND code = #{code}")
    TradePosition selectByUserAndCode(@Param("userId") Long userId, @Param("code") String code);

    /**
     * 查询用户所有持仓
     */
    @Select("SELECT * FROM trade_position WHERE user_id = #{userId} AND quantity > 0 ORDER BY update_time DESC")
    List<TradePosition> selectByUserId(@Param("userId") Long userId);

    /**
     * 更新可用数量（用于冻结/解冻）
     */
    @Update("UPDATE trade_position SET available_quantity = available_quantity + #{delta}, update_time = NOW() WHERE id = #{id}")
    int updateAvailableQuantity(@Param("id") Long id, @Param("delta") int delta);
}
