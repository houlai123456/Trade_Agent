package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.WatchRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WatchRuleMapper extends BaseMapper<WatchRule> {

    @Select("SELECT * FROM watch_rule WHERE user_id = #{userId} AND enabled = 1 ORDER BY create_time DESC")
    List<WatchRule> selectEnabledRules(@Param("userId") Long userId);

    @Select("SELECT * FROM watch_rule WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<WatchRule> selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE watch_rule SET last_triggered_time = #{time} WHERE id = #{id}")
    int updateLastTriggeredTime(@Param("id") Long id, @Param("time") LocalDateTime time);
}
