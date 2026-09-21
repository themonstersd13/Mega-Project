package com.synapse.core.service;

import java.util.UUID;

public class TraceContext {
    private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

    public static class Context {
        private UUID runId;
        private UUID traceId;

        public Context(UUID runId, UUID traceId) {
            this.runId = runId;
            this.traceId = traceId;
        }

        public UUID getRunId() {
            return runId;
        }

        public UUID getTraceId() {
            return traceId;
        }
    }

    public static void setContext(UUID runId, UUID traceId) {
        contextHolder.set(new Context(runId, traceId));
    }

    public static Context getContext() {
        Context ctx = contextHolder.get();
        if (ctx == null) {
            ctx = new Context(UUID.randomUUID(), UUID.randomUUID());
            contextHolder.set(ctx);
        }
        return ctx;
    }

    public static void clear() {
        contextHolder.remove();
    }
}
