package com.quantai.controller;

import com.quantai.service.DataServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lhb")
@RequiredArgsConstructor
public class LhbController {

    private final DataServiceClient dataServiceClient;

    /**
     * 获取龙虎榜详情
     * GET /api/lhb/detail?date=2025-01-20
     */
    @GetMapping("/detail")
    public ResponseEntity<List<Map<String, Object>>> getLhbDetail(
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(dataServiceClient.fetchLhbDetail(date));
    }
}
