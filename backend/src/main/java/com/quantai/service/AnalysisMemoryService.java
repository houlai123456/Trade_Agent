package com.quantai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantai.model.dto.AnalysisSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 分析记忆服务 - 会话记忆管理
 * 职责：
 * 1. 缓存完整分析结果（Redis，TTL=1小时）
 * 2. 支持按股票代码+分析类型召回
 * 3. 节省Token成本，提升响应速度
 *
 * 缓存策略：
 * - Key格式: analysis:snapshot:{stockCode}:{analysisType}
 * - TTL: 3600秒（1小时）
 * - 仅缓存完整分析（FULL），单维度分析实时性要求高不缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisMemoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "analysis:snapshot:";
    private static final long TTL_SECONDS = 3600; // 1小时

    /**
     * 保存分析快照
     */
    public void save(AnalysisSnapshot snapshot) {
        if (snapshot == null || snapshot.getStockCode() == null) {
            log.warn("[分析记忆] 无效快照，跳过保存");
            return;
        }

        try {
            String key = buildKey(snapshot.getStockCode(), snapshot.getAnalysisType());
            String json = objectMapper.writeValueAsString(snapshot);

            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(TTL_SECONDS));

            log.info("[分析记忆] 保存成功 - 股票: {}, 类型: {}, TTL: {}秒",
                    snapshot.getStockCode(), snapshot.getAnalysisType(), TTL_SECONDS);
        } catch (Exception e) {
            log.error("[分析记忆] 保存失败", e);
        }
    }

    /**
     * 召回分析快照
     * @param stockCode 股票代码
     * @param analysisType 分析类型（FULL/FUNDAMENTAL/TECHNICAL/SENTIMENT）
     * @return 快照（如果存在且未过期）
     */
    public Optional<AnalysisSnapshot> recall(String stockCode, String analysisType) {
        if (stockCode == null || analysisType == null) {
            return Optional.empty();
        }

        try {
            String key = buildKey(stockCode, analysisType);
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                log.debug("[分析记忆] 未命中 - 股票: {}, 类型: {}", stockCode, analysisType);
                return Optional.empty();
            }

            AnalysisSnapshot snapshot = objectMapper.readValue(json, AnalysisSnapshot.class);

            // 二次校验过期（防止Redis TTL误差）
            if (snapshot.isExpired(TTL_SECONDS)) {
                log.debug("[分析记忆] 快照已过期 - 股票: {}, 年龄: {}秒",
                        stockCode, snapshot.getAgeInSeconds());
                invalidate(stockCode, analysisType);
                return Optional.empty();
            }

            log.info("[分析记忆] 命中 - 股票: {}, 类型: {}, 年龄: {}秒",
                    stockCode, analysisType, snapshot.getAgeInSeconds());
            return Optional.of(snapshot);

        } catch (Exception e) {
            log.error("[分析记忆] 召回失败", e);
            return Optional.empty();
        }
    }

    /**
     * 强制失效快照（用户主动刷新时调用）
     */
    public void invalidate(String stockCode, String analysisType) {
        if (stockCode == null || analysisType == null) {
            return;
        }

        try {
            String key = buildKey(stockCode, analysisType);
            redisTemplate.delete(key);
            log.info("[分析记忆] 失效快照 - 股票: {}, 类型: {}", stockCode, analysisType);
        } catch (Exception e) {
            log.error("[分析记忆] 失效失败", e);
        }
    }

    /**
     * 清空某股票的所有快照
     */
    public void invalidateAll(String stockCode) {
        if (stockCode == null) {
            return;
        }

        try {
            String[] types = {"FULL", "FUNDAMENTAL", "TECHNICAL", "SENTIMENT"};
            for (String type : types) {
                invalidate(stockCode, type);
            }
            log.info("[分析记忆] 清空股票所有快照 - 股票: {}", stockCode);
        } catch (Exception e) {
            log.error("[分析记忆] 清空失败", e);
        }
    }

    /**
     * 构建缓存Key
     */
    private String buildKey(String stockCode, String analysisType) {
        return CACHE_PREFIX + stockCode + ":" + analysisType;
    }
}
