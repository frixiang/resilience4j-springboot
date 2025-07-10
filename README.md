
# Resilience4j Aspect Order demo's
# Defualt aspect order in Resilience4j retry (curcuitbraker (target)).

 In this example, the retry aspect is the first entry point. So each retry attempt would consider for the circuit breaker computation.  
 
 esilience4j.circuitbreaker:
	
    circuit-breaker-aspect-order: 2	
    instances:	
        getEmploye:		
            registerHealthIndicator: true
            slidingWindowSize: 10
            permittedNumberOfCallsInHalfOpenState: 5
            slidingWindowType: COUNT_BASED
            minimumNumberOfCalls: 4
            waitDurationInOpenState: 20s
            failureRateThreshold: 50
            eventConsumerBufferSize: 10
            record-exceptions:
            - org.springframework.web.client.ResourceAccessException
            - java.net.ConnectException
            - java.lang.RuntimeException
        
resilience4j:
  retry:
  
    retry-aspect-order: 4
    instances:
        getEmployetretry:
          max-retry-attempts: 3
          wait-duration:  1000

# Upon chaning the aspect  order curcuitbreaker (retry(target))).

 In this example, the circuit breaker aspect is the first entry point. So all retry attempts would consider as a single attempt for the circuit breaker computation. 
 
 esilience4j.circuitbreaker:
 
    circuit-breaker-aspect-order: 4
    instances:
        getEmploye:
            registerHealthIndicator: true
            slidingWindowSize: 10
            permittedNumberOfCallsInHalfOpenState: 5
            slidingWindowType: COUNT_BASED
            minimumNumberOfCalls: 4
            waitDurationInOpenState: 20s
            failureRateThreshold: 50
            eventConsumerBufferSize: 10
            record-exceptions:
            - org.springframework.web.client.ResourceAccessException
            - java.net.ConnectException
            - java.lang.RuntimeException
        
resilience4j:
  retry:
  
    retry-aspect-order: 2
    instances:
        getEmployetretry:
          max-retry-attempts: 3
          wait-duration:  1000


# minimumNumberOfCalls与maxAttempts的取值
## 策略一：minimumNumberOfCalls > maxAttempts (解耦策略，更常见且推荐)
**配置示例：**

- @Retry: maxAttempts = 3
- @CircuitBreaker: minimumNumberOfCalls = 10

**策略思想**：重试（Retry）处理“偶然抖动”，熔断（CircuitBreaker）处理“持续性故障”。两者职责分离。

**行为分析：**
- 当一个操作开始调用时，如果失败了，@Retry会尝试最多3次。
- 在这3次尝试中，@CircuitBreaker会记录下3次失败。
- 但是，因为 3 < 10，没有达到熔断器开始计算失败率的最小样本数（minimumNumberOfCalls）。
- 因此，这一个连续失败的操作（即使它重试了3次）永远不会靠它自己来打开熔断器。
- 只有当多个不同的操作接连失败，在滑动窗口内累计的失败次数超过了10次，熔断器才会开始计算失败率并决定是否跳闸。

**优点:**
- 容忍局部失败：一个单一的、特别“倒霉”的操作（可能因为它处理的数据很复杂而耗尽了所有重试机会）不会立即“污染”整个熔断器的状态，导致其他正常的请求也被熔断。
- 熔断决策更稳健：熔断器的决策是基于对下游服务在一段时间内整体健康状况的评估，而不是被单次操作的重试循环所“绑架”，这使得熔断的判断更准确，更能反映服务的普遍性问题。

**缺点:**
- 对突发故障反应稍慢：如果一个服务突然完全宕机，系统需要等待多个（本例中是4个，4 * 3 = 12 > 10）独立的操作都失败后，熔断器才能打开。

**适用场景:**
这是最常用、也通常是更推荐的通用配置。它提供了良好的隔离性，将小范围的、偶然的重试失败与大范围的、持续的服务故障清晰地分离开来。

## 策略二：minimumNumberOfCalls <= maxAttempts (联动策略，更激进)
**配置示例：**
- @Retry: maxAttempts = 5
- @CircuitBreaker: minimumNumberOfCalls = 3

**策略思想：** 如果一个操作的重试都救不活，就认为下游已经出现严重问题，立即“拉闸”保护。

**行为分析：**
- 当一个操作开始调用时，如果它连续失败：
  - 第1次失败：@CircuitBreaker记录1次失败。@Retry继续。
  - 第2次失败：@CircuitBreaker记录2次失败。@Retry继续。
  - 第3次失败：@CircuitBreaker记录3次失败。此时达到了minimumNumberOfCalls的门槛。如果失败率阈值设置得比较高（比如100%），熔断器会立即打开。
- 后续的第4、第5次重试会被打开的熔断器直接拒绝，并执行降级逻辑。

**优点:**
- 对持续故障反应极快：如果一个服务真的宕机了，那么第一个调用它的操作在经历了minimumNumberOfCalls次重试失败后，就会立刻触发整个系统的熔断，为该服务提供即时保护。

**缺点:**
- 过于敏感，可能误判：一个偶然的、复杂的、耗时较长的操作，如果它恰好连续失败了minimumNumberOfCalls次，就可能会导致熔断器打开，从而影响到其他本可以成功的、更简单的请求。这可能会“误伤友军”。

**适用场景:**
- 当您对下游服务的稳定性非常有信心，认为任何需要多次重试才能成功的调用都属于异常情况时。
- 对于那些“一次失败，很可能次次失败”的非幂等性关键操作，可以采用这种激进的保护策略。

**总结与建议**

| 策略 | 关系 | 核心思想 | 优点 | 缺点 |
|----|----|------|----|----|
| 策略一 (解耦)| minCalls > maxAttempts|重试和熔断各司其职|稳健，容忍局部失败|反应稍慢|
| 策略二 (联动)| minCalls <= maxAttempts|重试失败可直接触发熔断|反应快，保护性强|过于敏感，可能误判|
