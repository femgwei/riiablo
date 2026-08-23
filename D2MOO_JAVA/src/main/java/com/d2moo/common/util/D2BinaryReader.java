package com.d2moo.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 二进制数据读取工具类
 * 用于解析游戏文件（DS1、DT1 等）的二进制格式
 * 
 * 功能：
 * 1. 读取各种数据类型（int32、int16、byte、string 等）
 * 2. 支持小端序（Little Endian）和大端序（Big Endian）
 * 3. 边界检查和错误处理
 */
public class D2BinaryReader {
    
    /**
     * 从字节数组中读取 32 位整数（小端序，Diablo 2 使用）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 32 位整数，如果越界返回 0
     */
    public static int readInt32(byte[] data, int offset) {
        if (!hasEnoughData(data, offset, Integer.BYTES)) {
            D2Log.warning("D2BinaryReader_ReadInt32: Invalid offset or data length, offset: " + offset + ", length: " + (data != null ? data.length : 0));
            return 0;
        }
        
        // 小端序：最低有效字节在前
        return ((data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24));
    }
    
    /**
     * 从字节数组中读取 32 位无符号整数（小端序）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 32 位无符号整数（作为 long），如果越界返回 0
     */
    public static long readUInt32(byte[] data, int offset) {
        if (!hasEnoughData(data, offset, Integer.BYTES)) {
            return 0;
        }
        
        // 小端序：最低有效字节在前
        return ((long)(data[offset] & 0xFF) |
                ((long)(data[offset + 1] & 0xFF) << 8) |
                ((long)(data[offset + 2] & 0xFF) << 16) |
                ((long)(data[offset + 3] & 0xFF) << 24)) & 0xFFFFFFFFL;
    }
    
    /**
     * 从字节数组中读取 16 位整数（小端序）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 16 位整数，如果越界返回 0
     */
    public static int readInt16(byte[] data, int offset) {
        if (!hasEnoughData(data, offset, Short.BYTES)) {
            return 0;
        }
        
        // 小端序：最低有效字节在前
        return (short) ((data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8));
    }
    
    /**
     * 从字节数组中读取 16 位无符号整数（小端序）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 16 位无符号整数（作为 int），如果越界返回 0
     */
    public static int readUInt16(byte[] data, int offset) {
        if (!hasEnoughData(data, offset, Short.BYTES)) {
            return 0;
        }
        
        // 小端序：最低有效字节在前
        return ((data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8)) & 0xFFFF;
    }
    
    /**
     * 从字节数组中读取 8 位整数（有符号字节）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 8 位整数，如果越界返回 0
     */
    public static int readInt8(byte[] data, int offset) {
        if (data == null || offset < 0 || offset >= data.length) {
            return 0;
        }
        
        return data[offset];
    }
    
    /**
     * 从字节数组中读取 8 位无符号整数
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 8 位无符号整数（作为 int），如果越界返回 0
     */
    public static int readUInt8(byte[] data, int offset) {
        if (data == null || offset < 0 || offset >= data.length) {
            return 0;
        }
        
        return data[offset] & 0xFF;
    }
    
    /**
     * 从字节数组中读取字符串（以 null 结尾）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @param maxLength 最大长度
     * @return 字符串，如果越界返回空字符串
     */
    public static String readNullTerminatedString(byte[] data, int offset, int maxLength) {
        if (data == null || offset < 0 || offset >= data.length) {
            return "";
        }
        
        int endOffset = offset;
        int length = 0;
        
        // 查找 null 终止符
        while (endOffset < data.length && length < maxLength && data[endOffset] != 0) {
            endOffset++;
            length++;
        }
        
        if (length == 0) {
            return "";
        }
        
        // 转换为字符串（假设使用 ASCII 编码）
        byte[] stringBytes = new byte[length];
        System.arraycopy(data, offset, stringBytes, 0, length);
        return new String(stringBytes, StandardCharsets.US_ASCII);
    }
    
    /**
     * 从字节数组中读取固定长度的字符串
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @param length 字符串长度
     * @return 字符串，如果越界返回空字符串
     */
    public static String readString(byte[] data, int offset, int length) {
        if (!hasEnoughData(data, offset, length)) {
            return "";
        }
        
        // 转换为字符串（假设使用 ASCII 编码）
        byte[] stringBytes = new byte[length];
        System.arraycopy(data, offset, stringBytes, 0, length);
        return new String(stringBytes, StandardCharsets.US_ASCII);
    }
    
    /**
     * 检查是否有足够的数据读取指定长度的数据
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @param length 需要读取的长度
     * @return 如果有足够的数据返回 true，否则返回 false
     */
    public static boolean hasEnoughData(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset > data.length) {
            return false;
        }
        
        // 使用减法避免 offset + length 的 int 溢出。
        return length <= data.length - offset;
    }
    
    /**
     * 从字节数组中读取浮点数（32 位，小端序）
     * 
     * @param data 字节数组
     * @param offset 偏移位置
     * @return 浮点数，如果越界返回 0.0f
     */
    public static float readFloat32(byte[] data, int offset) {
        if (!hasEnoughData(data, offset, Float.BYTES)) {
            return 0.0f;
        }
        
        int bits = readInt32(data, offset);
        return Float.intBitsToFloat(bits);
    }

    /** Reads a signed 64-bit little-endian integer. */
    public static long readInt64(byte[] data, int offset) {
        if (!hasEnoughData(data, offset, Long.BYTES)) {
            return 0L;
        }

        return ((long) data[offset] & 0xFFL)
                | (((long) data[offset + 1] & 0xFFL) << 8)
                | (((long) data[offset + 2] & 0xFFL) << 16)
                | (((long) data[offset + 3] & 0xFFL) << 24)
                | (((long) data[offset + 4] & 0xFFL) << 32)
                | (((long) data[offset + 5] & 0xFFL) << 40)
                | (((long) data[offset + 6] & 0xFFL) << 48)
                | (((long) data[offset + 7] & 0xFFL) << 56);
    }

    /** Returns an independent copy of a byte range, or an empty array when invalid. */
    public static byte[] readBytes(byte[] data, int offset, int length) {
        if (!hasEnoughData(data, offset, length)) {
            return new byte[0];
        }
        return Arrays.copyOfRange(data, offset, offset + length);
    }
}
