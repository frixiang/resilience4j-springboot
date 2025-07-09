package com.snkit.springbootresilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.Metrics;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;

@Service
public class ResilianceDemoService {

    @Autowired
    RestTemplate restTemplate;
    
    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Logger logger = LoggerFactory.getLogger(ResilianceDemoService.class);
    
    // 添加调用计数器
    private static final AtomicInteger callCounter = new AtomicInteger(0);
    private static final AtomicInteger retryCounter = new AtomicInteger(0);
    
    // 使用完整类名避免与注解冲突
    private io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    @PostConstruct
    public void init() {
        // 获取断路器实例并添加事件监听器
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("getEmploye");
        
        // 添加断路器事件监听器
        circuitBreaker.getEventPublisher()
            .onSuccess(event -> {
                Metrics metrics = circuitBreaker.getMetrics();
                logger.info("🟢 断路器成功记录 - 调用耗时: {}ms | 成功次数: {}, 失败次数: {}, 总次数: {}", 
                    event.getElapsedDuration().toMillis(),
                    metrics.getNumberOfSuccessfulCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfBufferedCalls());
            })
            .onError(event -> {
                Metrics metrics = circuitBreaker.getMetrics();
                logger.warn("🔴 断路器记录失败 - 异常: {}, 耗时: {}ms | 成功次数: {}, 失败次数: {}, 总次数: {}", 
                    event.getThrowable().getClass().getSimpleName(), 
                    event.getElapsedDuration().toMillis(),
                    metrics.getNumberOfSuccessfulCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfBufferedCalls());
            })
            .onStateTransition(event -> {
                Metrics metrics = circuitBreaker.getMetrics();
                logger.warn("⚡ 断路器状态转换: {} -> {} | 成功次数: {}, 失败次数: {}, 总次数: {}", 
                    event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState(),
                    metrics.getNumberOfSuccessfulCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfBufferedCalls());
            })
            .onSlowCallRateExceeded(event -> {
                Metrics metrics = circuitBreaker.getMetrics();
                logger.warn("🐌 断路器慢调用率超标: {}% | 慢调用次数: {}, 总次数: {}", 
                    event.getSlowCallRate(),
                    metrics.getNumberOfSlowCalls(),
                    metrics.getNumberOfBufferedCalls());
            })
            .onFailureRateExceeded(event -> {
                Metrics metrics = circuitBreaker.getMetrics();
                logger.warn("💥 断路器失败率超标: {}% | 失败次数: {}, 总次数: {}", 
                    event.getFailureRate(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfBufferedCalls());
            })
            .onCallNotPermitted(event -> {
                Metrics metrics = circuitBreaker.getMetrics();
                logger.warn("🚫 断路器拒绝调用 - 当前状态: {} | 成功次数: {}, 失败次数: {}, 总次数: {}", 
                    circuitBreaker.getState(),
                    metrics.getNumberOfSuccessfulCalls(),
                    metrics.getNumberOfFailedCalls(),
                    metrics.getNumberOfBufferedCalls());
            });
    }

    @CircuitBreaker(name = "getEmploye", fallbackMethod = "getCustFallBack")
    @Retry(name = "getEmployetretry", fallbackMethod = "getRetryCustFallBack")
    public String getCust() {
        int currentCall = callCounter.incrementAndGet();
        int currentRetry = retryCounter.incrementAndGet();
        
        // 记录断路器当前状态和详细指标
        Metrics metrics = circuitBreaker.getMetrics();
        logger.info("🔍 断路器当前状态: {}", circuitBreaker.getState());
        logger.info("📊 断路器调用统计: 成功次数={}, 失败次数={}, 总次数={}, 慢调用次数={}", 
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfBufferedCalls(),
            metrics.getNumberOfSlowCalls());
        logger.info("📈 断路器比率指标: 失败率={}%, 慢调用率={}%", 
            metrics.getFailureRate(),
            metrics.getSlowCallRate());
        
        logger.info("=== 开始第 {} 次总调用，第 {} 次重试调用 ===", currentCall, currentRetry);
        logger.info("进入 getCust 方法 - ResilianceDemoService");

        try {
            MultiValueMap<String, String> headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            headers.add("Accept", "application/json");
            
            HttpEntity requestEntity = new HttpEntity(headers);
            
            logger.info("准备调用外部服务: http://localhost:8070/getEmploye");
            ResponseEntity<String> response = restTemplate.exchange("http://localhost:8070/getEmploye",
                    HttpMethod.GET,
                    requestEntity,
                    String.class);

            logger.info("外部服务调用成功！响应: {}", response.getBody());
            logger.info("=== 第 {} 次调用成功退出 ===", currentCall);
            
            // 成功后打印更新的指标
            Metrics metricsAfterSuccess = circuitBreaker.getMetrics();
            logger.info("✅ 成功后断路器统计: 成功次数={}, 失败次数={}, 总次数={}", 
                metricsAfterSuccess.getNumberOfSuccessfulCalls(),
                metricsAfterSuccess.getNumberOfFailedCalls(),
                metricsAfterSuccess.getNumberOfBufferedCalls());
            
            // 重置重试计数器（成功时）
            retryCounter.set(0);
            
            return response.getBody();
            
        } catch (Exception e) {
            logger.error("第 {} 次调用失败，异常: {}", currentCall, e.getMessage());
            logger.error("🔍 失败后断路器状态: {}", circuitBreaker.getState());
            
            // 失败后打印更新的指标
            Metrics metricsAfterFailure = circuitBreaker.getMetrics();
            logger.error("❌ 失败后断路器统计: 成功次数={}, 失败次数={}, 总次数={}, 失败率={}%", 
                metricsAfterFailure.getNumberOfSuccessfulCalls(),
                metricsAfterFailure.getNumberOfFailedCalls(),
                metricsAfterFailure.getNumberOfBufferedCalls(),
                metricsAfterFailure.getFailureRate());
            
            throw e; // 重新抛出异常，让重试和断路器处理
        }
    }

    // 断路器的fallback方法 - 处理CallNotPermittedException
    public String getCustFallBack(CallNotPermittedException ex) {
        int currentCall = callCounter.get();
        Metrics metrics = circuitBreaker.getMetrics();
        
        logger.warn("*** 🚫 断路器已打开！第 {} 次调用被断路器拦截 ***", currentCall);
        logger.warn("断路器详细状态: 状态={}", circuitBreaker.getState());
        logger.warn("📊 断路器完整统计: 成功次数={}, 失败次数={}, 总次数={}, 慢调用次数={}", 
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfBufferedCalls(),
            metrics.getNumberOfSlowCalls());
        logger.warn("📈 断路器比率: 失败率={}%, 慢调用率={}%", 
            metrics.getFailureRate(),
            metrics.getSlowCallRate());
        logger.info("断路器 Fallback 响应: {}", ex.getMessage());
        
        return "Response from CircuitBreaker Fallback - 断路器已打开，服务暂时不可用";
    }
    
    // 重试的fallback方法 - 处理其他异常
    public String getRetryCustFallBack(java.lang.Throwable throwable) {
        int currentCall = callCounter.get();
        int currentRetry = retryCounter.get();
        Metrics metrics = circuitBreaker.getMetrics();
        
        logger.error("*** 重试 {} 次后仍然失败！总调用次数: {} ***", currentRetry, currentCall);
        logger.error("重试 Fallback 原因: {}", throwable.getMessage());
        logger.error("🔍 重试失败后断路器状态: {}", circuitBreaker.getState());
        logger.error("📊 重试失败后断路器统计: 成功次数={}, 失败次数={}, 总次数={}", 
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfBufferedCalls());
        logger.error("📈 重试失败后比率: 失败率={}%, 慢调用率={}%", 
            metrics.getFailureRate(),
            metrics.getSlowCallRate());
        
        // 重置重试计数器
        retryCounter.set(0);
        //不能抛出异常，否则在未打开熔断前，会将该异常抛到前面（若有全局异常处理器或有try-catch则可以）
        //当前若想业务继续往下走，要么try-catch，要么不要抛异常
        return "Response from Retry Fallback - 重试 " + currentRetry + " 次后失败";
    }
    
    // 添加一个重置计数器的方法（用于测试）
    public void resetCounters() {
        callCounter.set(0);
        retryCounter.set(0);
        logger.info("计数器已重置");
    }
    
    // 获取当前计数（用于监控）
    public String getCounterStatus() {
        Metrics metrics = circuitBreaker.getMetrics();
        return String.format(
            "调用统计: 本地总调用=%d, 本地重试=%d | " +
            "断路器: 状态=%s, 成功次数=%d, 失败次数=%d, 总次数=%d, 失败率=%.2f%%", 
            callCounter.get(), retryCounter.get(),
            circuitBreaker.getState(),
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfBufferedCalls(),
            metrics.getFailureRate());
    }
    
    // 获取断路器详细状态
    public String getCircuitBreakerDetails() {
        Metrics metrics = circuitBreaker.getMetrics();
        return String.format(
            "断路器状态详情:\n" +
            "- 当前状态: %s\n" +
            "- 成功调用次数: %d\n" +
            "- 失败调用次数: %d\n" +
            "- 总调用次数(缓冲区): %d\n" +
            "- 慢调用次数: %d\n" +
            "- 失败率: %.2f%%\n" +
            "- 慢调用率: %.2f%%",
            circuitBreaker.getState(),
            metrics.getNumberOfSuccessfulCalls(),
            metrics.getNumberOfFailedCalls(),
            metrics.getNumberOfBufferedCalls(),
            metrics.getNumberOfSlowCalls(),
            metrics.getFailureRate(),
            metrics.getSlowCallRate()
        );
    }
}
