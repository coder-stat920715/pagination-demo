package com.example.paginationdemo.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * Wraps every ProductService method call with a StopWatch and logs:
 *   [PAGINATION-BENCHMARK] <method> | strategy=<OFFSET|SLICE|KEYSET_CURSOR|CUSTOM_COUNT|SPECIFICATION> | 12 ms
 *
 * The strategy label is derived from the method name so the same aspect
 * covers all five pagination styles without needing five separate advices.
 * The measured time is also pushed into ExecutionTimeHolder so the
 * controller can surface it as the X-Response-Time-Ms response header --
 * handy for a live demo where you want the audience to *see* the numbers,
 * not just read them in a log tail.
 */
@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {

    @Around("execution(* com.example.paginationdemo.service..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = joinPoint.proceed();

        stopWatch.stop();
        long elapsedMs = stopWatch.getTotalTimeMillis();

        String methodName = joinPoint.getSignature().getName();
        String strategy = resolveStrategy(methodName);

        ExecutionTimeHolder.set(elapsedMs);
        log.info("[PAGINATION-BENCHMARK] {} | strategy={} | {} ms", methodName, strategy, elapsedMs);

        return result;
    }

    private String resolveStrategy(String methodName) {
        if (methodName.contains("Window")) {
            return "KEYSET_CURSOR";
        } else if (methodName.contains("Slice")) {
            return "SLICE_NO_COUNT";
        } else if (methodName.contains("CustomQuery")) {
            return "CUSTOM_COUNT_QUERY";
        } else if (methodName.contains("Filtered")) {
            return "SPECIFICATION";
        } else if (methodName.contains("Page")) {
            return "OFFSET_WITH_COUNT";
        }
        return "UNKNOWN";
    }
}
