package com.laoda.chatfilter.logging;

import org.bukkit.plugin.Plugin;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ChatFilter 专用日志管理器
 * 支持分级日志记录和异步文件写入
 */
public class ChatFilterLogger {
    
    private final Logger bukkitLogger;
    private final BlockingQueue<LogEntry> logQueue;
    private final AtomicBoolean isFileLoggingEnabled;
    private final AtomicBoolean isRunning;
    private final Thread logWriterThread;
    
    private File logFile;
    private LogLevel currentLevel;
    private final SimpleDateFormat dateFormat;
    
    public ChatFilterLogger(Plugin plugin) {
        this.bukkitLogger = plugin.getLogger();
        this.logQueue = new LinkedBlockingQueue<>();
        this.isFileLoggingEnabled = new AtomicBoolean(false);
        this.isRunning = new AtomicBoolean(true);
        this.currentLevel = LogLevel.INFO;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // 启动异步日志写入线程
        this.logWriterThread = new Thread(this::processLogQueue, "ChatFilter-Logger");
        this.logWriterThread.setDaemon(true);
        this.logWriterThread.start();
    }
    
    /**
     * 配置日志设置
     */
    public void configure(LogLevel level, boolean enableFileLogging, File logFile) {
        this.currentLevel = level;
        this.logFile = logFile;
        this.isFileLoggingEnabled.set(enableFileLogging);
        
        if (enableFileLogging && logFile != null) {
            try {
                // 确保日志目录存在
                File parentDir = logFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // 测试文件写入权限
                if (!logFile.exists()) {
                    logFile.createNewFile();
                }
                
                info("文件日志已启用: " + logFile.getAbsolutePath());
            } catch (IOException e) {
                warning("无法创建日志文件: " + e.getMessage());
                this.isFileLoggingEnabled.set(false);
            }
        }
    }
    
    /**
     * 记录严重错误
     */
    public void severe(String message) {
        log(LogLevel.SEVERE, message, null);
    }
    
    public void severe(String message, Throwable throwable) {
        log(LogLevel.SEVERE, message, throwable);
    }
    
    /**
     * 记录警告
     */
    public void warning(String message) {
        log(LogLevel.WARNING, message, null);
    }
    
    public void warning(String message, Throwable throwable) {
        log(LogLevel.WARNING, message, throwable);
    }
    
    /**
     * 记录信息
     */
    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }
    
    /**
     * 记录调试信息
     */
    public void fine(String message) {
        log(LogLevel.FINE, message, null);
    }
    
    /**
     * 记录玩家违规行为
     */
    public void logViolation(String playerName, String message, String detectedWord, int violationCount) {
        String logMessage = String.format("违规检测 - 玩家: %s, 消息: \"%s\", 敏感词: \"%s\", 违规次数: %d", 
                                         playerName, message, detectedWord, violationCount);
        log(LogLevel.WARNING, logMessage, null);
    }
    
    /**
     * 记录处罚执行
     */
    public void logPunishment(String playerName, int stage, String command) {
        String logMessage = String.format("执行处罚 - 玩家: %s, 阶梯: %d, 命令: %s", 
                                         playerName, stage, command);
        log(LogLevel.INFO, logMessage, null);
    }
    
    /**
     * 记录配置重载
     */
    public void logConfigReload(String configType, boolean success) {
        String logMessage = String.format("配置重载 - 类型: %s, 结果: %s", 
                                         configType, success ? "成功" : "失败");
        log(LogLevel.INFO, logMessage, null);
    }
    
    /**
     * 核心日志记录方法
     */
    private void log(LogLevel level, String message, Throwable throwable) {
        // 检查日志级别
        if (level.ordinal() > currentLevel.ordinal()) {
            return;
        }
        
        // 记录到 Bukkit 日志
        Level bukkitLevel = toBukkitLevel(level);
        if (throwable != null) {
            bukkitLogger.log(bukkitLevel, message, throwable);
        } else {
            bukkitLogger.log(bukkitLevel, message);
        }
        
        // 如果启用文件日志，添加到队列
        if (isFileLoggingEnabled.get() && logFile != null) {
            LogEntry entry = new LogEntry(level, message, throwable, System.currentTimeMillis());
            logQueue.offer(entry);
        }
    }
    
    /**
     * 异步处理日志队列
     */
    private void processLogQueue() {
        while (isRunning.get()) {
            try {
                LogEntry entry = logQueue.take();
                writeToFile(entry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 避免日志记录本身出错导致的循环
                System.err.println("ChatFilter 日志写入失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 写入日志到文件
     */
    private void writeToFile(LogEntry entry) {
        if (logFile == null) return;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            String timestamp = dateFormat.format(new Date(entry.timestamp));
            String logLine = String.format("[%s] [%s] %s", 
                                          timestamp, entry.level.name(), entry.message);
            writer.println(logLine);
            
            // 如果有异常，也写入异常信息
            if (entry.throwable != null) {
                entry.throwable.printStackTrace(writer);
            }
            
        } catch (IOException e) {
            // 文件写入失败，禁用文件日志
            isFileLoggingEnabled.set(false);
            bukkitLogger.warning("日志文件写入失败，已禁用文件日志: " + e.getMessage());
        }
    }
    
    /**
     * 转换为 Bukkit 日志级别
     */
    private Level toBukkitLevel(LogLevel level) {
        switch (level) {
            case SEVERE: return Level.SEVERE;
            case WARNING: return Level.WARNING;
            case INFO: return Level.INFO;
            case FINE: return Level.FINE;
            default: return Level.INFO;
        }
    }
    
    /**
     * 关闭日志记录器
     */
    public void shutdown() {
        isRunning.set(false);
        logWriterThread.interrupt();
        
        // 处理剩余的日志条目
        while (!logQueue.isEmpty()) {
            LogEntry entry = logQueue.poll();
            if (entry != null) {
                writeToFile(entry);
            }
        }
    }
    
    /**
     * 获取当前日志级别
     */
    public LogLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * 检查是否启用文件日志
     */
    public boolean isFileLoggingEnabled() {
        return isFileLoggingEnabled.get();
    }
    
    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        SEVERE(0),
        WARNING(1),
        INFO(2),
        FINE(3);
        
        private final int value;
        
        LogLevel(int value) {
            this.value = value;
        }
        
        public static LogLevel fromString(String level) {
            if (level == null) return INFO;
            
            try {
                return LogLevel.valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                return INFO;
            }
        }
    }
    
    /**
     * 日志条目类
     */
    private static class LogEntry {
        final LogLevel level;
        final String message;
        final Throwable throwable;
        final long timestamp;
        
        LogEntry(LogLevel level, String message, Throwable throwable, long timestamp) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
            this.timestamp = timestamp;
        }
    }
}