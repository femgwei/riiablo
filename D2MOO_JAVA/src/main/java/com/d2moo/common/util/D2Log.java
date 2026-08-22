package com.d2moo.common.util;

/**
 * 日志工具类
 * 封装统一的日志函数，调试日志采用英文
 */
public class D2Log {
    
    /**
     * 记录调试日志
     * @param level 日志级别
     * @param message 日志消息（英文）
     * @param args 参数
     */
    public static void debug(LogLevel level, String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.out.println(String.format("[D2Log][%s] %s", level.name(), formattedMessage));
    }
    
    /**
     * 记录调试日志（使用默认 DEBUG 级别）
     * @param message 日志消息（英文）
     * @param args 参数
     */
    public static void debug(String message, Object... args) {
        debug(LogLevel.DEBUG, message, args);
    }
    
    /**
     * 记录警告日志
     * @param message 警告消息（英文）
     * @param args 参数
     */
    public static void warning(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.err.println(String.format("[D2Log][WARNING] %s", formattedMessage));
    }
    
    /**
     * 记录错误日志
     * @param message 错误消息（英文）
     * @param args 参数
     */
    public static void error(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.err.println(String.format("[D2Log][ERROR] %s", formattedMessage));
    }
    
    /**
     * 记录跟踪日志（用于调试）
     * @param message 跟踪消息（英文）
     * @param args 参数
     */
    public static void trace(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        System.out.println(String.format("[D2Log][TRACE] %s", formattedMessage));
    }
    
    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
