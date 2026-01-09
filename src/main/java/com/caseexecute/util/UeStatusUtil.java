package com.caseexecute.util;

import com.caseexecute.config.GoHttpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        UeStatusUtil.applicationContext = applicationContext;
        try {
            UeStatusUtil.goHttpServerConfig = applicationContext.getBean(GoHttpServerConfig.class);
            log.info("GoHttpServerConfig injected successfully - Host IP: {}", 
                    goHttpServerConfig != null ? goHttpServerConfig.getHostIp() : "null");
        } catch (Exception e) {
            log.warn("GoHttpServerConfig injection failed: {}", e.getMessage());
        }
    }
    
    /**
     * 获取GoHttpServerConfig实例
     */
    private static GoHttpServerConfig getGoHttpServerConfig() {
        if (goHttpServerConfig == null && applicationContext != null) {
            try {
                goHttpServerConfig = applicationContext.getBean(GoHttpServerConfig.class);
                log.info("Re-obtained GoHttpServerConfig - Host IP: {}", 
                        goHttpServerConfig != null ? goHttpServerConfig.getHostIp() : "null");
            } catch (Exception e) {
                log.warn("Failed to obtain GoHttpServerConfig: {}", e.getMessage());
            }
        }
        return goHttpServerConfig;
    }
    
    /**
     * 标记UE为使用中
     * 
     * @param ueIds UE ID列表（Integer类型）
     * @param resultReportUrl 结果上报URL（保留参数以兼容旧代码，但不使用）
     * @return 是否成功
     */
    public static boolean markUesInUse(List<Integer> ueIds, String resultReportUrl) {
        if (ueIds == null || ueIds.isEmpty()) {
            return true;
        }
        
        try {
            // 从application.yml中的gohttpserver.host-ip获取后台服务地址
            GoHttpServerConfig config = getGoHttpServerConfig();
            String baseUrl = null;
            if (config != null && config.getHostIp() != null && !config.getHostIp().trim().isEmpty()) {
                baseUrl = config.getHostIp();
                log.info("Using configured host IP for UE status update - Host IP: {}", baseUrl);
            } else {
                // 如果配置不存在，回退到从resultReportUrl中提取
                baseUrl = extractBaseUrl(resultReportUrl);
                if (baseUrl == null) {
                    log.warn("无法获取后台服务地址，跳过UE状态更新 - resultReportUrl: {}", resultReportUrl);
                    return false;
                }
                log.warn("GoHttpServerConfig not available, falling back to extract from resultReportUrl - Base URL: {}", baseUrl);
            }
            
            String apiUrl = baseUrl + "/ue-status/mark-in-use";
            
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
     * @param ueIds UE ID列表（Integer类型）
     * @param resultReportUrl 结果上报URL（保留参数以兼容旧代码，但不使用）
     * @return 是否成功
     */
    public static boolean markUesAvailable(List<Integer> ueIds, String resultReportUrl) {
        if (ueIds == null || ueIds.isEmpty()) {
            return true;
        }
        
        try {
            // 从application.yml中的gohttpserver.host-ip获取后台服务地址
            GoHttpServerConfig config = getGoHttpServerConfig();
            String baseUrl = null;
            if (config != null && config.getHostIp() != null && !config.getHostIp().trim().isEmpty()) {
                baseUrl = config.getHostIp();
                log.info("Using configured host IP for UE status update - Host IP: {}", baseUrl);
            } else {
                // 如果配置不存在，回退到从resultReportUrl中提取
                baseUrl = extractBaseUrl(resultReportUrl);
                if (baseUrl == null) {
                    log.warn("无法获取后台服务地址，跳过UE状态更新 - resultReportUrl: {}", resultReportUrl);
                    return false;
                }
                log.warn("GoHttpServerConfig not available, falling back to extract from resultReportUrl - Base URL: {}", baseUrl);
            }
            
            String apiUrl = baseUrl + "/ue-status/mark-available";
            
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
     * 从结果上报URL中提取后台服务基础地址
     * 
     * @param resultReportUrl 结果上报URL
     * @return 后台服务基础地址，如果提取失败则返回null
     */
    private static String extractBaseUrl(String resultReportUrl) {
        if (resultReportUrl == null || resultReportUrl.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 例如：http://192.168.1.100:8080/api/test-result/report
            // 提取：http://192.168.1.100:8080
            int apiIndex = resultReportUrl.indexOf("/api/");
            if (apiIndex > 0) {
                return resultReportUrl.substring(0, apiIndex);
            }
            
            // 如果没有/api/，尝试提取协议和主机部分
            java.net.URL url = new java.net.URL(resultReportUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            
            if (port != -1) {
                return protocol + "://" + host + ":" + port;
            } else {
                return protocol + "://" + host;
            }
            
        } catch (Exception e) {
            log.error("提取后台服务地址失败 - resultReportUrl: {}, 错误: {}", resultReportUrl, e.getMessage());
            return null;
        }
    }
}

