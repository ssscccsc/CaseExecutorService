package com.caseexecute.util;

import lombok.extern.slf4j.Slf4j;

import java.net.URL;

/**
 * URL替换工具类
 * 用于替换URL中的IP地址
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
public class UrlReplaceUtil {
    
    /**
     * 使用配置的gohttpserver URL替换URL中的IP地址
     * 
     * @param originalUrl 原始URL
     * @param configuredUrl 配置的gohttpserver URL（从application.yml读取）
     * @return 替换后的URL
     */
    public static String replaceUrlHost(String originalUrl, String configuredUrl) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return originalUrl;
        }
        
        // 如果不是HTTP/HTTPS URL，直接返回
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            return originalUrl;
        }
        
        // 如果配置的URL为空，直接返回原始URL
        if (configuredUrl == null || configuredUrl.trim().isEmpty()) {
            log.warn("配置的gohttpserver URL为空，使用原始URL: {}", originalUrl);
            return originalUrl;
        }
        
        try {
            // 解析原始URL
            URL originalUrlObj = new URL(originalUrl);
            
            // 解析配置的URL
            URL configuredUrlObj = new URL(configuredUrl.trim());
            
            // 如果协议、主机和端口都相同，不需要替换
            if (originalUrlObj.getProtocol().equals(configuredUrlObj.getProtocol()) &&
                originalUrlObj.getHost().equals(configuredUrlObj.getHost()) &&
                getPort(originalUrlObj) == getPort(configuredUrlObj)) {
                log.debug("URL主机和端口相同，无需替换: {}", originalUrl);
                return originalUrl;
            }
            
            // 构建新URL：使用配置的协议、主机和端口，保留原始URL的路径和查询参数
            String newProtocol = configuredUrlObj.getProtocol();
            String newHost = configuredUrlObj.getHost();
            int newPort = getPort(configuredUrlObj);
            
            StringBuilder newUrl = new StringBuilder();
            newUrl.append(newProtocol).append("://").append(newHost);
            if (newPort != -1 && newPort != getDefaultPort(newProtocol)) {
                newUrl.append(":").append(newPort);
            }
            newUrl.append(originalUrlObj.getPath());
            if (originalUrlObj.getQuery() != null) {
                newUrl.append("?").append(originalUrlObj.getQuery());
            }
            if (originalUrlObj.getRef() != null) {
                newUrl.append("#").append(originalUrlObj.getRef());
            }
            
            String replacedUrl = newUrl.toString();
            log.info("URL IP替换成功 - 原始URL: {}, 替换后URL: {}", originalUrl, replacedUrl);
            return replacedUrl;
            
        } catch (Exception e) {
            log.error("URL替换失败，使用原始URL: {}, 错误: {}", originalUrl, e.getMessage());
            return originalUrl;
        }
    }
    
    /**
     * 获取URL的端口号
     * 
     * @param url URL对象
     * @return 端口号，如果使用默认端口则返回-1
     */
    private static int getPort(URL url) {
        int port = url.getPort();
        if (port == -1) {
            // 如果URL中没有显式指定端口，返回默认端口
            return getDefaultPort(url.getProtocol());
        }
        return port;
    }
    
    /**
     * 获取协议的默认端口
     * 
     * @param protocol 协议名称
     * @return 默认端口号
     */
    private static int getDefaultPort(String protocol) {
        if ("http".equals(protocol)) {
            return 80;
        } else if ("https".equals(protocol)) {
            return 443;
        }
        return -1;
    }
}


