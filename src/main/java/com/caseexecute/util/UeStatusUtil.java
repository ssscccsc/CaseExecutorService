package com.caseexecute.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

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
public class UeStatusUtil {
    
    /**
     * 标记UE为使用中
     * 
     * @param ueIds UE ID列表
     * @param resultReportUrl 结果上报URL（用于获取后台服务地址）
     * @return 是否成功
     */
    public static boolean markUesInUse(List<Long> ueIds, String resultReportUrl) {
        if (ueIds == null || ueIds.isEmpty()) {
            return true;
        }
        
        try {
            // 从resultReportUrl中提取后台服务地址
            String baseUrl = extractBaseUrl(resultReportUrl);
            if (baseUrl == null) {
                log.warn("无法从resultReportUrl中提取后台服务地址，跳过UE状态更新 - resultReportUrl: {}", resultReportUrl);
                return false;
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
     * @param ueIds UE ID列表
     * @param resultReportUrl 结果上报URL（用于获取后台服务地址）
     * @return 是否成功
     */
    public static boolean markUesAvailable(List<Long> ueIds, String resultReportUrl) {
        if (ueIds == null || ueIds.isEmpty()) {
            return true;
        }
        
        try {
            // 从resultReportUrl中提取后台服务地址
            String baseUrl = extractBaseUrl(resultReportUrl);
            if (baseUrl == null) {
                log.warn("无法从resultReportUrl中提取后台服务地址，跳过UE状态更新 - resultReportUrl: {}", resultReportUrl);
                return false;
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

