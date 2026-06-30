package com.quantai.feishu;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuNotificationService {

    private final FeishuMessageService messageService;

    public void notifyWatchRule(String code, String name, String type, String targetPrice, String currentPrice) {
        String direction = "ABOVE".equals(type) ? "涨破" : "跌破";
        String text = String.format("【盯盘提醒】\n%s(%s) %s %s\n当前价：%s",
                name, code, direction, targetPrice, currentPrice);
        messageService.sendToMe(text);
    }

    public void notifyConditionOrder(String code, String name, String direction, String triggerType, String price) {
        String dirLabel = "BUY".equals(direction) ? "买入" : "卖出";
        String typeLabel = switch (triggerType) {
            case "ABOVE" -> "涨破目标价";
            case "BELOW" -> "跌破目标价";
            case "GOLDEN_CROSS" -> "金叉";
            case "DEATH_CROSS" -> "死叉";
            case "VOLUME_BREAKOUT" -> "放量突破";
            default -> triggerType;
        };
        String text = String.format("【条件单触发】\n%s(%s) %s\n已自动%s %s",
                name, code, typeLabel, dirLabel, price);
        messageService.sendToMe(text);
    }

    public void notifyAbnormalAlert(String code, String name, String alertType, String detail) {
        String text = String.format("【异动预警】\n%s(%s)\n%s", name, code, detail);
        messageService.sendToMe(text);
    }
}
