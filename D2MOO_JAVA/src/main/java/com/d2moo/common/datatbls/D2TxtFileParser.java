package com.d2moo.common.datatbls;

import com.d2moo.common.util.D2Log;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Diablo 2 TXT 文件解析器
 * 
 * Diablo 2 的 TXT 数据表文件格式：
 * - 第一行：列名（制表符分隔）
 * - 后续行：数据行（制表符分隔）
 * - 空行或注释行（以 // 开头）会被跳过
 * - 字段值可能是整数、字符串或空值
 */
public class D2TxtFileParser {
    
    /**
     * 解析 TXT 文件，返回行数据列表
     * 
     * @param fileData 文件数据（字节数组）
     * @return 行数据列表，每行是一个字符串数组（字段列表）
     */
    public static List<String[]> parseTxtFile(byte[] fileData) {
        List<String[]> rows = new ArrayList<>();
        
        if (fileData == null || fileData.length == 0) {
            return rows;
        }
        
        // 将字节数组转换为字符串
        String content = new String(fileData, StandardCharsets.UTF_8);
        
        // 按行分割
        String[] lines = content.split("\r\n|\n|\r");
        
        for (String line : lines) {
            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }
            
            // 跳过注释行（以 // 开头）
            if (line.trim().startsWith("//")) {
                continue;
            }
            
            // 按制表符分割字段
            String[] fields = line.split("\t", -1); // -1 保留空字段

            // UTF-8 表格可能带 BOM；它属于文件签名而不是第一列名称。
            if (rows.isEmpty() && fields.length > 0 && !fields[0].isEmpty()
                    && fields[0].charAt(0) == '\uFEFF') {
                fields[0] = fields[0].substring(1);
            }
            
            // 移除字段前后的空白字符
            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].trim();
            }
            
            rows.add(fields);
        }
        
        return rows;
    }
    
    /**
     * 解析整数字段值
     * 
     * @param value 字符串值
     * @param defaultValue 默认值（如果解析失败）
     * @return 整数值
     */
    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            // 处理十六进制值（以 0x 开头）
            String trimmed = value.trim();
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                long parsed = Long.parseLong(trimmed.substring(2), 16);
                if ((parsed & ~0xFFFFFFFFL) != 0) {
                    return defaultValue;
                }
                return (int) parsed;
            }
            
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            D2Log.debug("Failed to parse integer: " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 解析字符串字段值
     * 
     * @param value 字符串值
     * @param defaultValue 默认值（如果为空）
     * @return 字符串值
     */
    public static String parseString(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        return value.trim();
    }
    
    /**
     * 查找列索引（根据列名）
     * 
     * @param headerRow 表头行（列名数组）
     * @param columnName 列名
     * @return 列索引，如果未找到返回 -1
     */
    public static int findColumnIndex(String[] headerRow, String columnName) {
        if (headerRow == null || columnName == null) {
            return -1;
        }
        
        for (int i = 0; i < headerRow.length; i++) {
            if (columnName.equalsIgnoreCase(headerRow[i].trim())) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * 获取字段值（根据列索引）
     * 
     * @param row 数据行
     * @param columnIndex 列索引
     * @param defaultValue 默认值
     * @return 字段值
     */
    public static String getFieldValue(String[] row, int columnIndex, String defaultValue) {
        if (row == null || columnIndex < 0 || columnIndex >= row.length) {
            return defaultValue;
        }
        
        String value = row[columnIndex];
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        return value.trim();
    }
}
