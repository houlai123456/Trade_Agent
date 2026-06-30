package com.quantai.feishu;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feishu")
@RequiredArgsConstructor
public class FeishuTestController {

    private final FeishuMessageService messageService;

    /** 测试飞书推送 */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Boolean>> testPush(@RequestBody(required = false) Map<String, String> body) {
        String text = body != null && body.containsKey("text")
                ? body.get("text")
                : "【交易助手测试】Java后端飞书推送验证成功";
        boolean ok = messageService.sendToMe(text);
        return ResponseEntity.ok(Map.of("success", ok));
    }
}
