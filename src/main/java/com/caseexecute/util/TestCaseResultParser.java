package com.caseexecute.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用例执行结果解析工具类
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
public class TestCaseResultParser {
    
    /**
     * 解析测试用例执行结果
     * 
     * @param logContent 执行日志内容
     * @return 解析结果
     */
    public static TestCaseParseResult parseResult(String logContent) {
        TestCaseParseResult result = new TestCaseParseResult();
        
        // 解析测试统计信息
        parseTestStatistics(logContent, result);
        
        // 解析执行状态
        parseExecutionStatus(logContent, result);
        
        // 解析失败详情
        parseFailureDetails(logContent, result);
        
        // 解析性能指标
        parsePerformanceMetrics(logContent, result);
        
        return result;
    }
    
    /**
     * 解析测试统计信息
     */
    private static void parseTestStatistics(String logContent, TestCaseParseResult result) {
        // 匹配测试结果统计
        Pattern statsPattern = Pattern.compile("运行测试数:\\s*(\\d+).*?失败数:\\s*(\\d+).*?错误数:\\s*(\\d+)", Pattern.DOTALL);
        Matcher statsMatcher = statsPattern.matcher(logContent);
        
        if (statsMatcher.find()) {
            result.setTotalTests(Integer.parseInt(statsMatcher.group(1)));
            result.setFailedTests(Integer.parseInt(statsMatcher.group(2)));
            result.setErrorTests(Integer.parseInt(statsMatcher.group(3)));
            result.setSuccessTests(result.getTotalTests() - result.getFailedTests() - result.getErrorTests());
        }
        
        // 计算成功率
        if (result.getTotalTests() > 0) {
            result.setSuccessRate((double) result.getSuccessTests() / result.getTotalTests() * 100);
        }
    }
    
    /**
     * 解析执行状态 - 只返回3种状态：SUCCESS、FAILED、BLOCKED
     */
    private static void parseExecutionStatus(String logContent, TestCaseParseResult result) {
        String status = "BLOCKED";
        String resultMessage = "用例执行被阻塞";
        
        // 检查成功标识 - 使用 "case success" 作为成功条件
        if (logContent.contains("case success")) {
            if (result.getFailedTests() == 0 && result.getErrorTests() == 0) {
                status = "SUCCESS";
                resultMessage = "用例执行成功";
            } else {
                // 部分成功也归类为FAILED
                status = "FAILED";
                resultMessage = "用例部分成功，但存在失败项";
            }
        }
        
        // 检查失败标识 - 使用 "case failed" 作为失败条件
        if (logContent.contains("case failed")) {
            status = "FAILED";
            resultMessage = "用例执行失败";
        }
        
        // 检查超时标识 - 超时归类为FAILED
        if (logContent.contains("超时") || logContent.contains("timeout") || logContent.contains("TIMEOUT")) {
            status = "FAILED";
            resultMessage = "用例执行超时";
        }
        
        // 检查是否有Python环境问题或其他阻塞因素
        if (logContent.contains("ImportError") || logContent.contains("ModuleNotFoundError") || 
            logContent.contains("Permission denied") || logContent.contains("No such file") ||
            logContent.contains("Connection refused") || logContent.contains("Network is unreachable")) {
            status = "BLOCKED";
            resultMessage = "用例执行被阻塞（环境或网络问题）";
        }
        
        result.setStatus(status);
        result.setResultMessage(resultMessage);
    }
    
    /**
     * 解析失败详情
     */
    private static void parseFailureDetails(String logContent, TestCaseParseResult result) {
        StringBuilder failureDetails = new StringBuilder();
        
        // 提取失败的测试用例信息
        Pattern failurePattern = Pattern.compile("失败的测试:.*?(- .*?): (.*?)(?=\\n|$)", Pattern.DOTALL);
        Matcher failureMatcher = failurePattern.matcher(logContent);
        
        while (failureMatcher.find()) {
            String testName = failureMatcher.group(1).trim();
            String errorMessage = failureMatcher.group(2).trim();
            failureDetails.append(testName).append(": ").append(errorMessage).append("; ");
        }
        
        // 提取错误信息
        Pattern errorPattern = Pattern.compile("错误的测试:.*?(- .*?): (.*?)(?=\\n|$)", Pattern.DOTALL);
        Matcher errorMatcher = errorPattern.matcher(logContent);
        
        while (errorMatcher.find()) {
            String testName = errorMatcher.group(1).trim();
            String errorMessage = errorMatcher.group(2).trim();
            failureDetails.append(testName).append(": ").append(errorMessage).append("; ");
        }
        
        // 如果没有找到具体的失败信息，尝试从日志中提取错误关键词
        if (failureDetails.length() == 0) {
            String[] errorKeywords = {"AssertionError", "Exception", "Error", "失败", "错误"};
            for (String keyword : errorKeywords) {
                if (logContent.contains(keyword)) {
                    failureDetails.append("发现错误关键词: ").append(keyword).append("; ");
                }
            }
        }
        
        result.setFailureDetails(failureDetails.length() > 0 ? failureDetails.toString() : null);
    }
    
    /**
     * 解析性能指标
     */
    private static void parsePerformanceMetrics(String logContent, TestCaseParseResult result) {
        // 解析网络延迟
        Pattern latencyPattern = Pattern.compile("网络延迟:\\s*([\\d.]+)ms");
        Matcher latencyMatcher = latencyPattern.matcher(logContent);
        if (latencyMatcher.find()) {
            result.setNetworkLatency(Double.parseDouble(latencyMatcher.group(1)));
        }
        
        // 解析带宽信息
        Pattern bandwidthPattern = Pattern.compile("带宽:\\s*([\\d.]+)\\s*(Mbps|Kbps)");
        Matcher bandwidthMatcher = bandwidthPattern.matcher(logContent);
        if (bandwidthMatcher.find()) {
            result.setBandwidth(Double.parseDouble(bandwidthMatcher.group(1)));
            result.setBandwidthUnit(bandwidthMatcher.group(2));
        }
        
        // 解析信号强度
        Pattern signalPattern = Pattern.compile("信号强度:\\s*([\\d.]+)\\s*dBm");
        Matcher signalMatcher = signalPattern.matcher(logContent);
        if (signalMatcher.find()) {
            result.setSignalStrength(Double.parseDouble(signalMatcher.group(1)));
        }
    }
    
    /**
     * 用例解析结果
     */
    public static class TestCaseParseResult {
        private String status;
        private String resultMessage;
        private int totalTests;
        private int successTests;
        private int failedTests;
        private int errorTests;
        private double successRate;
        private String failureDetails;
        private Double networkLatency;
        private Double bandwidth;
        private String bandwidthUnit;
        private Double signalStrength;
        
        // Getters and Setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getResultMessage() { return resultMessage; }
        public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
        
        public int getTotalTests() { return totalTests; }
        public void setTotalTests(int totalTests) { this.totalTests = totalTests; }
        
        public int getSuccessTests() { return successTests; }
        public void setSuccessTests(int successTests) { this.successTests = successTests; }
        
        public int getFailedTests() { return failedTests; }
        public void setFailedTests(int failedTests) { this.failedTests = failedTests; }
        
        public int getErrorTests() { return errorTests; }
        public void setErrorTests(int errorTests) { this.errorTests = errorTests; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public String getFailureDetails() { return failureDetails; }
        public void setFailureDetails(String failureDetails) { this.failureDetails = failureDetails; }
        
        public Double getNetworkLatency() { return networkLatency; }
        public void setNetworkLatency(Double networkLatency) { this.networkLatency = networkLatency; }
        
        public Double getBandwidth() { return bandwidth; }
        public void setBandwidth(Double bandwidth) { this.bandwidth = bandwidth; }
        
        public String getBandwidthUnit() { return bandwidthUnit; }
        public void setBandwidthUnit(String bandwidthUnit) { this.bandwidthUnit = bandwidthUnit; }
        
        public Double getSignalStrength() { return signalStrength; }
        public void setSignalStrength(Double signalStrength) { this.signalStrength = signalStrength; }
    }
}
