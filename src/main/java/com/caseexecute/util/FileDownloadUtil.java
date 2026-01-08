package com.caseexecute.util;

import com.caseexecute.config.FileStorageConfig;
import com.caseexecute.config.GoHttpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文件下载工具类
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Component
public class FileDownloadUtil implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    private static FileStorageConfig fileStorageConfig;
    private static GoHttpServerConfig goHttpServerConfig;
    
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        FileDownloadUtil.applicationContext = applicationContext;
        // 从ApplicationContext中获取FileStorageConfig
        FileDownloadUtil.fileStorageConfig = applicationContext.getBean(FileStorageConfig.class);
        log.info("FileStorageConfig注入成功 - 根目录: {}", fileStorageConfig.getRootDirectory());
        // 从ApplicationContext中获取GoHttpServerConfig
        try {
            FileDownloadUtil.goHttpServerConfig = applicationContext.getBean(GoHttpServerConfig.class);
            log.info("GoHttpServerConfig注入成功 - URL: {}", goHttpServerConfig != null ? goHttpServerConfig.getUrl() : "null");
        } catch (Exception e) {
            log.warn("GoHttpServerConfig注入失败: {}", e.getMessage());
        }
    }
    
    /**
     * 获取FileStorageConfig实例
     */
    private static FileStorageConfig getFileStorageConfig() {
        if (fileStorageConfig == null && applicationContext != null) {
            fileStorageConfig = applicationContext.getBean(FileStorageConfig.class);
            log.info("重新获取FileStorageConfig - 根目录: {}", fileStorageConfig.getRootDirectory());
        }
        return fileStorageConfig;
    }
    
    /**
     * 获取GoHttpServerConfig实例
     */
    private static GoHttpServerConfig getGoHttpServerConfig() {
        if (goHttpServerConfig == null && applicationContext != null) {
            try {
                goHttpServerConfig = applicationContext.getBean(GoHttpServerConfig.class);
                log.info("重新获取GoHttpServerConfig - URL: {}", goHttpServerConfig != null ? goHttpServerConfig.getUrl() : "null");
            } catch (Exception e) {
                log.warn("获取GoHttpServerConfig失败: {}", e.getMessage());
            }
        }
        return goHttpServerConfig;
    }
    
    /**
     * 下载文件到/opt目录下的taskId子目录
     * 
     * @param url 文件URL
     * @param taskId 任务ID
     * @return 下载的文件路径
     * @throws Exception 下载异常
     */
    public static Path downloadFile(String url, String taskId) throws Exception {
        log.info("开始下载文件 - URL: {}, 任务ID: {}", url, taskId);
        
        // 使用配置的gohttpserver URL替换URL中的IP地址
        String actualUrl = url;
        GoHttpServerConfig config = getGoHttpServerConfig();
        if (config != null && config.getUrl() != null && !config.getUrl().trim().isEmpty()) {
            actualUrl = UrlReplaceUtil.replaceUrlHost(url, config.getUrl());
            if (!actualUrl.equals(url)) {
                log.info("用例集下载URL已替换 - 原始URL: {}, 替换后URL: {}", url, actualUrl);
            }
        }
        
        // 获取配置的根目录
        String rootDir = getFileStorageConfig() != null ? getFileStorageConfig().getRootDirectory() : System.getProperty("java.io.tmpdir");
        Path rootDirectory = Paths.get(rootDir);
        Path taskDir = rootDirectory.resolve(taskId);
        
        // 确保根目录存在
        if (!Files.exists(rootDirectory)) {
            log.warn("根目录 {} 不存在，尝试创建", rootDir);
            try {
                Files.createDirectories(rootDirectory);
            } catch (Exception e) {
                log.error("创建根目录失败: {}", e.getMessage());
                throw new RuntimeException("无法创建根目录 " + rootDir + ": " + e.getMessage());
            }
        }
        
        // 创建taskId子目录
        if (!Files.exists(taskDir)) {
            Files.createDirectories(taskDir);
            log.info("创建任务目录: {}", taskDir);
        }
        
        // 从URL中提取文件名
        String fileName = actualUrl.substring(actualUrl.lastIndexOf("/") + 1);
        Path filePath = taskDir.resolve(fileName);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(actualUrl);
            
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("下载文件失败，HTTP状态码: " + response.getStatusLine().getStatusCode());
                }
                
                HttpEntity entity = response.getEntity();
                
                // 获取文件总大小
                long totalSize = entity.getContentLength();
                if (totalSize < 0) {
                    log.warn("无法获取文件大小，将不显示下载进度");
                } else {
                    log.info("文件大小: {} ({})", formatFileSize(totalSize), totalSize);
                }
                
                try (InputStream inputStream = entity.getContent();
                     FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                    
                    // 使用带进度监控的下载方法
                    downloadWithProgress(inputStream, outputStream, totalSize, fileName, taskId);
                }
            }
        }
        
        log.info("文件下载完成 - 路径: {}", filePath);
        return filePath;
    }
    
    /**
     * 带进度监控的下载方法
     * 
     * @param inputStream 输入流
     * @param outputStream 输出流
     * @param totalSize 文件总大小（字节），如果未知则为-1
     * @param fileName 文件名
     * @param taskId 任务ID
     * @throws IOException IO异常
     */
    private static void downloadWithProgress(InputStream inputStream, FileOutputStream outputStream, 
                                           long totalSize, String fileName, String taskId) throws IOException {
        byte[] buffer = new byte[8192]; // 8KB缓冲区
        long downloadedBytes = 0;
        long lastReportTime = System.currentTimeMillis();
        long lastReportBytes = 0;
        int reportInterval = 1000; // 每1秒报告一次进度
        
        log.info("开始下载文件 - 文件名: {}, 任务ID: {}", fileName, taskId);
        
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
            downloadedBytes += bytesRead;
            
            long currentTime = System.currentTimeMillis();
            long timeElapsed = currentTime - lastReportTime;
            
            // 每1秒或每下载10%时报告一次进度
            boolean shouldReport = false;
            if (totalSize > 0) {
                // 如果知道总大小，每10%报告一次，或每1秒报告一次
                double progress = (double) downloadedBytes / totalSize * 100;
                double lastProgress = (double) lastReportBytes / totalSize * 100;
                shouldReport = (timeElapsed >= reportInterval) || (progress - lastProgress >= 10.0);
            } else {
                // 如果不知道总大小，每1秒报告一次
                shouldReport = timeElapsed >= reportInterval;
            }
            
            if (shouldReport) {
                // 计算下载速度
                long bytesDownloadedInInterval = downloadedBytes - lastReportBytes;
                double speedBytesPerSecond = (double) bytesDownloadedInInterval / (timeElapsed / 1000.0);
                String speedStr = formatSpeed(speedBytesPerSecond);
                
                if (totalSize > 0) {
                    // 已知文件大小，显示百分比
                    double progress = (double) downloadedBytes / totalSize * 100;
                    String downloadedStr = formatFileSize(downloadedBytes);
                    String totalStr = formatFileSize(totalSize);
                    log.info("下载进度 - 任务ID: {}, 文件名: {}, 进度: {:.2f}% ({}/{}), 速度: {}", 
                            taskId, fileName, String.format("%.2f", progress), downloadedStr, totalStr, speedStr);
                } else {
                    // 未知文件大小，只显示已下载大小和速度
                    String downloadedStr = formatFileSize(downloadedBytes);
                    log.info("下载进度 - 任务ID: {}, 文件名: {}, 已下载: {}, 速度: {}", 
                            taskId, fileName, downloadedStr, speedStr);
                }
                
                lastReportTime = currentTime;
                lastReportBytes = downloadedBytes;
            }
        }
        
        outputStream.flush();
        
        // 下载完成，输出最终统计
        if (totalSize > 0) {
            double finalProgress = (double) downloadedBytes / totalSize * 100;
            String downloadedStr = formatFileSize(downloadedBytes);
            String totalStr = formatFileSize(totalSize);
            log.info("下载完成 - 任务ID: {}, 文件名: {}, 最终进度: {}% ({}/{})", 
                    taskId, fileName, String.format("%.2f", finalProgress), downloadedStr, totalStr);
        } else {
            String downloadedStr = formatFileSize(downloadedBytes);
            log.info("下载完成 - 任务ID: {}, 文件名: {}, 总大小: {}", 
                    taskId, fileName, downloadedStr);
        }
    }
    
    /**
     * 格式化文件大小
     * 
     * @param bytes 字节数
     * @return 格式化后的文件大小字符串
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 格式化下载速度
     * 
     * @param bytesPerSecond 每秒字节数
     * @return 格式化后的速度字符串
     */
    private static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) {
            return String.format("%.2f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.2f KB/s", bytesPerSecond / 1024.0);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB/s", bytesPerSecond / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 下载文件（兼容旧版本，使用临时目录）
     * 
     * @param url 文件URL
     * @return 下载的文件路径
     * @throws Exception 下载异常
     */
    public static Path downloadFile(String url) throws Exception {
        log.info("开始下载文件到临时目录 - URL: {}", url);
        
        // 创建临时目录
        Path tempDir = Files.createTempDirectory("download_");
        String fileName = url.substring(url.lastIndexOf("/") + 1);
        Path filePath = tempDir.resolve(fileName);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("下载文件失败，HTTP状态码: " + response.getStatusLine().getStatusCode());
                }
                
                HttpEntity entity = response.getEntity();
                try (InputStream inputStream = entity.getContent();
                     FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                    IOUtils.copy(inputStream, outputStream);
                }
            }
        }
        
        log.info("文件下载完成 - 路径: {}", filePath);
        return filePath;
    }
    
    /**
     * 解压ZIP文件到/opt目录下的taskId子目录
     * 
     * @param zipFilePath ZIP文件路径
     * @param taskId 任务ID
     * @return 解压目录路径
     * @throws Exception 解压异常
     */
    public static Path extractZipFile(Path zipFilePath, String taskId) throws Exception {
        log.info("开始解压ZIP文件到临时目录 - 路径: {}, 任务ID: {}", zipFilePath, taskId);
        
        // 获取配置的根目录
        String rootDir = getFileStorageConfig() != null ? getFileStorageConfig().getRootDirectory() : System.getProperty("java.io.tmpdir");
        Path rootDirectory = Paths.get(rootDir);
        Path taskDir = rootDirectory.resolve(taskId);
        
        // 确保根目录存在
        if (!Files.exists(rootDirectory)) {
            log.warn("根目录 {} 不存在，尝试创建", rootDir);
            try {
                Files.createDirectories(rootDirectory);
            } catch (Exception e) {
                log.error("创建根目录失败: {}", e.getMessage());
                throw new RuntimeException("无法创建根目录 " + rootDir + ": " + e.getMessage());
            }
        }
        
        // 创建taskId子目录
        if (!Files.exists(taskDir)) {
            Files.createDirectories(taskDir);
            log.info("创建任务目录: {}", taskDir);
        }
        
        // 创建解压目录
        Path extractPath = taskDir.resolve("extracted");
        if (Files.exists(extractPath)) {
            // 如果目录已存在，先删除
            FileUtils.deleteDirectory(extractPath.toFile());
        }
        Files.createDirectories(extractPath);
        
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path filePath = extractPath.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                        IOUtils.copy(zipInputStream, outputStream);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
        
        log.info("ZIP文件解压完成 - 路径: {}", extractPath);
        return extractPath;
    }
    
    /**
     * 解压ZIP文件（兼容旧版本，使用临时目录）
     * 
     * @param zipFilePath ZIP文件路径
     * @return 解压目录路径
     * @throws Exception 解压异常
     */
    public static Path extractZipFile(Path zipFilePath) throws Exception {
        log.info("开始解压ZIP文件到临时目录 - 路径: {}", zipFilePath);
        
        // 创建解压目录
        Path extractPath = Files.createTempDirectory("extract_");
        
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path filePath = extractPath.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                        IOUtils.copy(zipInputStream, outputStream);
                    }
                }
                zipInputStream.closeEntry();
            }
        }
        
        log.info("ZIP文件解压完成 - 路径: {}", extractPath);
        return extractPath;
    }
    
    /**
     * 清理文件或目录
     * 
     * @param filePath 文件路径
     */
    public static void cleanupFile(Path filePath) {
        try {
            if (Files.exists(filePath)) {
                if (Files.isDirectory(filePath)) {
                    FileUtils.deleteDirectory(filePath.toFile());
                } else {
                    Files.delete(filePath);
                }
                log.info("删除文件/目录: {}", filePath);
            }
        } catch (Exception e) {
            log.warn("清理文件/目录失败: {}", e.getMessage());
        }
    }
    
    /**
     * 清理任务目录
     * 
     * @param taskId 任务ID
     */
    public static void cleanupTaskDirectory(String taskId) {
        try {
            String rootDir = getFileStorageConfig() != null ? getFileStorageConfig().getRootDirectory() : System.getProperty("java.io.tmpdir");
            Path taskDir = Paths.get(rootDir).resolve(taskId);
            if (Files.exists(taskDir)) {
                FileUtils.deleteDirectory(taskDir.toFile());
                log.info("清理任务目录: {}", taskDir);
            }
        } catch (Exception e) {
            log.warn("清理任务目录失败 - 任务ID: {}, 错误: {}", taskId, e.getMessage());
        }
    }
}
