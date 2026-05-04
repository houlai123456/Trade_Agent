package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.News;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NewsMapper extends BaseMapper<News> {

    @Select("SELECT * FROM news WHERE stock_code = #{stockCode} ORDER BY publish_time DESC LIMIT #{limit}")
    List<News> selectByStockCode(@Param("stockCode") String stockCode, @Param("limit") int limit);
}
