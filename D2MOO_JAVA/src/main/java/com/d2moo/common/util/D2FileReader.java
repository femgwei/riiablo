package com.d2moo.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件读取工具类
 * 用于从存档或文件系统读取游戏文件（DS1、DT1 等）
 * 
 * 功能：
 * 1. 从文件系统读取文件
 * 2. 从存档（MPQ）读取文件（待实现）
 * 3. 支持路径转换和查找
 */
public class D2FileReader {

    /** Minimal archive adapter so the DRLG module stays independent of riiablo. */
    @FunctionalInterface
    public interface ArchiveReader {
        byte[] read(String fileName) throws IOException;
    }
    
    /**
     * 从文件系统或存档读取文件
     * 
     * 优先级：
     * 1. 如果 hArchive 不为 null，尝试从存档读取
     * 2. 否则，从文件系统读取
     * 
     * @param hArchive 存档句柄（MPQ 存档，可为 null）
     * @param fileName 文件名（如 "DATA\\GLOBAL\\Tiles\\Act1\\Town\\Floor.dt1"）
     * @return 文件数据，如果读取失败返回 null
     */
    public static byte[] readFile(Object hArchive, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            D2Log.warning("D2FileReader_ReadFile: Invalid file name");
            return null;
        }
        
        // 尝试从存档读取（如果存档句柄不为 null）
        if (hArchive != null) {
            byte[] archiveData = readFromArchive(hArchive, fileName);
            if (archiveData != null) {
                return archiveData;
            }
            // 如果存档读取失败，继续尝试文件系统
        }
        
        // 从文件系统读取
        return readFromFileSystem(fileName);
    }
    
    /**
     * 从存档读取文件（MPQ 格式）
     * 
     * 注意：MPQ 存档读取逻辑可以在后续根据需要实现
     * 当前实现：返回 null，表示从存档读取失败，会回退到文件系统读取
     * 
     * MPQ 存档读取实现步骤（参考）：
     * 1. 打开 MPQ 文件（使用 MPQ 库，如 StormLib 或 Java 实现）
     * 2. 查找文件（根据文件名在 MPQ 哈希表中查找）
     * 3. 读取文件数据（解压缩并读取文件内容）
     * 4. 返回字节数组
     * 
     * 当前框架实现：
     * - 如果 archive 为 null，readFile 会直接从文件系统读取
     * - 如果 archive 不为 null 但 readFromArchive 返回 null，readFile 也会回退到文件系统读取
     * - 这种设计允许在 MPQ 读取未实现时，系统仍能正常工作（从文件系统读取）
     * 
     * @param hArchive 存档句柄（MPQ 存档对象，可为 null）
     * @param fileName 文件名（如 "DATA\\GLOBAL\\Tiles\\Act1\\Town\\Floor.dt1"）
     * @return 文件数据，如果读取失败返回 null（会触发回退到文件系统读取）
     */
    private static byte[] readFromArchive(Object hArchive, String fileName) {
        if (hArchive == null || fileName == null || fileName.isEmpty()) {
            return null;
        }
        
        if (hArchive instanceof ArchiveReader) {
            try {
                byte[] data = ((ArchiveReader) hArchive).read(fileName);
                if (data != null && data.length > 0) {
                    D2Log.debug("D2FileReader_ReadFromArchive: read file=%s size=%d",
                            fileName, data.length);
                    return data;
                }
            } catch (IOException | RuntimeException e) {
                D2Log.warning("D2FileReader_ReadFromArchive: Failed file=%s error=%s",
                        fileName, e.getMessage());
            }
        }

        // 注意：其他 MPQ 存档读取逻辑可以在后续根据需要实现
        // 当前实现：返回 null，表示从存档读取失败，会触发 readFile 回退到文件系统读取
        // 这种设计允许在 MPQ 读取未实现时，系统仍能正常工作
        D2Log.debug("D2FileReader_ReadFromArchive: Archive reading not implemented yet, will fallback to filesystem, file: " + fileName);
        return null;
    }
    
    /**
     * 从文件系统读取文件
     * 
     * @param fileName 文件名（支持 Windows 路径分隔符）
     * @return 文件数据，如果读取失败返回 null
     */
    private static byte[] readFromFileSystem(String fileName) {
        try {
            // 转换路径分隔符（Windows 使用 \，Java 使用 /）
            String normalizedPath = fileName.replace('\\', File.separatorChar);
            
            // 尝试多种路径查找策略
            String[] searchPaths = {
                normalizedPath,                                    // 直接路径
                "data" + File.separator + normalizedPath,         // data/ 前缀
                "Data" + File.separator + normalizedPath,         // Data/ 前缀
                "DATA" + File.separator + normalizedPath,         // DATA/ 前缀
                System.getProperty("user.dir") + File.separator + normalizedPath,  // 当前工作目录
            };
            
            for (String searchPath : searchPaths) {
                Path filePath = Paths.get(searchPath);
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    // 读取文件
                    byte[] fileData = Files.readAllBytes(filePath);
                    D2Log.debug("D2FileReader_ReadFromFileSystem: Successfully read file: " + searchPath + " (size: " + fileData.length + " bytes)");
                    return fileData;
                }
            }
            
            // 如果所有路径都找不到，记录警告
            D2Log.warning("D2FileReader_ReadFromFileSystem: File not found: " + fileName);
            return null;
            
        } catch (IOException e) {
            D2Log.warning("D2FileReader_ReadFromFileSystem: Error reading file: " + fileName + ", error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            D2Log.warning("D2FileReader_ReadFromFileSystem: Unexpected error reading file: " + fileName + ", error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 检查文件是否存在
     * 
     * @param fileName 文件名
     * @return 如果文件存在返回 true，否则返回 false
     */
    public static boolean fileExists(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String normalizedPath = fileName.replace('\\', File.separatorChar);
        String[] searchPaths = {
            normalizedPath,
            "data" + File.separator + normalizedPath,
            "Data" + File.separator + normalizedPath,
            "DATA" + File.separator + normalizedPath,
            System.getProperty("user.dir") + File.separator + normalizedPath,
        };
        
        for (String searchPath : searchPaths) {
            Path filePath = Paths.get(searchPath);
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 从输入流读取文件数据
     * 
     * @param inputStream 输入流
     * @return 文件数据，如果读取失败返回 null
     */
    public static byte[] readFromStream(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        
        try {
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            return outputStream.toByteArray();
            
        } catch (IOException e) {
            D2Log.warning("D2FileReader_ReadFromStream: Error reading from stream: " + e.getMessage());
            return null;
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // 忽略关闭错误
            }
        }
    }
}
