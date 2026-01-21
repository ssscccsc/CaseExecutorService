package com.caseexecute.util;

import com.caseexecute.config.GoHttpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UE状态工具类
 * 用于通知后台服务更新UE使用状态
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Component
public class UeStatusUtil implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    private static GoHttpServerConfig goHttpServerConfig;
    
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        UeStatusUtil.applicationContext = applicationContext;
        // 从ApplicationContext中获取GoHttpServerConfig
        try {
            UeStatusUtil.goHttpServerConfig = applicationContext.getBean(GoHttpServerConfig.class);
            log.info("GoHttpServerConfig注入成功 - URL: {}", goHttpServerConfig != null ? goHttpServerConfig.getUrl() : "null");
        } catch (Exception e) {
            log.warn("GoHttpServerConfig注入失败: {}", e.getMessage());
        }
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
     * 标记UE为使用中
     * 
     * @param ueIds UE ID列表
     * @param resultReportUrl 结果上报URL（已废弃，不再使用）
     * @return 是否成功
     */
    public static boolean markUesInUse(List<Long> ueIds, String resultReportUrl) {
        if (ueIds == null || ueIds.isEmpty()) {
            return true;
        }
        
        try {
            // 从application.yml配置中获取后台服务地址
            String baseUrl = getBaseUrlFromConfig();
            if (baseUrl == null) {
                log.warn("无法从配置中获取后台服务地址，跳过UE状态更新");
                return false;
            }
            
            // 构建API URL，确保路径正确
            String apiUrl = buildApiUrl(baseUrl, "/ue-status/mark-in-use");
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ueIds", ueIds);
            
            String jsonBody = JSON.toJSONString(requestBody);
            
            // 发送HTTP请求
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(apiUrl);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
                
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    if (statusCode == 200) {
                        log.info("UE已标记为使用中 - UE IDs: {}", ueIds);
                        return true;
                    } else {
                        log.error("标记UE为使用中失败 - HTTP状态码: {}, 响应: {}", statusCode, responseBody);
                        return false;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("标记UE为使用中失败 - UE IDs: {}, 错误: {}", ueIds, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 标记UE为可用（未使用）
     * 
     * @param ueIds UE ID列表
     * @param resultReportUrl 结果上报URL（已废弃，不再使用）
     * @return 是否成功
     */
    public static boolean markUesAvailable(List<Long> ueIds, String resultReportUrl) {
        if (ueIds == null || ueIds.isEmpty()) {
            return true;
        }
        
        try {
            // 从application.yml配置中获取后台服务地址
            String baseUrl = getBaseUrlFromConfig();
            if (baseUrl == null) {
                log.warn("无法从配置中获取后台服务地址，跳过UE状态更新");
                return false;
            }
            
            // 构建API URL，确保路径正确
            String apiUrl = buildApiUrl(baseUrl, "/ue-status/mark-available");
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ueIds", ueIds);
            
            String jsonBody = JSON.toJSONString(requestBody);
            
            // 发送HTTP请求
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(apiUrl);
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
                
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    if (statusCode == 200) {
                        log.info("UE已标记为可用 - UE IDs: {}", ueIds);
                        return true;
                    } else {
                        log.error("标记UE为可用失败 - HTTP状态码: {}, 响应: {}", statusCode, responseBody);
                        return false;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("标记UE为可用失败 - UE IDs: {}, 错误: {}", ueIds, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 从application.yml配置中获取后台服务基础地址
     * 
     * @return 后台服务基础地址，如果获取失败则返回null
     */
    private static String getBaseUrlFromConfig() {
        try {
            GoHttpServerConfig config = getGoHttpServerConfig();
            if (config == null) {
                log.warn("无法获取GoHttpServerConfig配置");
                return null;
            }
            
            String hostIp = config.getHostIp();
            if (hostIp == null || hostIp.trim().isEmpty()) {
                log.warn("gohttpserver.host-ip配置为空");
                return null;
            }
            
            // 确保hostIp以http://或https://开头
            String baseUrl = hostIp.trim();
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                baseUrl = "http://" + baseUrl;
            }
            
            log.debug("从配置中获取后台服务地址: {}", baseUrl);
            return baseUrl;
            
        } catch (Exception e) {
            log.error("从配置中获取后台服务地址失败，错误: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 构建API URL，确保路径正确
     * 
     * @param baseUrl 基础URL
     * @param apiPath API路径（不包含/api前缀）
     * @return 完整的API URL
     */
    private static String buildApiUrl(String baseUrl, String apiPath) {
        // 移除baseUrl末尾的斜杠
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        // 确保apiPath以斜杠开头
        String normalizedApiPath = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        
        // 检查baseUrl是否已经包含/api路径
        if (normalizedBaseUrl.endsWith("/api")) {
            // 如果baseUrl已经包含/api，直接拼接apiPath
            return normalizedBaseUrl + normalizedApiPath;
        } else {
            // 如果baseUrl不包含/api，添加/api前缀
            return normalizedBaseUrl + "/api" + normalizedApiPath;
        }
    }
}

