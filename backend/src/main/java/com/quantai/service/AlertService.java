package com.quantai.service;

import com.quantai.model.vo.AlertVO;

import java.util.List;

public interface AlertService {

    /**
     * 检查所有自选股的异动情况
     */
    List<AlertVO> checkAlerts();

    /**
     * 获取最近的预警记录
     */
    List<AlertVO> getRecentAlerts(int limit);

    /**
     * 标记预警为已读
     */
    void markAsRead(Long alertId);

    /**
     * 获取未读预警数量
     */
    long getUnreadCount();
}
