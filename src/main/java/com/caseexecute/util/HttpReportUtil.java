package com.caseexecute.util;

import com.caseexecute.config.GoHttpServerConfig;
import com.caseexecute.dto.TestCaseResultReport;
import com.caseexecute.dto.TestCaseLogRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * HTTP上报工具类
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Component
public class HttpReportUtil {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private GoHttpServerConfig goHttpServerConfig;
    
    /**
     * 上报用例执行结果
     * 
     * @param reportUrl 上报URL
     * @param report 执行结果
     * @return 是否上报成功
     */
    public boolean reportTestCaseResult(String reportUrl, TestCaseResultReport report) {
        try {
            log.info("开始上报用例执行结果 - 用例ID: {}, 轮次: {}, 状态: {}", 
                    report.getTestCaseId(), report.getRound(), report.getStatus());
            log.info("原始上报URL: {}", reportUrl);
            
            // 使用配置的主机IP替换上报URL中的IP地址
            String actualReportUrl = reportUrl;
            if (goHttpServerConfig != null && goHttpServerConfig.getHostIp() != null && !goHttpServerConfig.getHostIp().trim().isEmpty()) {
                actualReportUrl = UrlReplaceUtil.replaceUrlHost(reportUrl, goHttpServerConfig.getHostIp());
                if (!actualReportUrl.equals(reportUrl)) {
                    log.info("结果上报URL已替换 - 原始URL: {}, 替换后URL: {}", reportUrl, actualReportUrl);
                }
            }
            
            log.info("实际上报URL: {}", actualReportUrl);
            
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(actualReportUrl);
                httpPost.setHeader("Content-Type", "application/json");
                
                String jsonBody = objectMapper.writeValueAsString(report);
                httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
                
                // 记录请求体内容
                log.info("上报请求体内容 - 用例ID: {}, 轮次: {}", report.getTestCaseId(), report.getRound());
                log.info("请求体JSON: {}", jsonBody);
                
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = org.apache.http.util.EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    log.info("上报响应 - 用例ID: {}, 轮次: {}, HTTP状态码: {}, 响应体: {}", 
                            report.getTestCaseId(), report.getRound(), statusCode, responseBody);
                    
                    if (statusCode == 200) {
                        log.info("用例执行结果上报成功 - 用例ID: {}, 轮次: {}", 
                                report.getTestCaseId(), report.getRound());
                        return true;
                    } else {
                        log.error("用例执行结果上报失败 - 用例ID: {}, 轮次: {}, HTTP状态码: {}, 响应体: {}", 
                                report.getTestCaseId(), report.getRound(), statusCode, responseBody);
                        return false;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("上报用例执行结果失败 - 用例ID: {}, 轮次: {}, 错误: {}", 
                    report.getTestCaseId(), report.getRound(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 上报用例执行日志
     * 
     * @param logReportUrl 日志上报URL
     * @param logContent 日志内容
     * @param logFileName 日志文件名
     * @param testCaseId 用例ID
     * @param round 轮次
     * @return 是否上报成功
     */
    public boolean reportTestCaseLog(String logReportUrl, String logContent, String logFileName, 
                                          Long testCaseId, Integer round) {
        
        // 构建任务ID
        String taskId = "TASK_" + System.currentTimeMillis() + "_" + testCaseId + "_" + round;
        return reportTestCaseLog(logReportUrl, logContent, logFileName, taskId, testCaseId, round);
    }
    
    /**
     * 上报用例执行日志文件
     * 
     * @param logReportUrl 日志上报URL
     * @param logContent 日志内容
     * @param logFileName 日志文件名
     * @param taskId 任务ID
     * @param testCaseId 用例ID
     * @param round 轮次
     * @return 是否上报成功
     */
    public boolean reportTestCaseLog(String logReportUrl, String logContent, String logFileName, 
                                          String taskId, Long testCaseId, Integer round) {
        try {
            log.info("开始上报用例执行日志文件 - 用例ID: {}, 轮次: {}", testCaseId, round);
            
            // 构建日志上报URL - 使用文件上传接口
            String fullLogReportUrl = logReportUrl;
            if (fullLogReportUrl.endsWith("/")) {
                fullLogReportUrl = fullLogReportUrl.substring(0, fullLogReportUrl.length() - 1);
            }
            fullLogReportUrl += "/upload";
            
            log.info("日志上报URL: {}", fullLogReportUrl);
            log.info("日志文件名: {}", logFileName);
            log.info("日志内容长度: {} 字符", logContent != null ? logContent.length() : 0);
            
            // 记录日志内容的前500个字符（避免日志过长）
            if (logContent != null && logContent.length() > 0) {
                String logPreview = logContent.length() > 500 ? 
                    logContent.substring(0, 500) + "..." : logContent;
                log.info("日志内容预览 - 用例ID: {}, 轮次: {}", testCaseId, round);
                log.info("日志预览: {}", logPreview);
            }
            
            // 创建临时日志文件
            java.nio.file.Path tempLogFile = java.nio.file.Files.createTempFile("testcase_log_", ".log");
            java.nio.file.Files.write(tempLogFile, logContent.getBytes(StandardCharsets.UTF_8));
            
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                // 构建multipart请求
                org.apache.http.entity.mime.MultipartEntityBuilder builder = org.apache.http.entity.mime.MultipartEntityBuilder.create();
                builder.setMode(org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE);
                
                // 添加参数
                builder.addTextBody("taskId", taskId);
                builder.addTextBody("testCaseId", testCaseId.toString());
                builder.addTextBody("round", round.toString());
                
                // 添加文件
                builder.addBinaryBody("logFile", 
                    java.nio.file.Files.readAllBytes(tempLogFile),
                    org.apache.http.entity.ContentType.create("text/plain"),
                    logFileName);
                
                org.apache.http.HttpEntity multipart = builder.build();
                
                HttpPost httpPost = new HttpPost(fullLogReportUrl);
                httpPost.setEntity(multipart);
                
                log.info("日志文件上传请求 - 用例ID: {}, 轮次: {}, 文件大小: {} bytes", 
                        testCaseId, round, java.nio.file.Files.size(tempLogFile));
                
                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = org.apache.http.util.EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    log.info("日志文件上传响应 - 用例ID: {}, 轮次: {}, HTTP状态码: {}, 响应体: {}", 
                            testCaseId, round, statusCode, responseBody);
                    
                    if (statusCode == 200) {
                        log.info("用例执行日志文件上传成功 - 用例ID: {}, 轮次: {}", testCaseId, round);
                        return true;
                    } else {
                        log.error("用例执行日志文件上传失败 - 用例ID: {}, 轮次: {}, HTTP状态码: {}, 响应体: {}", 
                                testCaseId, round, statusCode, responseBody);
                        return false;
                    }
                }
            } finally {
                // 清理临时文件
                java.nio.file.Files.deleteIfExists(tempLogFile);
            }
            
        } catch (Exception e) {
            log.error("上报用例执行日志文件失败 - 用例ID: {}, 轮次: {}, 错误: {}", 
                    testCaseId, round, e.getMessage(), e);
            return false;
        }
    }
}
