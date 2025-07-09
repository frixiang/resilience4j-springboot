package com.snkit.springbootresilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResilianceDemoController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResilianceDemoController.class);
    
    @Autowired
    ResilianceDemoService resilianceDemoService;
    
    @GetMapping(value = "/getCust")
    public String getcustByNames() {
        logger.info("🚀 Controller 收到请求: /getCust");
        
        try {
            String result = resilianceDemoService.getCust();
            logger.info("✅ Controller 返回成功响应: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("❌ Controller 捕获异常: {}", e.getMessage());
            return "Controller Error: " + e.getMessage();
        }
    }
    
    @GetMapping(value = "/reset")
    public String resetCounters() {
        resilianceDemoService.resetCounters();
        return "计数器已重置";
    }
    
    @GetMapping(value = "/status")
    public String getStatus() {
        return resilianceDemoService.getCounterStatus();
    }
}
