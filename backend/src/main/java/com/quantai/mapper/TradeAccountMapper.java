package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.TradeAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TradeAccountMapper extends BaseMapper<TradeAccount> {

    /**
     * 根据用户ID查询账户
     */
    @Select("SELECT * FROM trade_account WHERE user_id = #{userId}")
    TradeAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询账户（悲观锁，FOR UPDATE）
     * 用于事务内需要修改账户余额的场景，防止并发超支
     */
    @Select("SELECT * FROM trade_account WHERE user_id = #{userId} FOR UPDATE")
    TradeAccount selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 更新可用资金（带乐观锁校验，防止并发覆盖）
     */
    @Update("UPDATE trade_account SET available_balance = #{balance}, update_time = NOW() WHERE user_id = #{userId}")
    int updateBalance(@Param("userId") Long userId, @Param("balance") java.math.BigDecimal balance);

    /**
     * 原子更新可用余额和冻结资金
     * @param availableDelta 可用资金变动（正=增加，负=扣减）
     * @param frozenDelta 冻结资金变动（正=冻结增加，负=解冻减少）
     */
    @Update("UPDATE trade_account SET " +
            "available_balance = available_balance + #{availableDelta}, " +
            "frozen_balance = frozen_balance + #{frozenDelta}, " +
            "update_time = NOW() " +
            "WHERE user_id = #{userId}")
    int freezeBalance(@Param("userId") Long userId,
                      @Param("availableDelta") java.math.BigDecimal availableDelta,
                      @Param("frozenDelta") java.math.BigDecimal frozenDelta);
}
