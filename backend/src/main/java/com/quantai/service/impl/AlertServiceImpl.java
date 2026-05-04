package com.quantai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantai.mapper.AlertRecordMapper;
import com.quantai.mapper.UserStockMapper;
import com.quantai.model.entity.AlertRecord;
import com.quantai.model.entity.StockQuote;
import com.quantai.model.entity.UserStock;
import com.quantai.model.vo.AlertVO;
import com.quantai.service.AlertService;
import com.quantai.service.DataServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final DataServiceClient dataServiceClient;
    private final AlertRecordMapper alertRecordMapper;
    private final UserStockMapper userStockMapper;

    @Override
    public List<AlertVO> checkAlerts() {
        List<UserStock> watchlist = userStockMapper.selectByUserId(1L);
        if (watchlist.isEmpty()) return Collections.emptyList();

        List<String> codes = watchlist.stream().map(UserStock::getCode).collect(Collectors.toList());
        List<StockQuote> quotes = dataServiceClient.fetchQuotes(codes);

        List<AlertVO> alerts = new ArrayList<>();
        for (StockQuote quote : quotes) {
            AlertVO alert = checkSingleStock(quote);
            if (alert != null) {
                alerts.add(alert);
                saveAlertRecord(alert);
            }
        }
        return alerts;
    }

    private AlertVO checkSingleStock(StockQuote quote) {
        if (quote.getChangePercent() == null) return null;
        BigDecimal cp = quote.getChangePercent();

        if (cp.compareTo(BigDecimal.valueOf(5)) > 0) {
            return buildAlert(quote, "PRICE",
                    String.format("%s 涨幅%.2f%%，触发涨跌幅异动预警", quote.getName(), cp));
        } else if (cp.compareTo(BigDecimal.valueOf(-3)) < 0) {
            return buildAlert(quote, "PRICE",
                    String.format("%s 跌幅%.2f%%，触发涨跌幅异动预警", quote.getName(), cp));
        }
        return null;
    }

    @Override
    public List<AlertVO> getRecentAlerts(int limit) {
        return convertToVO(alertRecordMapper.selectLatestAlerts(limit));
    }

    @Override
    public void markAsRead(Long alertId) {
        AlertRecord r = alertRecordMapper.selectById(alertId);
        if (r != null) {
            r.setReadFlag(1);
            alertRecordMapper.updateById(r);
        }
    }

    @Override
    public long getUnreadCount() {
        return alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>().eq(AlertRecord::getReadFlag, 0));
    }

    private AlertVO buildAlert(StockQuote q, String type, String desc) {
        AlertVO vo = new AlertVO();
        vo.setCode(q.getCode());
        vo.setName(q.getName());
        vo.setAlertType(type);
        vo.setDescription(desc);
        vo.setCurrentPrice(q.getCurrentPrice());
        vo.setChangePercent(q.getChangePercent());
        vo.setVolume(q.getVolume());
        vo.setReadFlag(0);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    private void saveAlertRecord(AlertVO vo) {
        AlertRecord r = new AlertRecord();
        r.setCode(vo.getCode());
        r.setName(vo.getName());
        r.setAlertType(vo.getAlertType());
        r.setDescription(vo.getDescription());
        r.setCurrentPrice(vo.getCurrentPrice());
        r.setChangePercent(vo.getChangePercent());
        r.setVolume(vo.getVolume());
        r.setCreateTime(vo.getCreateTime());
        alertRecordMapper.insert(r);
    }

    private List<AlertVO> convertToVO(List<AlertRecord> records) {
        if (records == null || records.isEmpty()) return Collections.emptyList();
        return records.stream().map(r -> {
            AlertVO vo = new AlertVO();
            vo.setId(r.getId());
            vo.setCode(r.getCode());
            vo.setName(r.getName());
            vo.setAlertType(r.getAlertType());
            vo.setDescription(r.getDescription());
            vo.setCurrentPrice(r.getCurrentPrice());
            vo.setChangePercent(r.getChangePercent());
            vo.setVolume(r.getVolume());
            vo.setReadFlag(r.getReadFlag());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
