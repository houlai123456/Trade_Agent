package com.quantai.mapper;

import com.quantai.model.entity.SuggestionTracking;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent建议追踪Mapper（回测系统）
 */
@Mapper
public interface SuggestionTrackingMapper {

    /**
     * 保存建议
     */
    @Insert("""
            INSERT INTO suggestion_tracking (
                stock_code, stock_name, suggestion, confidence,
                suggested_at, suggested_price, target_price,
                weighted_score, fundamental_score, technical_score, sentiment_score, risk_score,
                risk_override, original_suggestion, backtest_status
            ) VALUES (
                #{stockCode}, #{stockName}, #{suggestion}, #{confidence},
                #{suggestedAt}, #{suggestedPrice}, #{targetPrice},
                #{weightedScore}, #{fundamentalScore}, #{technicalScore}, #{sentimentScore}, #{riskScore},
                #{riskOverride}, #{originalSuggestion}, 'PENDING'
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SuggestionTracking tracking);

    /**
     * 更新回测数据（7天）
     */
    @Update("""
            UPDATE suggestion_tracking SET
                actual_price_7d = #{actualPrice7d},
                return_7d = #{return7d},
                accuracy_7d = #{accuracy7d},
                backtest_status = 'PARTIAL',
                updated_at = NOW()
            WHERE id = #{id}
            """)
    void update7dBacktest(SuggestionTracking tracking);

    /**
     * 更新回测数据（30天）
     */
    @Update("""
            UPDATE suggestion_tracking SET
                actual_price_30d = #{actualPrice30d},
                return_30d = #{return30d},
                accuracy_30d = #{accuracy30d},
                backtest_status = 'COMPLETED',
                updated_at = NOW()
            WHERE id = #{id}
            """)
    void update30dBacktest(SuggestionTracking tracking);

    /**
     * 查询待回测的7天建议（超过7天未回测）
     */
    @Select("""
            SELECT * FROM suggestion_tracking
            WHERE backtest_status = 'PENDING'
              AND suggested_at <= #{deadline}
            ORDER BY suggested_at ASC
            LIMIT #{limit}
            """)
    List<SuggestionTracking> findPending7dBacktest(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * 查询待回测的30天建议（超过30天未回测）
     */
    @Select("""
            SELECT * FROM suggestion_tracking
            WHERE backtest_status = 'PARTIAL'
              AND suggested_at <= #{deadline}
            ORDER BY suggested_at ASC
            LIMIT #{limit}
            """)
    List<SuggestionTracking> findPending30dBacktest(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * 按股票代码查询历史建议（用于自适应权重）
     */
    @Select("""
            SELECT * FROM suggestion_tracking
            WHERE stock_code = #{stockCode}
              AND backtest_status IN ('PARTIAL', 'COMPLETED')
            ORDER BY suggested_at DESC
            LIMIT #{limit}
            """)
    List<SuggestionTracking> findByStockCode(@Param("stockCode") String stockCode, @Param("limit") int limit);

    /**
     * 统计某维度的准确率（按时间窗口）
     */
    @Select("""
            SELECT AVG(CASE WHEN accuracy_7d = TRUE THEN 1.0 ELSE 0.0 END) as accuracy_7d,
                   AVG(CASE WHEN accuracy_30d = TRUE THEN 1.0 ELSE 0.0 END) as accuracy_30d
            FROM suggestion_tracking
            WHERE suggested_at >= #{startDate}
              AND backtest_status = 'COMPLETED'
            """)
    AccuracyStats calculateAccuracy(@Param("startDate") LocalDateTime startDate);

    /**
     * 按股票统计准确率（用于自适应权重）
     */
    @Select("""
            SELECT
                AVG(CASE WHEN accuracy_7d = TRUE THEN 1.0 ELSE 0.0 END) as accuracy_7d,
                AVG(CASE WHEN accuracy_30d = TRUE THEN 1.0 ELSE 0.0 END) as accuracy_30d,
                COUNT(*) as total_count
            FROM suggestion_tracking
            WHERE stock_code = #{stockCode}
              AND backtest_status = 'COMPLETED'
              AND suggested_at >= #{startDate}
            """)
    AccuracyStats calculateAccuracyByStock(@Param("stockCode") String stockCode, @Param("startDate") LocalDateTime startDate);

    /**
     * 准确率统计DTO
     */
    class AccuracyStats {
        public Double accuracy7d;
        public Double accuracy30d;
        public Integer totalCount;
    }
}
