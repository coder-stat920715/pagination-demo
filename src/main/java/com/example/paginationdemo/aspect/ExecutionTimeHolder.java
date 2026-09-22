package com.example.paginationdemo.aspect;

/**
 * Simple request-scoped (thread-local) bridge: the AOP aspect measures how
 * long the service-layer call took, stashes it here, and the controller
 * reads it back to stamp the X-Response-Time-Ms header before returning.
 * Must be cleared at the end of each request to avoid leaking values across
 * threads in a pooled Tomcat executor.
 */
public final class ExecutionTimeHolder {

    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private ExecutionTimeHolder() {
    }

    public static void set(long millis) {
        HOLDER.set(millis);
    }

    public static Long get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
