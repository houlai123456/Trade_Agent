package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.StockKline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockKlineMapper extends BaseMapper<StockKline> {

    @Select("SELECT * FROM stock_kline WHERE code = #{code} AND period = #{period} " +
            "AND date >= #{startDate} ORDER BY date ASC")
    List<StockKline> selectKlineByCodeAndPeriod(@Param("code") String code,
                                                 @Param("period") String period,
                                                 @Param("startDate") LocalDate startDate);

    @Select("SELECT * FROM stock_kline WHERE code = #{code} AND period = #{period} " +
            "ORDER BY date DESC LIMIT #{limit}")
    List<StockKline> selectLatestKline(@Param("code") String code,
                                       @Param("period") String period,
                                       @Param("limit") int limit);

    @Select("SELECT * FROM stock_kline WHERE code = #{code} AND period = 'daily' " +
            "AND date = #{date} LIMIT 1")
    StockKline selectByDate(@Param("code") String code, @Param("date") LocalDate date);
}
