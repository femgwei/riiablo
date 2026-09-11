package com.d2moo.common.util;

import java.util.IllegalFormatException;

/** 日志工具类。 */
public class D2Log {
    private static boolean debugEnabled() {
        return !Boolean.getBoolean("riiablo.d2gs.quiet")
                || Boolean.getBoolean("riiablo.d2moo.verbose");
    }

    /** C++ diagnostics often contain a literal '%' and no formatter args. */
    private static String format(String message, Object... args) {
        if (message == null) return "null";
        if (args == null || args.length == 0) return message;
        try {
            return String.format(message, args);
        } catch (IllegalFormatException e) {
            StringBuilder fallback = new StringBuilder(message).append(" [args=");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) fallback.append(',');
                fallback.append(String.valueOf(args[i]));
            }
            return fallback.append(']').toString();
        }
    }

    public static void debug(LogLevel level, String message, Object... args) {
        if (!debugEnabled()) return;
        String formattedMessage = format(message, args);
        System.out.println(String.format("[D2Log][%s] %s", level.name(), formattedMessage));
    }

    public static void debug(String message, Object... args) {
        debug(LogLevel.DEBUG, message, args);
    }

    public static void warning(String message, Object... args) {
        String formattedMessage = format(message, args);
        System.err.println(String.format("[D2Log][WARNING] %s", formattedMessage));
    }

    public static void error(String message, Object... args) {
        String formattedMessage = format(message, args);
        System.err.println(String.format("[D2Log][ERROR] %s", formattedMessage));
    }

    public static void trace(String message, Object... args) {
        if (!debugEnabled()) return;
        String formattedMessage = format(message, args);
        System.out.println(String.format("[D2Log][TRACE] %s", formattedMessage));
    }

    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
