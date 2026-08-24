package com.axcend.ignition.agenttools;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import org.python.core.PyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes Jython code through the gateway ScriptManager (headless "script console").
 * Captures stdout/stderr, honors a per-call timeout, and returns the value bound to
 * the optional {@code result} variable as its string representation.
 */
public class GatewayScriptService {

    static final int DEFAULT_TIMEOUT_SECONDS = 15;
    static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_CODE_LENGTH = 100_000;

    private final GatewayContext gatewayContext;
    private final Logger logger = LoggerFactory.getLogger(GatewayScriptService.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-tools-script-exec");
        thread.setDaemon(true);
        return thread;
    });

    public GatewayScriptService(GatewayContext gatewayContext) {
        this.gatewayContext = gatewayContext;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * @param request parsed JSON body: {@code {code: String, timeoutSec?: int}}
     * @return response payload with success/result/stdout/stderr/durationMs (and error on failure)
     */
    public Map<String, Object> exec(JsonObject request) throws IllegalArgumentException {
        if (!request.has("code") || !request.get("code").isJsonPrimitive()
                || request.get("code").getAsString().isBlank()) {
            throw new IllegalArgumentException("'code' is required and must be a non-empty string.");
        }
        String code = request.get("code").getAsString();
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("'code' exceeds max length of " + MAX_CODE_LENGTH + " chars.");
        }
        int timeoutSec = DEFAULT_TIMEOUT_SECONDS;
        if (request.has("timeoutSec") && request.get("timeoutSec").isJsonPrimitive()) {
            timeoutSec = Math.min(Math.max(request.get("timeoutSec").getAsInt(), 1), MAX_TIMEOUT_SECONDS);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        ScriptManager scriptManager = gatewayContext.getScriptManager();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        long startNanos = System.nanoTime();
        try {
            PyObject locals = scriptManager.createLocalsMap();
            scriptManager.addStdOutStream(stdout);
            scriptManager.addStdErrStream(stderr);
            Future<?> future = null;
            try {
                future = executor.submit((Callable<Object>) () -> {
                    scriptManager.runCode(code, locals, "agent-tools-script-exec");
                    return null;
                });
                future.get(timeoutSec, TimeUnit.SECONDS);

                payload.put("success", true);
                PyObject result = locals.__finditem__("result");
                if (result != null && !"None".equals(result.toString())) {
                    payload.put("result", result.toString());
                    payload.put("resultType", result.getType().fastGetName());
                } else {
                    payload.put("result", null);
                }
            } catch (TimeoutException exception) {
                if (future != null) {
                    future.cancel(true);
                }
                payload.put("success", false);
                payload.put("error", "Script timed out after " + timeoutSec + "s and was cancelled.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                payload.put("success", false);
                payload.put("error", "Script execution was interrupted.");
            } catch (java.util.concurrent.ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                Throwable root = cause;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                payload.put("success", false);
                payload.put("error", root.toString());
            } finally {
                scriptManager.removeStdOutStream(stdout);
                scriptManager.removeStdErrStream(stderr);
            }
        } catch (RuntimeException exception) {
            logger.warn("Script exec setup failed", exception);
            payload.put("success", false);
            payload.put("error", String.valueOf(exception.getMessage()));
        }

        payload.put("stdout", stdout.toString());
        payload.put("stderr", stderr.toString());
        payload.put("durationMs", (System.nanoTime() - startNanos) / 1_000_000);
        return payload;
    }
}
