package com.laoda.chatfilter.algorithm;

import java.util.*;

/**
 * Aho-Corasick 算法实现
 * 用于高效的多模式字符串匹配
 */
public class AhoCorasick {
    
    private final TrieNode root;
    private final boolean caseSensitive;
    
    public AhoCorasick(boolean caseSensitive) {
        this.root = new TrieNode();
        this.caseSensitive = caseSensitive;
    }
    
    /**
     * 构建 Trie 树和失败函数
     */
    public void build(Collection<String> patterns) {
        // 清空现有的 Trie 树
        root.children.clear();
        root.fail = null;
        root.output.clear();
        
        // 构建 Trie 树
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            
            String processedPattern = caseSensitive ? pattern : pattern.toLowerCase();
            TrieNode current = root;
            
            for (char c : processedPattern.toCharArray()) {
                current.children.putIfAbsent(c, new TrieNode());
                current = current.children.get(c);
            }
            current.output.add(pattern); // 保存原始模式
        }
        
        // 构建失败函数
        buildFailureFunction();
    }
    
    /**
     * 搜索文本中的所有匹配模式
     */
    public List<MatchResult> search(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<MatchResult> results = new ArrayList<>();
        String processedText = caseSensitive ? text : text.toLowerCase();
        
        TrieNode current = root;
        
        for (int i = 0; i < processedText.length(); i++) {
            char c = processedText.charAt(i);
            
            // 沿着失败函数寻找匹配
            while (current != root && !current.children.containsKey(c)) {
                current = current.fail;
            }
            
            if (current.children.containsKey(c)) {
                current = current.children.get(c);
            }
            
            // 检查当前节点和其失败链上的所有输出
            TrieNode temp = current;
            while (temp != null) {
                for (String pattern : temp.output) {
                    results.add(new MatchResult(pattern, i - pattern.length() + 1, i));
                }
                temp = temp.fail;
            }
        }
        
        return results;
    }
    
    /**
     * 检查文本是否包含任何模式
     */
    public String findFirst(String text) {
        List<MatchResult> results = search(text);
        return results.isEmpty() ? null : results.get(0).getPattern();
    }
    
    /**
     * 构建失败函数
     */
    private void buildFailureFunction() {
        Queue<TrieNode> queue = new LinkedList<>();
        
        // 初始化根节点的直接子节点
        for (TrieNode child : root.children.values()) {
            child.fail = root;
            queue.offer(child);
        }
        
        // BFS 构建失败函数
        while (!queue.isEmpty()) {
            TrieNode current = queue.poll();
            
            for (Map.Entry<Character, TrieNode> entry : current.children.entrySet()) {
                char c = entry.getKey();
                TrieNode child = entry.getValue();
                queue.offer(child);
                
                // 寻找失败节点
                TrieNode fail = current.fail;
                while (fail != null && !fail.children.containsKey(c)) {
                    fail = fail.fail;
                }
                
                child.fail = (fail != null) ? fail.children.get(c) : root;
                
                // 合并输出
                child.output.addAll(child.fail.output);
            }
        }
    }
    
    /**
     * Trie 树节点
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        TrieNode fail;
        Set<String> output = new HashSet<>();
    }
    
    /**
     * 匹配结果
     */
    public static class MatchResult {
        private final String pattern;
        private final int start;
        private final int end;
        
        public MatchResult(String pattern, int start, int end) {
            this.pattern = pattern;
            this.start = start;
            this.end = end;
        }
        
        public String getPattern() { return pattern; }
        public int getStart() { return start; }
        public int getEnd() { return end; }
        
        @Override
        public String toString() {
            return String.format("Match{pattern='%s', start=%d, end=%d}", pattern, start, end);
        }
    }
}