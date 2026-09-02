package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE users SET api_used_today = api_used_today + 1 WHERE id = #{userId}")
    int incrementApiUsage(@Param("userId") Long userId);

    @Update("UPDATE users SET api_used_today = 0, quota_reset_date = #{resetDate} WHERE quota_reset_date < #{resetDate} OR quota_reset_date IS NULL")
    int resetDailyQuota(@Param("resetDate") LocalDate resetDate);
}
