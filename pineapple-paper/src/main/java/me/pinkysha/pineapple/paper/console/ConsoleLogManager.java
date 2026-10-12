package me.pinkysha.pineapple.paper.console;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ConsoleLogManager {

    private static final int MAX_LOG_LINES = 200;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ConcurrentLinkedDeque<String> logBuffer = new ConcurrentLinkedDeque<>();
    private final Handler logHandler;

    public ConsoleLogManager() {
        this.logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getMessage() == null) return;
                String time = LocalTime.now().format(TIME_FORMATTER);
                String level = record.getLevel() != null ? record.getLevel().getName() : "INFO";
                String msg = record.getMessage();
                String line = "[" + time + " " + level + "] " + msg;
                logBuffer.addLast(line);
                while (logBuffer.size() > MAX_LOG_LINES) {
                    logBuffer.pollFirst();
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };

        try {
            Logger.getLogger("").addHandler(logHandler);
        } catch (Exception ignored) {}
    }

    public List<String> getRecentLogs() {
        return new ArrayList<>(logBuffer);
    }

    public void appendManual(String line) {
        if (line == null) return;
        String time = LocalTime.now().format(TIME_FORMATTER);
        logBuffer.addLast("[" + time + " CONSOLE] " + line);
        while (logBuffer.size() > MAX_LOG_LINES) {
            logBuffer.pollFirst();
        }
    }

    public void shutdown() {
        try {
            Logger.getLogger("").removeHandler(logHandler);
        } catch (Exception ignored) {}
    }
}
