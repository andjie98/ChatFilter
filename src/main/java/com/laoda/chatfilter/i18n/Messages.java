package com.laoda.chatfilter.i18n;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.logging.Level;

/**
 * 中文消息管理器
 * 统一管理所有插件消息，支持占位符替换
 */
public class Messages {
    
    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    
    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }
    
    /**
     * 初始化消息系统
     */
    private void initialize() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        
        // 如果文件不存在，创建默认消息文件
        if (!messagesFile.exists()) {
            createDefaultMessages();
        }
        
        // 加载消息配置
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // 检查并添加缺失的消息
        addMissingMessages();
    }
    
    /**
     * 创建默认中文消息文件
     */
    private void createDefaultMessages() {
        try {
            messagesFile.getParentFile().mkdirs();
            messagesFile.createNewFile();
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(messagesFile);
            setDefaultMessages(config);
            config.save(messagesFile);
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "无法创建消息文件", e);
        }
    }
    
    /**
     * 设置默认中文消息
     */
    private void setDefaultMessages(FileConfiguration config) {
        // 系统消息
        config.set("system.plugin-enabled", "&a[ChatFilter] 插件已启用!");
        config.set("system.plugin-disabled", "&c[ChatFilter] 插件已禁用!");
        config.set("system.config-reloaded", "&a配置已重载!");
        config.set("system.config-reload-failed", "&c配置重载失败: {0}");
        config.set("system.loading-words", "&7正在加载 {0} 个敏感词...");
        config.set("system.loading-blacklist", "&7正在加载 {0} 个黑名单玩家...");
        config.set("system.loading-punishments", "&7正在加载 {0} 个处罚阶梯...");
        
        // 权限消息
        config.set("permission.no-permission", "&c你没有权限执行此命令!");
        config.set("permission.player-only", "&c此命令只能由玩家执行!");
        config.set("permission.bypass", "&7玩家 {0} 拥有绕过权限，跳过检测");
        
        // 命令消息
        config.set("command.unknown-command", "&c未知命令! 使用 &e/cf help &c查看帮助");
        config.set("command.invalid-usage", "&c用法错误! 正确用法: &e{0}");
        config.set("command.player-not-found", "&c玩家 &e{0} &c不存在或不在线!");
        config.set("command.execution-error", "&c命令执行出错: {0}");
        
        // 敏感词管理
        config.set("word.added", "&a已添加敏感词: &e{0}");
        config.set("word.already-exists", "&c敏感词 &e{0} &c已存在!");
        config.set("word.removed", "&a已删除敏感词: &e{0}");
        config.set("word.not-found", "&c敏感词 &e{0} &c不存在!");
        config.set("word.list-header", "&6===== 敏感词列表 ({0}个) =====");
        config.set("word.list-empty", "&e暂无敏感词");
        config.set("word.save-failed", "&c保存敏感词文件失败");
        
        // 黑名单管理
        config.set("blacklist.added", "&a已添加黑名单玩家: &e{0}");
        config.set("blacklist.already-exists", "&c玩家 &e{0} &c已在黑名单中!");
        config.set("blacklist.removed", "&a已从黑名单移除玩家: &e{0}");
        config.set("blacklist.not-found", "&c玩家 &e{0} &c不在黑名单中!");
        config.set("blacklist.list-header", "&6===== 黑名单玩家 ({0}人) =====");
        config.set("blacklist.list-empty", "&e暂无黑名单玩家");
        config.set("blacklist.save-failed", "&c保存黑名单文件失败");
        
        // 违规处理
        config.set("violation.detected", "&c检测到敏感词: &e{0}");
        config.set("violation.warning", "&c[警告] 你使用了敏感词 &e{1}&c! 当前违规次数: &e{0}");
        config.set("violation.punishment", "&c你因使用敏感词被处罚! 违规次数: &e{0}");
        config.set("violation.list-header", "&6===== 玩家违规次数 ({0}人) =====");
        config.set("violation.list-item", "&e{0}: &7{1} 次");
        config.set("violation.list-empty", "&e暂无违规记录");
        config.set("violation.reset-all", "&a已重置所有玩家的违规次数 (共 &e{0} &a人)");
        config.set("violation.reset-player", "&a已重置玩家 &e{0} &a的违规次数 (原: &e{1} &a次)");
        config.set("violation.no-violations", "&e玩家 &e{0} &e没有违规记录");
        config.set("violation.player-count", "&e玩家 &e{0} &e当前违规次数: &7{1}");
        
        // 测试功能
        config.set("test.no-message", "&c请输入要测试的消息!");
        config.set("test.no-sensitive-word", "&a测试消息: &7{0} &a- 未检测到敏感词");
        config.set("test.sensitive-word-found", "&c测试消息: &7{0} &c- 检测到敏感词: &e{1}");
        
        // 统计信息
        config.set("stats.header", "&6===== ChatFilter 统计信息 =====");
        config.set("stats.plugin-status", "&e插件状态: &7{0}");
        config.set("stats.sensitive-words", "&e敏感词数量: &7{0}");
        config.set("stats.blacklist-players", "&e黑名单玩家: &7{0}");
        config.set("stats.violation-players", "&e违规玩家数: &7{0}");
        config.set("stats.total-violations", "&e总违规次数: &7{0}");
        config.set("stats.detection-mode", "&e检测模式: &7{0}");
        config.set("stats.case-sensitive", "&e大小写敏感: &7{0}");
        config.set("stats.last-reset", "&e上次重置: &7{0}");
        config.set("stats.log-level", "&e日志级别: &7{0}");
        config.set("stats.file-logging", "&e文件日志: &7{0}");
        
        // 帮助信息
        config.set("help.header", "&6===== ChatFilter 命令帮助 =====");
        config.set("help.reload", "&e/cf reload &7- 重载配置文件");
        config.set("help.addword", "&e/cf addword <词语> &7- 添加敏感词");
        config.set("help.removeword", "&e/cf removeword <词语> &7- 删除敏感词");
        config.set("help.listwords", "&e/cf listwords &7- 列出所有敏感词");
        config.set("help.addblacklist", "&e/cf addblacklist <玩家> &7- 添加黑名单玩家");
        config.set("help.removeblacklist", "&e/cf removeblacklist <玩家> &7- 移除黑名单玩家");
        config.set("help.listblacklist", "&e/cf listblacklist &7- 列出黑名单玩家");
        config.set("help.test", "&e/cf test <消息> &7- 测试消息是否包含敏感词");
        config.set("help.violations", "&e/cf violations [玩家] &7- 查看违规次数");
        config.set("help.resetviolations", "&e/cf resetviolations [玩家] &7- 重置违规次数");
        config.set("help.stats", "&e/cf stats &7- 查看插件统计信息");
        
        // 状态文本
        config.set("status.enabled", "启用");
        config.set("status.disabled", "禁用");
        config.set("status.yes", "是");
        config.set("status.no", "否");
        config.set("status.regex", "正则表达式");
        config.set("status.string-match", "字符串匹配");
        
        // 日志消息
        config.set("log.violation-detected", "玩家 {0} 使用敏感词: {1} (违规次数: {2})");
        config.set("log.punishment-executed", "对玩家 {0} 执行处罚 (阶梯 {1}): {2}");
        config.set("log.config-validation-failed", "配置验证失败: {0}");
        config.set("log.admin-action", "管理员 {0} 执行操作: {1}");
        
        // 配置验证消息
        config.set("validation.invalid-punishment-stage", "无效的处罚阶梯: {0}");
        config.set("validation.invalid-regex", "无效的正则表达式: {0}");
        config.set("validation.missing-config-section", "缺少配置节: {0}");
        config.set("validation.invalid-log-level", "无效的日志级别: {0}");
    }
    
    /**
     * 检查并添加缺失的消息
     */
    private void addMissingMessages() {
        boolean modified = false;
        FileConfiguration defaultConfig = new YamlConfiguration();
        setDefaultMessages(defaultConfig);
        
        // 检查每个默认消息是否存在
        for (String key : defaultConfig.getKeys(true)) {
            if (!messagesConfig.contains(key)) {
                messagesConfig.set(key, defaultConfig.get(key));
                modified = true;
            }
        }
        
        // 如果有修改，保存文件
        if (modified) {
            try {
                messagesConfig.save(messagesFile);
                plugin.getLogger().info("已添加缺失的消息配置");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "保存消息文件失败", e);
            }
        }
    }
    
    /**
     * 获取消息
     * @param key 消息键
     * @param args 占位符参数
     * @return 格式化后的消息
     */
    public String getMessage(String key, Object... args) {
        String message = messagesConfig.getString(key, "&c消息未找到: " + key);
        
        // 替换占位符
        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }
        
        // 转换颜色代码
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * 获取原始消息（不处理颜色代码）
     */
    public String getRawMessage(String key, Object... args) {
        String message = messagesConfig.getString(key, "消息未找到: " + key);
        
        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }
        
        return message;
    }
    
    /**
     * 重载消息配置
     */
    public void reload() {
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        addMissingMessages();
    }
    
    /**
     * 获取状态文本
     */
    public String getStatusText(boolean status) {
        return getMessage(status ? "status.enabled" : "status.disabled");
    }
    
    /**
     * 获取是否文本
     */
    public String getYesNoText(boolean value) {
        return getMessage(value ? "status.yes" : "status.no");
    }
    
    /**
     * 获取检测模式文本
     */
    public String getDetectionModeText(boolean useRegex) {
        return getMessage(useRegex ? "status.regex" : "status.string-match");
    }
}