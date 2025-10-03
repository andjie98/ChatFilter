package com.laoda.chatfilter.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的违规计数器
 * 支持自动重置和并发访问
 */
public class ViolationCounter {
    
    private final ConcurrentHashMap<String, AtomicInteger> violationCounts;
    private final ReadWriteLock resetLock;
    private volatile String lastResetDate;
    private final SimpleDateFormat dateFormat;
    
    public ViolationCounter() {
        this.violationCounts = new ConcurrentHashMap<>();
        this.resetLock = new ReentrantReadWriteLock();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.lastResetDate = dateFormat.format(new Date());
    }
    
    /**
     * 增加玩家的违规次数
     * @param playerName 玩家名
     * @return 增加后的违规次数
     */
    public int incrementViolation(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("玩家名不能为空");
        }
        
        resetLock.readLock().lock();
        try {
            return violationCounts.computeIfAbsent(playerName, k -> new AtomicInteger(0))
                                 .incrementAndGet();
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 获取玩家的违规次数
     * @param playerName 玩家名
     * @return 违规次数
     */
    public int getViolationCount(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return 0;
        }
        
        resetLock.readLock().lock();
        try {
            AtomicInteger count = violationCounts.get(playerName);
            return count != null ? count.get() : 0;
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 重置特定玩家的违规次数
     * @param playerName 玩家名
     * @return 重置前的违规次数
     */
    public int resetPlayerViolations(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return 0;
        }
        
        resetLock.readLock().lock();
        try {
            AtomicInteger count = violationCounts.remove(playerName);
            return count != null ? count.get() : 0;
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 重置所有玩家的违规次数
     * @return 重置的玩家数量
     */
    public int resetAllViolations() {
        resetLock.writeLock().lock();
        try {
            int resetCount = violationCounts.size();
            violationCounts.clear();
            lastResetDate = dateFormat.format(new Date());
            return resetCount;
        } finally {
            resetLock.writeLock().unlock();
        }
    }
    
    /**
     * 检查是否需要每日重置
     * @return 如果执行了重置则返回重置的玩家数量，否则返回 -1
     */
    public int checkAndResetDaily() {
        String currentDate = dateFormat.format(new Date());
        
        if (!currentDate.equals(lastResetDate)) {
            return resetAllViolations();
        }
        
        return -1;
    }
    
    /**
     * 获取所有玩家的违规次数（只读副本）
     * @return 玩家名到违规次数的映射
     */
    public Map<String, Integer> getAllViolations() {
        resetLock.readLock().lock();
        try {
            Map<String, Integer> result = new ConcurrentHashMap<>();
            for (Map.Entry<String, AtomicInteger> entry : violationCounts.entrySet()) {
                result.put(entry.getKey(), entry.getValue().get());
            }
            return result;
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 获取违规玩家总数
     * @return 有违规记录的玩家数量
     */
    public int getViolationPlayerCount() {
        resetLock.readLock().lock();
        try {
            return violationCounts.size();
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 获取总违规次数
     * @return 所有玩家的违规次数总和
     */
    public int getTotalViolations() {
        resetLock.readLock().lock();
        try {
            return violationCounts.values().stream()
                                 .mapToInt(AtomicInteger::get)
                                 .sum();
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 检查玩家是否有违规记录
     * @param playerName 玩家名
     * @return 如果有违规记录返回 true
     */
    public boolean hasViolations(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return false;
        }
        
        resetLock.readLock().lock();
        try {
            return violationCounts.containsKey(playerName);
        } finally {
            resetLock.readLock().unlock();
        }
    }
    
    /**
     * 获取上次重置日期
     * @return 上次重置的日期字符串
     */
    public String getLastResetDate() {
        return lastResetDate;
    }
    
    /**
     * 清理空的计数器条目（计数为0的条目）
     * @return 清理的条目数量
     */
    public int cleanupEmptyEntries() {
        resetLock.writeLock().lock();
        try {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, AtomicInteger> entry : violationCounts.entrySet()) {
                if (entry.getValue().get() <= 0) {
                    toRemove.add(entry.getKey());
                }
            }
            
            for (String key : toRemove) {
                violationCounts.remove(key);
            }
            
            return toRemove.size();
        } finally {
            resetLock.writeLock().unlock();
        }
    }
}