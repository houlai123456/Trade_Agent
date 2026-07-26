package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session ORDER BY update_time DESC LIMIT #{limit}")
    List<ChatSession> listRecent(@Param("limit") int limit);

    @Select("SELECT * FROM chat_session WHERE session_id = #{sessionId}")
    ChatSession selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM chat_session WHERE title LIKE CONCAT('%', #{keyword}, '%') ORDER BY update_time DESC")
    List<ChatSession> search(@Param("keyword") String keyword);

    @Update("UPDATE chat_session SET title = #{title}, message_count = #{messageCount}, update_time = #{updateTime} WHERE session_id = #{sessionId}")
    int updateInfo(@Param("sessionId") String sessionId, @Param("title") String title,
                   @Param("messageCount") int messageCount, @Param("updateTime") LocalDateTime updateTime);
}
