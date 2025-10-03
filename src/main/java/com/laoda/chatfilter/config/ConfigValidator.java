package com.laoda.chatfilter.config;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 配置文件验证器
 * 负责验证配置文件的格式和内容是否正确
 */
public class ConfigValidator {
    
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    
    /**
     * 验证主配置文件
     */
    public ValidationResult validateMainConfig(FileConfiguration config) {
        errors.clear();
        warnings.clear();
        
        // 验证基本设置
        validateBasicSettings(config);
        
        // 验证检测设置
        validateDetectionSettings(config);
        
        // 验证处罚阶梯
        validatePunishmentStages(config);
        
        // 验证日志设置
        validateLogSettings(config);
        
        return new ValidationResult(new ArrayList<>(errors), new ArrayList<>(warnings));
    }
    
    /**
     * 验证敏感词配置
     */
    public ValidationResult validateWordsConfig(FileConfiguration config) {
        errors.clear();
        warnings.clear();
        
        List<String> words = config.getStringList("sensitive-words");
        if (words == null) {
            errors.add("敏感词列表不能为空");
        } else {
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (word == null || word.trim().isEmpty()) {
                    warnings.add("第 " + (i + 1) + " 个敏感词为空，将被忽略");
                } else if (word.length() > 100) {
                    warnings.add("敏感词过长 (超过100字符): " + word.substring(0, 20) + "...");
                }
            }
        }
        
        return new ValidationResult(new ArrayList<>(errors), new ArrayList<>(warnings));
    }
    
    /**
     * 验证黑名单配置
     */
    public ValidationResult validateBlacklistConfig(FileConfiguration config) {
        errors.clear();
        warnings.clear();
        
        List<String> players = config.getStringList("blacklist-players");
        if (players != null) {
            for (int i = 0; i < players.size(); i++) {
                String player = players.get(i);
                if (player == null || player.trim().isEmpty()) {
                    warnings.add("第 " + (i + 1) + " 个黑名单玩家名为空，将被忽略");
                } else if (!isValidPlayerName(player)) {
                    warnings.add("无效的玩家名格式: " + player);
                }
            }
        }
        
        return new ValidationResult(new ArrayList<>(errors), new ArrayList<>(warnings));
    }
    
    /**
     * 验证正则表达式列表
     */
    public ValidationResult validateRegexPatterns(List<String> patterns) {
        errors.clear();
        warnings.clear();
        
        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i);
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                errors.add("第 " + (i + 1) + " 个正则表达式无效: " + pattern + " - " + e.getMessage());
            }
        }
        
        return new ValidationResult(new ArrayList<>(errors), new ArrayList<>(warnings));
    }
    
    private void validateBasicSettings(FileConfiguration config) {
        // 验证启用状态
        if (!config.contains("enabled")) {
            warnings.add("缺少 'enabled' 配置项，将使用默认值 true");
        }
        
        // 验证版本
        if (!config.contains("config-version")) {
            warnings.add("缺少配置版本号，建议添加 'config-version' 项");
        }
    }
    
    private void validateDetectionSettings(FileConfiguration config) {
        String basePath = "detection-settings";
        
        if (!config.contains(basePath)) {
            warnings.add("缺少检测设置配置段");
            return;
        }
        
        // 验证正则表达式设置
        if (config.getBoolean(basePath + ".use-regex", false)) {
            warnings.add("启用了正则表达式模式，请确保敏感词格式正确");
        }
        
        // 验证大小写敏感设置
        if (!config.contains(basePath + ".case-sensitive")) {
            warnings.add("缺少大小写敏感设置，将使用默认值 false");
        }
    }
    
    private void validatePunishmentStages(FileConfiguration config) {
        String basePath = "punishment-stages";
        
        if (!config.contains(basePath)) {
            errors.add("缺少处罚阶梯配置");
            return;
        }
        
        Set<String> stages = config.getConfigurationSection(basePath).getKeys(false);
        if (stages.isEmpty()) {
            errors.add("处罚阶梯配置为空");
            return;
        }
        
        for (String stageKey : stages) {
            try {
                int stage = Integer.parseInt(stageKey);
                if (stage <= 0) {
                    errors.add("处罚阶梯编号必须为正整数: " + stageKey);
                }
                
                String stagePath = basePath + "." + stageKey;
                
                // 验证命令列表
                List<String> commands = config.getStringList(stagePath + ".commands");
                if (commands == null || commands.isEmpty()) {
                    warnings.add("处罚阶梯 " + stage + " 没有配置命令");
                } else {
                    for (String command : commands) {
                        if (command == null || command.trim().isEmpty()) {
                            warnings.add("处罚阶梯 " + stage + " 包含空命令");
                        }
                    }
                }
                
                // 验证警告消息
                String warningMessage = config.getString(stagePath + ".warning-message");
                if (warningMessage == null || warningMessage.trim().isEmpty()) {
                    warnings.add("处罚阶梯 " + stage + " 缺少警告消息");
                }
                
            } catch (NumberFormatException e) {
                errors.add("无效的处罚阶梯编号: " + stageKey);
            }
        }
    }
    
    private void validateLogSettings(FileConfiguration config) {
        String basePath = "log-settings";
        
        if (config.contains(basePath)) {
            String logLevel = config.getString(basePath + ".level", "INFO");
            if (!isValidLogLevel(logLevel)) {
                errors.add("无效的日志级别: " + logLevel + "，有效值: SEVERE, WARNING, INFO, FINE");
            }
            
            boolean logToFile = config.getBoolean(basePath + ".log-to-file", false);
            if (logToFile) {
                String logFile = config.getString(basePath + ".log-file");
                if (logFile == null || logFile.trim().isEmpty()) {
                    errors.add("启用文件日志但未指定日志文件路径");
                }
            }
        }
    }
    
    private boolean isValidPlayerName(String name) {
        // Minecraft 玩家名规则：3-16字符，只能包含字母、数字和下划线
        return name != null && name.matches("^[a-zA-Z0-9_]{3,16}$");
    }
    
    private boolean isValidLogLevel(String level) {
        return level != null && 
               (level.equalsIgnoreCase("SEVERE") || 
                level.equalsIgnoreCase("WARNING") || 
                level.equalsIgnoreCase("INFO") || 
                level.equalsIgnoreCase("FINE"));
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(List<String> errors, List<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }
}