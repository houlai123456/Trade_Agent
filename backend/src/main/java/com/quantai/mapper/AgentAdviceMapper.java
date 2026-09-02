package com.quantai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quantai.model.entity.AgentAdvice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentAdviceMapper extends BaseMapper<AgentAdvice> {

    /**
     * 查询所有活跃的建议（需要监控的）
     */
    @Select("SELECT * FROM agent_advice WHERE status = 'ACTIVE' ORDER BY create_time DESC")
    List<AgentAdvice> selectActiveAdvices();

    /**
     * 查询某只股票的历史建议
     */
    @Select("SELECT * FROM agent_advice WHERE stock_code = #{stockCode} ORDER BY create_time DESC LIMIT #{limit}")
    List<AgentAdvice> selectByStockCode(String stockCode, int limit);

    /**
     * 统计某只股票的建议准确率（30天维度）
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN review_30d_correct = TRUE THEN 1 ELSE 0 END) as correct, " +
            "AVG(review_30d_return) as avg_return " +
            "FROM agent_advice " +
            "WHERE stock_code = #{stockCode} AND review_30d_correct IS NOT NULL")
    AccuracyStats selectAccuracyStats(String stockCode);

    /**
     * 准确率统计DTO
     */
    class AccuracyStats {
        public int total;
        public int correct;
        public Double avgReturn;
    }
}
