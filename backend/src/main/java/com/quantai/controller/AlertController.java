package com.quantai.controller;

import com.quantai.model.vo.AlertVO;
import com.quantai.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * 获取最近的预警记录
     * GET /api/alert/recent?limit=20
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AlertVO>> getRecentAlerts(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(alertService.getRecentAlerts(limit));
    }

    /**
     * 获取未读预警数量
     * GET /api/alert/unread/count
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", alertService.getUnreadCount()));
    }

    /**
     * 标记预警为已读
     * PUT /api/alert/{id}/read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Boolean>> markAsRead(@PathVariable Long id) {
        alertService.markAsRead(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 手动触发异动检查
     * POST /api/alert/check
     */
    @PostMapping("/check")
    public ResponseEntity<List<AlertVO>> checkAlerts() {
        return ResponseEntity.ok(alertService.checkAlerts());
    }
}
