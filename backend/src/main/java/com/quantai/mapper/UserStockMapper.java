package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.UserStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserStockMapper extends BaseMapper<UserStock> {

    @Select("SELECT * FROM user_stock WHERE user_id = #{userId} ORDER BY sort_order ASC")
    List<UserStock> selectByUserId(@Param("userId") Long userId);
}
