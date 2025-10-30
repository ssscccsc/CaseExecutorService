package com.caseexecute.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 日志上传问题诊断工具
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
public class LogUploadDiagnosticTool {
    
    /**
     * 诊断日志文件路径问题
     * 
     * @param logFilePath 日志文件路径
     * @param taskId 任务ID
     * @param testCaseId 用例ID
     * @param round 轮次
     */
    public static void diagnoseLogFilePath(String logFilePath, String taskId, Long testCaseId, Integer round) {
        log.info("=== 日志文件路径诊断开始 ===");
        log.info("输入参数:");
        log.info("  - 日志文件路径: {}", logFilePath);
        log.info("  - 任务ID: {}", taskId);
        log.info("  - 用例ID: {}", testCaseId);
        log.info("  - 轮次: {}", round);
        
        try {
            // 1. 检查路径是否为空
            if (logFilePath == null || logFilePath.trim().isEmpty()) {
                log.error("❌ 日志文件路径为空");
                return;
            }
            
            // 2. 解析路径
            Path path = Paths.get(logFilePath);
            log.info("路径解析结果:");
            log.info("  - 原始路径: {}", logFilePath);
            log.info("  - 解析后路径: {}", path.toString());
            log.info("  - 是否为绝对路径: {}", path.isAbsolute());
            
            // 3. 转换为绝对路径
            Path absolutePath = path.toAbsolutePath();
            log.info("  - 绝对路径: {}", absolutePath.toString());
            
            // 4. 检查文件是否存在
            boolean exists = Files.exists(absolutePath);
            log.info("  - 文件是否存在: {}", exists);
            
            if (exists) {
                // 5. 检查文件属性
                boolean isReadable = Files.isReadable(absolutePath);
                boolean isRegularFile = Files.isRegularFile(absolutePath);
                long fileSize = Files.size(absolutePath);
                
                log.info("文件属性:");
                log.info("  - 是否可读: {}", isReadable);
                log.info("  - 是否为普通文件: {}", isRegularFile);
                log.info("  - 文件大小: {} bytes", fileSize);
                
                if (fileSize == 0) {
                    log.warn("⚠️ 文件大小为0，可能为空文件");
                }
                
                // 6. 检查父目录
                Path parentDir = absolutePath.getParent();
                if (parentDir != null) {
                    boolean parentExists = Files.exists(parentDir);
                    boolean parentIsDirectory = Files.isDirectory(parentDir);
                    boolean parentIsWritable = Files.isWritable(parentDir);
                    
                    log.info("父目录信息:");
                    log.info("  - 父目录路径: {}", parentDir.toString());
                    log.info("  - 父目录是否存在: {}", parentExists);
                    log.info("  - 父目录是否为目录: {}", parentIsDirectory);
                    log.info("  - 父目录是否可写: {}", parentIsWritable);
                }
                
                // 7. 列出父目录内容
                if (parentDir != null && Files.exists(parentDir)) {
                    try {
                        log.info("父目录内容:");
                        Files.list(parentDir).forEach(child -> {
                            try {
                                boolean isFile = Files.isRegularFile(child);
                                boolean isDir = Files.isDirectory(child);
                                long size = Files.size(child);
                                log.info("  - {} ({}): {} bytes", 
                                    child.getFileName().toString(), 
                                    isFile ? "文件" : (isDir ? "目录" : "其他"), 
                                    size);
                            } catch (IOException e) {
                                log.warn("  - {}: 无法获取信息", child.getFileName().toString());
                            }
                        });
                    } catch (IOException e) {
                        log.error("无法列出父目录内容: {}", e.getMessage());
                    }
                }
                
            } else {
                log.error("❌ 文件不存在，开始检查目录结构");
                
                // 8. 检查目录结构
                Path currentPath = absolutePath;
                while (currentPath != null && !Files.exists(currentPath)) {
                    log.info("  - 检查路径: {} (不存在)", currentPath.toString());
                    currentPath = currentPath.getParent();
                }
                
                if (currentPath != null) {
                    log.info("  - 找到存在的路径: {}", currentPath.toString());
                } else {
                    log.error("  - 所有父路径都不存在");
                }
            }
            
            // 9. 检查工作目录
            String currentDir = System.getProperty("user.dir");
            log.info("当前工作目录: {}", currentDir);
            
            // 10. 检查临时目录
            String tempDir = System.getProperty("java.io.tmpdir");
            log.info("系统临时目录: {}", tempDir);
            
        } catch (Exception e) {
            log.error("❌ 诊断过程中发生错误: {}", e.getMessage(), e);
        }
        
        log.info("=== 日志文件路径诊断结束 ===");
    }
    
    /**
     * 诊断gohttpserver连接
     * 
     * @param goHttpServerUrl gohttpserver地址
     * @param taskId 任务ID
     */
    public static void diagnoseGoHttpServer(String goHttpServerUrl, String taskId) {
        log.info("=== GoHttpServer连接诊断开始 ===");
        log.info("输入参数:");
        log.info("  - GoHttpServer地址: {}", goHttpServerUrl);
        log.info("  - 任务ID: {}", taskId);
        
        if (goHttpServerUrl == null || goHttpServerUrl.trim().isEmpty()) {
            log.error("❌ GoHttpServer地址为空");
            return;
        }
        
        // 构建上传URL
        String uploadUrl;
        if (taskId != null && !taskId.trim().isEmpty()) {
            uploadUrl = goHttpServerUrl + "/upload/" + taskId;
        } else {
            uploadUrl = goHttpServerUrl + "/upload";
        }
        
        log.info("构建的上传URL: {}", uploadUrl);
        
        // 检查URL格式
        if (!uploadUrl.startsWith("http://") && !uploadUrl.startsWith("https://")) {
            log.warn("⚠️ URL格式可能不正确，缺少协议前缀");
        }
        
        log.info("=== GoHttpServer连接诊断结束 ===");
    }
}
