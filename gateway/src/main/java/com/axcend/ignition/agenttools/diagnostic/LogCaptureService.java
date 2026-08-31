package com.axcend.ignition.agenttools.diagnostic;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for capturing and parsing gateway logs for diagnostic purposes.
 */
public class LogCaptureService {

    private static final Logger logger = LoggerFactory.getLogger(LogCaptureService.class);

    private static final Path WRAPPER_LOG_PATH = Paths.get(
            System.getProperty("ignition.install.dir", ""),
            "logs",
            "wrapper.log"
    );

    private static final Pattern LOG_ENTRY_PATTERN = Pattern.compile(
            "^(INFO|WARN|ERROR|DEBUG|TRACE)\\s*\\|\\s*jvm\\s*\\d+\\s*\\|\\s*(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s*\\|\\s*(.*)"
    );

    private static final int MAX_LINES = 1000;
    private static final int DEFAULT_ERROR_COUNT = 50;

    /**
     * Get recent errors from wrapper.log.
     */
    public List<LogEntry> getRecentErrors(int count) {
        return getRecentErrors(count, null, null);
    }

    /**
     * Get recent errors from wrapper.log with optional filtering.
     */
    public List<LogEntry> getRecentErrors(int count, String projectFilter, String patternFilter) {
        List<LogEntry> errors = new ArrayList<>();

        if (!Files.exists(WRAPPER_LOG_PATH)) {
            logger.warn("wrapper.log not found at: {}", WRAPPER_LOG_PATH);
            return errors;
        }

        try (BufferedReader reader = Files.newBufferedReader(WRAPPER_LOG_PATH)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && lines.size() < MAX_LINES) {
                lines.add(line);
            }

            // Read from the end (most recent first)
            for (int i = lines.size() - 1; i >= 0 && errors.size() < count; i--) {
                String logLine = lines.get(i);
                Matcher matcher = LOG_ENTRY_PATTERN.matcher(logLine);

                if (matcher.matches()) {
                    String level = matcher.group(1);
                    String timestamp = matcher.group(2);
                    String message = matcher.group(3);

                    // Only include ERROR and WARN entries
                    if ("ERROR".equals(level) || "WARN".equals(level)) {
                        // Apply filters
                        if (projectFilter != null && !projectFilter.isEmpty() &&
                                !message.contains(projectFilter)) {
                            continue;
                        }
                        if (patternFilter != null && !patternFilter.isEmpty() &&
                                !message.contains(patternFilter)) {
                            continue;
                        }

                        errors.add(new LogEntry(
                                level,
                                timestamp,
                                message,
                                logLine
                        ));
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read wrapper.log", e);
        }

        return errors;
    }

    /**
     * Get recent log entries (all levels).
     */
    public List<LogEntry> getRecentEntries(int count) {
        List<LogEntry> entries = new ArrayList<>();

        if (!Files.exists(WRAPPER_LOG_PATH)) {
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(WRAPPER_LOG_PATH)) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && lines.size() < MAX_LINES) {
                lines.add(line);
            }

            // Read from the end (most recent first)
            for (int i = lines.size() - 1; i >= 0 && entries.size() < count; i--) {
                String logLine = lines.get(i);
                Matcher matcher = LOG_ENTRY_PATTERN.matcher(logLine);

                if (matcher.matches()) {
                    String level = matcher.group(1);
                    String timestamp = matcher.group(2);
                    String message = matcher.group(3);

                    entries.add(new LogEntry(
                            level,
                            timestamp,
                            message,
                            logLine
                    ));
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read wrapper.log", e);
        }

        return entries;
    }

    /**
     * Log entry record.
     */
    public record LogEntry(
            String level,
            String timestamp,
            String message,
            String rawLine
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("level", level);
            map.put("timestamp", timestamp);
            map.put("message", message);
            return map;
        }
    }
}
