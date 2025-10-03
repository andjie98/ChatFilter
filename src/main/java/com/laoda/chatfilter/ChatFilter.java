package com.laoda.chatfilter;

import com.laoda.chatfilter.algorithm.AhoCorasick;
import com.laoda.chatfilter.config.ConfigValidator;
import com.laoda.chatfilter.logging.ChatFilterLogger;
import com.laoda.chatfilter.util.ViolationCounter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatFilter extends JavaPlugin implements Listener {

    // 核心组件
    private AhoCorasick wordMatcher;
    private ViolationCounter violationCounter;
    private ChatFilterLogger logger;
    private ConfigValidator configValidator;

    // 配置数据
    private Set<String> sensitiveWords;
    private Set<String> blacklistPlayers;
    private volatile boolean enabled;
    private volatile boolean useRegex;
    private volatile boolean caseSensitive;

    // 阶梯处罚相关变量
    private final Map<Integer, List<String>> punishmentCommands = new ConcurrentHashMap<>();
    private final Map<Integer, String> warningMessages = new ConcurrentHashMap<>();

    // 配置文件
    private File wordsFile;
    private File blacklistFile;
    private FileConfiguration wordsConfig;
    private FileConfiguration blacklistConfig;

    @Override
    public void onEnable() {
        try {
            // 初始化核心组件
            initializeComponents();
            
            // 保存默认配置
            saveDefaultConfig();

            // 初始化配置文件
            initializeConfigFiles();

            // 加载并验证配置
            loadAndValidateConfiguration();

            // 注册事件监听器
            getServer().getPluginManager().registerEvents(this, this);

            // 设置每日重置任务
            setupDailyResetTask();

            logger.info("ChatFilter 插件已启用!");
            logger.info("已加载 " + sensitiveWords.size() + " 个敏感词");
            logger.info("已加载 " + blacklistPlayers.size() + " 个黑名单玩家");
            logger.info("已加载 " + punishmentCommands.size() + " 个处罚阶梯");
            
        } catch (Exception e) {
            getLogger().severe("插件启用失败: " + e.getMessage());
            e.printStackTrace();
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (logger != null) {
            logger.info("ChatFilter 插件正在关闭...");
            logger.shutdown();
        }
        getLogger().info("ChatFilter 插件已禁用!");
    }

    private void initializeComponents() {
        this.logger = new ChatFilterLogger(this);
        this.configValidator = new ConfigValidator();
        this.violationCounter = new ViolationCounter();
        this.sensitiveWords = ConcurrentHashMap.newKeySet();
        this.blacklistPlayers = ConcurrentHashMap.newKeySet();
        this.wordMatcher = new AhoCorasick(false);
    }

    private void initializeConfigFiles() throws IOException {
        wordsFile = new File(getDataFolder(), "words.yml");
        blacklistFile = new File(getDataFolder(), "blacklist.yml");

        if (!wordsFile.exists()) {
            saveResource("words.yml", false);
        }
        if (!blacklistFile.exists()) {
            saveResource("blacklist.yml", false);
        }

        wordsConfig = YamlConfiguration.loadConfiguration(wordsFile);
        blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
    }

    private void loadAndValidateConfiguration() {
        // 验证主配置
        ConfigValidator.ValidationResult mainResult = configValidator.validateMainConfig(getConfig());
        if (!mainResult.isValid()) {
            for (String error : mainResult.getErrors()) {
                logger.severe("配置错误: " + error);
            }
            throw new RuntimeException("主配置文件验证失败");
        }
        
        if (mainResult.hasWarnings()) {
            for (String warning : mainResult.getWarnings()) {
                logger.warning("配置警告: " + warning);
            }
        }

        // 验证敏感词配置
        ConfigValidator.ValidationResult wordsResult = configValidator.validateWordsConfig(wordsConfig);
        if (!wordsResult.isValid()) {
            for (String error : wordsResult.getErrors()) {
                logger.severe("敏感词配置错误: " + error);
            }
            throw new RuntimeException("敏感词配置文件验证失败");
        }

        // 验证黑名单配置
        ConfigValidator.ValidationResult blacklistResult = configValidator.validateBlacklistConfig(blacklistConfig);
        if (!blacklistResult.isValid()) {
            for (String error : blacklistResult.getErrors()) {
                logger.severe("黑名单配置错误: " + error);
            }
            throw new RuntimeException("黑名单配置文件验证失败");
        }

        // 加载配置数据
        loadConfigurationData();
        
        // 配置日志系统
        configureLogging();
    }

    private void loadConfigurationData() {
        FileConfiguration config = getConfig();

        // 基本设置
        this.enabled = config.getBoolean("enabled", true);
        this.useRegex = config.getBoolean("detection-settings.use-regex", false);
        this.caseSensitive = config.getBoolean("detection-settings.case-sensitive", false);

        // 加载敏感词
        sensitiveWords.clear();
        List<String> words = wordsConfig.getStringList("sensitive-words");
        if (words != null) {
            for (String word : words) {
                if (word != null && !word.trim().isEmpty()) {
                    sensitiveWords.add(word.trim());
                }
            }
        }

        // 验证正则表达式（如果启用）
        if (useRegex) {
            ConfigValidator.ValidationResult regexResult = configValidator.validateRegexPatterns(new ArrayList<>(sensitiveWords));
            if (!regexResult.isValid()) {
                logger.warning("检测到无效的正则表达式，将禁用正则模式");
                this.useRegex = false;
            }
        }

        // 重建字符串匹配器
        this.wordMatcher = new AhoCorasick(caseSensitive);
        this.wordMatcher.build(sensitiveWords);

        // 加载黑名单
        blacklistPlayers.clear();
        List<String> blacklist = blacklistConfig.getStringList("blacklist-players");
        if (blacklist != null) {
            for (String player : blacklist) {
                if (player != null && !player.trim().isEmpty()) {
                    blacklistPlayers.add(player.trim());
                }
            }
        }

        // 加载处罚阶梯
        loadPunishmentStages(config);
    }

    private void configureLogging() {
        FileConfiguration config = getConfig();
        
        String logLevelStr = config.getString("log-settings.level", "INFO");
        boolean logToFile = config.getBoolean("log-settings.log-to-file", false);
        String logFileName = config.getString("log-settings.log-file", "chatfilter.log");
        
        ChatFilterLogger.LogLevel logLevel = ChatFilterLogger.LogLevel.fromString(logLevelStr);
        File logFile = logToFile ? new File(getDataFolder(), logFileName) : null;
        
        logger.configure(logLevel, logToFile, logFile);
        logger.logConfigReload("主配置", true);
    }

    private void loadPunishmentStages(FileConfiguration config) {
        punishmentCommands.clear();
        warningMessages.clear();

        if (config.getConfigurationSection("punishment-stages") != null) {
            for (String stageKey : config.getConfigurationSection("punishment-stages").getKeys(false)) {
                try {
                    int stage = Integer.parseInt(stageKey);
                    List<String> commands = config.getStringList("punishment-stages." + stageKey + ".commands");
                    String warningMessage = config.getString("punishment-stages." + stageKey + ".warning-message", "&c请文明聊天!");

                    punishmentCommands.put(stage, new ArrayList<>(commands));
                    warningMessages.put(stage, warningMessage);

                    logger.fine("加载处罚阶梯 " + stage + ": " + commands.size() + " 个命令");
                } catch (NumberFormatException e) {
                    logger.warning("无效的处罚阶梯: " + stageKey);
                }
            }
        }
    }

    private void setupDailyResetTask() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                int resetCount = violationCounter.checkAndResetDaily();
                if (resetCount >= 0) {
                    logger.info("已重置 " + resetCount + " 个玩家的敏感词处罚次数");
                }
            }
        }, 0L, 20L * 60 * 10);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        if (blacklistPlayers.contains(player.getName())) {
            return;
        }

        String detectedWord = containsSensitiveWord(message);
        if (detectedWord != null) {
            event.setCancelled(true);

            int currentCount = violationCounter.incrementViolation(player.getName());
            logger.logViolation(player.getName(), message, detectedWord, currentCount);
            executePunishment(player, message, detectedWord, currentCount);
        }
    }

    private String containsSensitiveWord(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        if (useRegex) {
            return containsSensitiveWordRegex(message);
        } else {
            return wordMatcher.findFirst(message);
        }
    }

    private String containsSensitiveWordRegex(String message) {
        String checkMessage = caseSensitive ? message : message.toLowerCase();

        for (String word : sensitiveWords) {
            String checkWord = caseSensitive ? word : word.toLowerCase();
            try {
                Pattern pattern = Pattern.compile(checkWord);
                if (pattern.matcher(checkMessage).find()) {
                    return word;
                }
            } catch (Exception e) {
                logger.warning("无效的正则表达式: " + word);
            }
        }
        return null;
    }

    private void executePunishment(Player player, String originalMessage, String detectedWord, int violationCount) {
        int effectiveStage = violationCount;
        while (effectiveStage > 0 && !punishmentCommands.containsKey(effectiveStage)) {
            effectiveStage--;
        }

        if (effectiveStage > 0) {
            String warningMsg = warningMessages.get(effectiveStage);
            if (warningMsg != null) {
                String finalWarning = ChatColor.translateAlternateColorCodes('&',
                        warningMsg.replace("%player%", player.getName())
                                .replace("%count%", String.valueOf(violationCount))
                                .replace("%word%", detectedWord));
                player.sendMessage(finalWarning);
            }

            List<String> commands = punishmentCommands.get(effectiveStage);
            if (commands != null) {
                executePunishmentCommands(player, originalMessage, detectedWord, commands, violationCount);
            }
        }
    }

    private void executePunishmentCommands(final Player player, final String originalMessage,
                                           final String detectedWord, final List<String> commands,
                                           final int violationCount) {
        for (final String command : commands) {
            if (command != null && !command.trim().isEmpty()) {
                final String processedCommand = command
                        .replace("%player%", player.getName())
                        .replace("%message%", originalMessage)
                        .replace("%word%", detectedWord)
                        .replace("%count%", String.valueOf(violationCount));

                final String finalCommand = processedCommand.startsWith("/") ?
                        processedCommand.substring(1) : processedCommand;

                logger.logPunishment(player.getName(), violationCount, finalCommand);

                Bukkit.getScheduler().runTask(this, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                        } catch (Exception e) {
                            logger.warning("执行处罚命令失败: " + finalCommand + " - " + e.getMessage());
                        }
                    }
                });
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("chatfilter")) {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            try {
                switch (args[0].toLowerCase()) {
                    case "reload":
                        return reloadCommand(sender);
                    case "addword":
                        return addWordCommand(sender, args);
                    case "removeword":
                        return removeWordCommand(sender, args);
                    case "listwords":
                        return listWordsCommand(sender);
                    case "addblacklist":
                        return addBlacklistCommand(sender, args);
                    case "removeblacklist":
                        return removeBlacklistCommand(sender, args);
                    case "listblacklist":
                        return listBlacklistCommand(sender);
                    case "test":
                        return testCommand(sender, args);
                    case "violations":
                        return viewViolationsCommand(sender, args);
                    case "resetviolations":
                        return resetViolationsCommand(sender, args);
                    case "stats":
                        return statsCommand(sender);
                    default:
                        sendHelp(sender);
                        return true;
                }
            } catch (Exception e) {
                sender.sendMessage("§c命令执行出错: " + e.getMessage());
                logger.warning("命令执行异常: " + e.getMessage(), e);
                return true;
            }
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== ChatFilter 帮助 =====");
        sender.sendMessage("§e/cf reload §7- 重载插件配置");
        sender.sendMessage("§e/cf addword <词语> §7- 添加敏感词");
        sender.sendMessage("§e/cf removeword <词语> §7- 删除敏感词");
        sender.sendMessage("§e/cf listwords §7- 列出所有敏感词");
        sender.sendMessage("§e/cf addblacklist <玩家> §7- 添加黑名单玩家");
        sender.sendMessage("§e/cf removeblacklist <玩家> §7- 删除黑名单玩家");
        sender.sendMessage("§e/cf listblacklist §7- 列出黑名单玩家");
        sender.sendMessage("§e/cf test <消息> §7- 测试消息是否包含敏感词");
        sender.sendMessage("§e/cf violations [玩家] §7- 查看违规次数");
        sender.sendMessage("§e/cf resetviolations [玩家] §7- 重置违规次数");
        sender.sendMessage("§e/cf stats §7- 查看插件统计信息");
    }

    private boolean reloadCommand(CommandSender sender) {
        try {
            reloadConfig();
            wordsConfig = YamlConfiguration.loadConfiguration(wordsFile);
            blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
            loadAndValidateConfiguration();
            sender.sendMessage("§aChatFilter 配置已重载!");
            logger.logConfigReload("手动重载", true);
            return true;
        } catch (Exception e) {
            sender.sendMessage("§c配置重载失败: " + e.getMessage());
            logger.logConfigReload("手动重载", false);
            return true;
        }
    }

    private boolean addWordCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /cf addword <词语>");
            return true;
        }

        String word = args[1];
        List<String> words = wordsConfig.getStringList("sensitive-words");
        if (!words.contains(word)) {
            words.add(word);
            wordsConfig.set("sensitive-words", words);
            try {
                wordsConfig.save(wordsFile);
                sensitiveWords.add(word);
                wordMatcher.build(sensitiveWords);
                sender.sendMessage("§a已添加敏感词: " + word);
                logger.info("管理员 " + sender.getName() + " 添加敏感词: " + word);
            } catch (IOException e) {
                sender.sendMessage("§c保存失败: " + e.getMessage());
                logger.warning("保存敏感词文件失败", e);
            }
        } else {
            sender.sendMessage("§c敏感词已存在: " + word);
        }
        return true;
    }

    private boolean removeWordCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /cf removeword <词语>");
            return true;
        }

        String word = args[1];
        List<String> words = wordsConfig.getStringList("sensitive-words");
        if (words.contains(word)) {
            words.remove(word);
            wordsConfig.set("sensitive-words", words);
            try {
                wordsConfig.save(wordsFile);
                sensitiveWords.remove(word);
                wordMatcher.build(sensitiveWords);
                sender.sendMessage("§a已删除敏感词: " + word);
                logger.info("管理员 " + sender.getName() + " 删除敏感词: " + word);
            } catch (IOException e) {
                sender.sendMessage("§c保存失败: " + e.getMessage());
                logger.warning("保存敏感词文件失败", e);
            }
        } else {
            sender.sendMessage("§c敏感词不存在: " + word);
        }
        return true;
    }

    private boolean listWordsCommand(CommandSender sender) {
        List<String> words = new ArrayList<>(sensitiveWords);
        sender.sendMessage("§6===== 敏感词列表 (" + words.size() + "个) =====");
        for (int i = 0; i < words.size(); i++) {
            sender.sendMessage("§e" + (i + 1) + ". §7" + words.get(i));
        }
        return true;
    }

    private boolean addBlacklistCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /cf addblacklist <玩家>");
            return true;
        }

        String player = args[1];
        List<String> blacklist = blacklistConfig.getStringList("blacklist-players");
        if (!blacklist.contains(player)) {
            blacklist.add(player);
            blacklistConfig.set("blacklist-players", blacklist);
            try {
                blacklistConfig.save(blacklistFile);
                blacklistPlayers.add(player);
                sender.sendMessage("§a已添加黑名单玩家: " + player);
                logger.info("管理员 " + sender.getName() + " 添加黑名单玩家: " + player);
            } catch (IOException e) {
                sender.sendMessage("§c保存失败: " + e.getMessage());
                logger.warning("保存黑名单文件失败", e);
            }
        } else {
            sender.sendMessage("§c黑名单玩家已存在: " + player);
        }
        return true;
    }

    private boolean removeBlacklistCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /cf removeblacklist <玩家>");
            return true;
        }

        String player = args[1];
        List<String> blacklist = blacklistConfig.getStringList("blacklist-players");
        if (blacklist.contains(player)) {
            blacklist.remove(player);
            blacklistConfig.set("blacklist-players", blacklist);
            try {
                blacklistConfig.save(blacklistFile);
                blacklistPlayers.remove(player);
                sender.sendMessage("§a已删除黑名单玩家: " + player);
                logger.info("管理员 " + sender.getName() + " 删除黑名单玩家: " + player);
            } catch (IOException e) {
                sender.sendMessage("§c保存失败: " + e.getMessage());
                logger.warning("保存黑名单文件失败", e);
            }
        } else {
            sender.sendMessage("§c黑名单玩家不存在: " + player);
        }
        return true;
    }

    private boolean listBlacklistCommand(CommandSender sender) {
        List<String> blacklist = new ArrayList<>(blacklistPlayers);
        sender.sendMessage("§6===== 黑名单玩家列表 (" + blacklist.size() + "个) =====");
        for (int i = 0; i < blacklist.size(); i++) {
            sender.sendMessage("§e" + (i + 1) + ". §7" + blacklist.get(i));
        }
        return true;
    }

    private boolean testCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /cf test <消息>");
            return true;
        }

        StringBuilder message = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            message.append(args[i]).append(" ");
        }

        String detectedWord = containsSensitiveWord(message.toString().trim());
        if (detectedWord != null) {
            sender.sendMessage("§c检测到敏感词: " + detectedWord);
        } else {
            sender.sendMessage("§a未检测到敏感词");
        }
        return true;
    }

    private boolean viewViolationsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Map<String, Integer> violations = violationCounter.getAllViolations();
            sender.sendMessage("§6===== 玩家违规次数 (" + violations.size() + "人) =====");
            for (Map.Entry<String, Integer> entry : violations.entrySet()) {
                sender.sendMessage("§e" + entry.getKey() + ": §7" + entry.getValue() + " 次");
            }
        } else {
            String playerName = args[1];
            int count = violationCounter.getViolationCount(playerName);
            sender.sendMessage("§e玩家 " + playerName + " 今日违规次数: §7" + count + " 次");
        }
        return true;
    }

    private boolean resetViolationsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            int resetCount = violationCounter.resetAllViolations();
            sender.sendMessage("§a已重置 " + resetCount + " 个玩家的违规次数");
            logger.info("管理员 " + sender.getName() + " 重置了所有玩家的违规次数");
        } else {
            String playerName = args[1];
            int oldCount = violationCounter.resetPlayerViolations(playerName);
            if (oldCount > 0) {
                sender.sendMessage("§a已重置玩家 " + playerName + " 的违规次数 (原: " + oldCount + " 次)");
                logger.info("管理员 " + sender.getName() + " 重置了玩家 " + playerName + " 的违规次数");
            } else {
                sender.sendMessage("§c玩家 " + playerName + " 没有违规记录");
            }
        }
        return true;
    }

    private boolean statsCommand(CommandSender sender) {
        sender.sendMessage("§6===== ChatFilter 统计信息 =====");
        sender.sendMessage("§e插件状态: §7" + (enabled ? "启用" : "禁用"));
        sender.sendMessage("§e敏感词数量: §7" + sensitiveWords.size());
        sender.sendMessage("§e黑名单玩家: §7" + blacklistPlayers.size());
        sender.sendMessage("§e违规玩家数: §7" + violationCounter.getViolationPlayerCount());
        sender.sendMessage("§e总违规次数: §7" + violationCounter.getTotalViolations());
        sender.sendMessage("§e检测模式: §7" + (useRegex ? "正则表达式" : "字符串匹配"));
        sender.sendMessage("§e大小写敏感: §7" + (caseSensitive ? "是" : "否"));
        sender.sendMessage("§e上次重置: §7" + violationCounter.getLastResetDate());
        sender.sendMessage("§e日志级别: §7" + logger.getCurrentLevel());
        sender.sendMessage("§e文件日志: §7" + (logger.isFileLoggingEnabled() ? "启用" : "禁用"));
        return true;
    }
}