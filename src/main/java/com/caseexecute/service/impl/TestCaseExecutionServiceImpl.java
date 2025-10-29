package com.caseexecute.service.impl;

import com.caseexecute.config.CaseExecutionConfig;
import com.caseexecute.config.FileStorageConfig;
import com.caseexecute.dto.TestCaseExecutionRequest;
import com.caseexecute.dto.TestCaseResultReport;
import com.caseexecute.service.TestCaseExecutionService;
import com.caseexecute.util.FileDownloadUtil;
import com.caseexecute.util.HttpReportUtil;
import com.caseexecute.util.PythonExecutorUtil;
import com.caseexecute.util.TestCaseResultParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 用例执行服务实现类
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Service
public class TestCaseExecutionServiceImpl implements TestCaseExecutionService {

    @Autowired
    private CaseExecutionConfig caseExecutionConfig;
    
    @Autowired
    private FileStorageConfig fileStorageConfig;
    
    @Autowired
    private HttpReportUtil httpReportUtil;
    
    // 任务管理：存储正在执行的任务和进程信息
    private final Map<String, TaskExecutionInfo> runningTasks = new ConcurrentHashMap<>();
    
    /**
     * 任务执行信息
     */
    private static class TaskExecutionInfo {
        private final String taskId;
        private final List<Process> processes;
        private CompletableFuture<Void> executionFuture;
        private final LocalDateTime startTime;
        
        public TaskExecutionInfo(String taskId, CompletableFuture<Void> executionFuture) {
            this.taskId = taskId;
            this.processes = new ArrayList<>();
            this.executionFuture = executionFuture;
            this.startTime = LocalDateTime.now();
        }
        
        public void setExecutionFuture(CompletableFuture<Void> executionFuture) {
            this.executionFuture = executionFuture;
        }
        
        public void addProcess(Process process) {
            processes.add(process);
        }
        
        public void removeProcess(Process process) {
            processes.remove(process);
        }
        
        public void cancelAllProcesses() {
            for (Process process : processes) {
                if (process != null && process.isAlive()) {
                    try {
                        // 直接调用进程的destroy方法
                        process.destroy();
                        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                        log.info("已终止进程 - 任务ID: {}", taskId);
                    } catch (Exception e) {
                        log.error("终止进程失败 - 任务ID: {}, 错误: {}", taskId, e.getMessage());
                    }
                }
            }
            processes.clear();
        }
        
        public void cancelExecution() {
            if (executionFuture != null && !executionFuture.isDone()) {
                executionFuture.cancel(true);
                log.info("已取消任务执行 - 任务ID: {}", taskId);
            }
        }
        
        // Getters
        public String getTaskId() { return taskId; }
        public List<Process> getProcesses() { return processes; }
        public CompletableFuture<Void> getExecutionFuture() { return executionFuture; }
        public LocalDateTime getStartTime() { return startTime; }
    }

    @Override
    public void processTestCaseExecution(TestCaseExecutionRequest request) {
        log.info("开始处理用例执行任务 - 任务ID: {}", request.getTaskId());
        
        // 记录UE信息和采集策略信息
        logTaskContextInfo(request);
        
        // 先创建任务执行信息并存储，确保在异步执行开始前就可用
        TaskExecutionInfo taskInfo = new TaskExecutionInfo(request.getTaskId(), null);
        runningTasks.put(request.getTaskId(), taskInfo);
        log.info("任务已添加到运行列表 - 任务ID: {}", request.getTaskId());
        
        // 异步执行，避免阻塞接口响应
        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            Path zipFilePath = null;
            Path extractPath = null;
            
            try {
                log.info("开始下载用例集文件 - 任务ID: {}, URL: {}", request.getTaskId(), request.getTestCaseSetPath());
                
                // 1. 下载用例集文件到/opt目录下的taskId子目录
                zipFilePath = FileDownloadUtil.downloadFile(request.getTestCaseSetPath(), request.getTaskId());
                log.info("用例集文件下载完成 - 任务ID: {}, 文件路径: {}", request.getTaskId(), zipFilePath);
                
                // 2. 解压用例集到/opt目录下的taskId子目录
                log.info("开始解压用例集文件 - 任务ID: {}, 文件路径: {}", request.getTaskId(), zipFilePath);
                extractPath = FileDownloadUtil.extractZipFile(zipFilePath, request.getTaskId());
                log.info("用例集文件解压完成 - 任务ID: {}, 解压路径: {}", request.getTaskId(), extractPath);
                
                // 3. 执行用例列表
                log.info("开始执行用例列表 - 任务ID: {}, 用例数量: {}", request.getTaskId(), request.getTestCaseList().size());
                executeTestCaseList(request, extractPath);
                
                log.info("用例执行任务处理完成 - 任务ID: {}", request.getTaskId());
                
            } catch (Exception e) {
                log.error("用例执行任务处理失败 - 任务ID: {}, 错误: {}", request.getTaskId(), e.getMessage(), e);
            } finally {
                // 4. 清理任务目录
                log.info("开始清理任务目录 - 任务ID: {}", request.getTaskId());
                try {
                    FileDownloadUtil.cleanupTaskDirectory(request.getTaskId());
                    log.info("任务目录已清理 - 任务ID: {}", request.getTaskId());
                } catch (Exception e) {
                    log.warn("清理任务目录失败 - 任务ID: {}, 错误: {}", request.getTaskId(), e.getMessage());
                }
                log.info("任务目录清理完成 - 任务ID: {}", request.getTaskId());
                
                // 5. 从运行任务列表中移除
                runningTasks.remove(request.getTaskId());
                log.info("任务已从运行列表中移除 - 任务ID: {}", request.getTaskId());
            }
        });
        
        // 更新任务执行信息中的Future
        taskInfo.setExecutionFuture(executionFuture);
        log.info("任务执行Future已设置 - 任务ID: {}", request.getTaskId());
    }
    
    /**
     * 执行用例列表
     */
    private void executeTestCaseList(TestCaseExecutionRequest request, Path extractPath) {
        log.info("开始执行用例列表 - 用例数量: {}", request.getTestCaseList().size());
        
        int successCount = 0;
        int failedCount = 0;
        int cancelledCount = 0;
        
        for (TestCaseExecutionRequest.TestCaseInfo testCase : request.getTestCaseList()) {
            // 检查任务是否已被取消
            TaskExecutionInfo taskInfo = runningTasks.get(request.getTaskId());
            if (taskInfo == null) {
                log.warn("任务已被取消，停止执行剩余用例 - 任务ID: {}", request.getTaskId());
                cancelledCount++;
                // 上报被取消的用例状态
                try {
                    reportTestCaseResult(request, testCase, "BLOCKED", "用例执行被取消", 0L, null, null, "任务被用户取消", null);
                } catch (Exception reportException) {
                    log.error("上报用例取消状态失败 - 用例ID: {}, 错误: {}", testCase.getTestCaseId(), reportException.getMessage());
                }
                continue;
            }
            
            // 检查执行Future是否已被取消
            if (taskInfo.getExecutionFuture() != null && taskInfo.getExecutionFuture().isCancelled()) {
                log.warn("任务执行已被取消，停止执行剩余用例 - 任务ID: {}", request.getTaskId());
                cancelledCount++;
                // 上报被取消的用例状态
                try {
                    reportTestCaseResult(request, testCase, "BLOCKED", "用例执行被取消", 0L, null, null, "任务被用户取消", null);
                } catch (Exception reportException) {
                    log.error("上报用例取消状态失败 - 用例ID: {}, 错误: {}", testCase.getTestCaseId(), reportException.getMessage());
                }
                continue;
            }
            
            try {
                log.info("开始执行用例 - 用例ID: {}, 用例编号: {}, 轮次: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
                
                executeSingleTestCase(request, testCase, extractPath);
                successCount++;
                
                log.info("用例执行完成 - 用例ID: {}, 用例编号: {}, 轮次: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
                
            } catch (Exception e) {
                failedCount++;
                log.error("执行用例异常 - 用例ID: {}, 用例编号: {}, 轮次: {}, 错误: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), e.getMessage(), e);
                
                // 对于未处理的异常，上报为FAILED状态
                try {
                    reportTestCaseResult(request, testCase, "FAILED", "执行异常: " + e.getMessage(), 0L, null, null, "执行异常: " + e.getMessage(), null);
                } catch (Exception reportException) {
                    log.error("上报用例执行结果失败 - 用例ID: {}, 错误: {}", testCase.getTestCaseId(), reportException.getMessage());
                }
            }
        }
        
        log.info("用例列表执行完成 - 成功: {}, 失败: {}, 取消: {}, 总计: {}", 
                successCount, failedCount, cancelledCount, request.getTestCaseList().size());
    }
    
    /**
     * 查找脚本文件
     */
    private Path findScriptFile(Path extractPath, TestCaseExecutionRequest.TestCaseInfo testCase) {
        String testCaseNumber = testCase.getTestCaseNumber();
        Long testCaseId = testCase.getTestCaseId();
        
        // 1. 优先尝试精确匹配用例编号
        if (testCaseNumber != null && !testCaseNumber.trim().isEmpty()) {
            Path scriptPath = extractPath.resolve("scripts").resolve(testCaseNumber + ".py");
            if (java.nio.file.Files.exists(scriptPath)) {
                log.info("找到精确匹配的脚本文件: {}", scriptPath);
                return scriptPath;
            }
            
            // 尝试cases目录
            scriptPath = extractPath.resolve("cases").resolve(testCaseNumber + ".py");
            if (java.nio.file.Files.exists(scriptPath)) {
                log.info("找到精确匹配的脚本文件: {}", scriptPath);
                return scriptPath;
            }
        }
        
        // 2. 尝试用例ID
        Path scriptPath = extractPath.resolve("scripts").resolve(testCaseId + ".py");
        if (java.nio.file.Files.exists(scriptPath)) {
            log.info("找到用例ID匹配的脚本文件: {}", scriptPath);
            return scriptPath;
        }
        
        scriptPath = extractPath.resolve("cases").resolve(testCaseId + ".py");
        if (java.nio.file.Files.exists(scriptPath)) {
            log.info("找到用例ID匹配的脚本文件: {}", scriptPath);
            return scriptPath;
        }
        
        // 3. 智能匹配：根据用例编号映射到可能的脚本文件名
        if (testCaseNumber != null && !testCaseNumber.trim().isEmpty()) {
            String[] possibleScriptNames = getPossibleScriptNames(testCaseNumber);
            for (String scriptName : possibleScriptNames) {
                scriptPath = extractPath.resolve("scripts").resolve(scriptName);
                if (java.nio.file.Files.exists(scriptPath)) {
                    log.info("找到智能匹配的脚本文件: {} -> {}", testCaseNumber, scriptPath);
                    return scriptPath;
                }
                
                scriptPath = extractPath.resolve("cases").resolve(scriptName);
                if (java.nio.file.Files.exists(scriptPath)) {
                    log.info("找到智能匹配的脚本文件: {} -> {}", testCaseNumber, scriptPath);
                    return scriptPath;
                }
            }
        }
        
        // 4. 如果都找不到，返回第一个可用的Python脚本
        try {
            java.nio.file.Files.walk(extractPath)
                .filter(path -> path.toString().endsWith(".py"))
                .findFirst()
                .ifPresent(path -> {
                    log.warn("未找到匹配的脚本文件，使用第一个可用的Python脚本: {} -> {}", testCaseNumber, path);
                });
        } catch (Exception e) {
            log.error("搜索Python脚本文件时出错: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 根据用例编号获取可能的脚本文件名
     */
    private String[] getPossibleScriptNames(String testCaseNumber) {
        // 这里可以根据实际的命名规则进行映射
        // 例如：TC001 -> 4G_Network_Connection_Test.py
        switch (testCaseNumber) {
            case "TC001":
                return new String[]{"4G_Network_Connection_Test.py", "test_network_connection.py"};
            case "TC002":
                return new String[]{"5G_Network_Performance_Test.py", "test_network_performance.py"};
            case "TC003":
                return new String[]{"WiFi_Connection_Stability_Test.py", "test_wifi_stability.py"};
            case "TC004":
                return new String[]{"Multi_Network_Switch_Test.py", "test_network_switch.py"};
            case "TC005":
                return new String[]{"Weak_Network_Test.py", "test_weak_network.py"};
            case "TC006":
                return new String[]{"Network_Interruption_Recovery_Test.py", "test_network_recovery.py"};
            case "TC007":
                return new String[]{"High_Concurrency_Network_Test.py", "test_high_concurrency.py"};
            case "TC008":
                return new String[]{"Network_Security_Test.py", "test_network_security.py"};
            default:
                return new String[]{};
        }
    }
    
    /**
     * 执行单个用例
     */
    private void executeSingleTestCase(TestCaseExecutionRequest request, 
                                     TestCaseExecutionRequest.TestCaseInfo testCase, 
                                     Path extractPath) throws Exception {
        log.info("开始执行用例 - 用例ID: {}, 用例编号: {}, 轮次: {}", 
                testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
        
        // 查找用例脚本文件 - 在scripts目录中查找，只使用用例编号
        if (testCase.getTestCaseNumber() == null || testCase.getTestCaseNumber().trim().isEmpty()) {
            String failureReason = "用例编号为空，无法查找脚本文件";
            log.error("用例编号为空 - 用例ID: {}, 轮次: {}", testCase.getTestCaseId(), testCase.getRound());
            reportTestCaseResult(request, testCase, "BLOCKED", "用例执行失败", 0L, null, null, failureReason, null);
            return;
        }
        
        String scriptFileName = testCase.getTestCaseNumber() + ".py";
        Path scriptsDir = extractPath.resolve("scripts");
        
        // 递归查找脚本文件
        Path scriptPath = findScriptFileRecursively(scriptsDir, scriptFileName);
        
        if (scriptPath == null) {
            String failureReason = "Python脚本文件不存在: 在scripts目录及其子目录中未找到 " + scriptFileName + " (用例编号: " + testCase.getTestCaseNumber() + ")";
            
            log.error("脚本文件不存在 - 用例ID: {}, 用例编号: {}, 轮次: {}, 错误: {}", 
                    testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), failureReason);
            
            // 上报Blocked状态和失败原因
            reportTestCaseResult(request, testCase, "BLOCKED", "用例执行失败", 0L, null, null, failureReason, null);
            return;
        }
        
        log.info("找到脚本文件 - 用例ID: {}, 用例编号: {}, 脚本路径: {}", 
                testCase.getTestCaseId(), testCase.getTestCaseNumber(), scriptPath);
        
        try {
            // 执行Python脚本，使用配置的超时时间
            Integer timeoutMinutes = caseExecutionConfig.getTimeoutMinutes();
            log.info("使用配置的超时时间执行用例 - 用例ID: {}, 用例编号: {}, 轮次: {}, 超时时间: {}分钟", 
                    testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), timeoutMinutes);
            
            // 获取当前任务的执行信息
            TaskExecutionInfo taskInfo = runningTasks.get(request.getTaskId());
            if (taskInfo == null) {
                log.warn("任务执行信息不存在，无法管理进程 - 任务ID: {}", request.getTaskId());
            }
            
            // 启动Python进程并添加到任务管理
            Process process = PythonExecutorUtil.startPythonProcess(scriptPath, testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), request.getLogReportUrl(), request.getTaskId(), request.getExecutorIp(), request.getCollectStrategyInfo(), request.getUeList(), request.getTaskCustomParams());
            
            if (taskInfo != null) {
                taskInfo.addProcess(process);
                log.info("Python进程已添加到任务管理 - 任务ID: {}, 用例ID: {}, 轮次: {}", 
                        request.getTaskId(), testCase.getTestCaseId(), testCase.getRound());
            }
            
            // 等待进程完成，使用配置的超时时间，同时检查任务是否被取消
            boolean completed = false;
            long startTime = System.currentTimeMillis();
            long timeoutMillis = timeoutMinutes * 60 * 1000L;
            
            while (!completed && (System.currentTimeMillis() - startTime) < timeoutMillis) {
                // 检查任务是否被取消
                if (taskInfo != null && taskInfo.getExecutionFuture() != null && taskInfo.getExecutionFuture().isCancelled()) {
                    log.warn("任务已被取消，终止正在执行的用例 - 任务ID: {}, 用例ID: {}", 
                            request.getTaskId(), testCase.getTestCaseId());
                    // 强制终止进程
                    if (process != null && process.isAlive()) {
                        process.destroy();
                        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            process.destroyForcibly();
                        }
                        log.info("已强制终止被取消任务的进程 - 任务ID: {}, 用例ID: {}", 
                                request.getTaskId(), testCase.getTestCaseId());
                    }
                    // 上报取消状态
                    reportTestCaseResult(request, testCase, "BLOCKED", "用例执行被取消", 
                            System.currentTimeMillis() - startTime, null, null, "任务被用户取消", null);
                    return;
                }
                
                // 等待进程完成，每次检查间隔1秒
                completed = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            }
            
            // 从任务管理中移除进程
            if (taskInfo != null) {
                taskInfo.removeProcess(process);
                log.info("Python进程已从任务管理移除 - 任务ID: {}, 用例ID: {}, 轮次: {}", 
                        request.getTaskId(), testCase.getTestCaseId(), testCase.getRound());
            }
            
            // 处理执行结果
            PythonExecutorUtil.PythonExecutionResult executionResult = handleProcessResult(process, completed, scriptPath, testCase, timeoutMinutes, request);
            
            // 解析执行结果和失败原因
            String status = executionResult.getStatus();
            String result = executionResult.getResult();
            String failureReason = executionResult.getFailureReason();
            
            // 根据执行结果进行详细分析
            TestCaseAnalysis analysis = analyzeTestCaseResult(executionResult, testCase);
            
            // 上报解析后的执行结果
            log.info("准备上报用例执行结果 - 用例ID: {}, 轮次: {}, 状态: {}, 结果: {}, 失败原因: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), analysis.getStatus(), analysis.getResult(), analysis.getFailureReason());
            log.info("结果上报URL: {}", request.getResultReportUrl());
            
            reportTestCaseResult(request, testCase, analysis.getStatus(), analysis.getResult(), 
                    executionResult.getExecutionTime(), executionResult.getStartTime(), executionResult.getEndTime(), analysis.getFailureReason(), executionResult.getLogFilePath());
            
            // // 上报执行日志
            // log.info("准备上报用例执行日志 - 用例ID: {}, 轮次: {}, 日志文件路径: {}, 日志内容长度: {}", 
            //         testCase.getTestCaseId(), testCase.getRound(), executionResult.getLogFilePath(), 
            //         executionResult.getLogContent() != null ? executionResult.getLogContent().length() : 0);
            // log.info("日志上报URL: {}", request.getLogReportUrl());
            
            // httpReportUtil.reportTestCaseLog(request.getLogReportUrl(), executionResult.getLogContent(), 
            //         executionResult.getLogFilePath(), request.getTaskId(), testCase.getTestCaseId(), testCase.getRound());
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            String failureReason;
            
            // 检查是否是Python执行器不可用的错误
            if (errorMessage != null && errorMessage.contains("Cannot run program \"python\"") && errorMessage.contains("No such file or directory")) {
                failureReason = "Python执行器不可用: 系统中未安装Python或Python不在PATH环境变量中";
                log.error("Python执行器不可用 - 用例ID: {}, 用例编号: {}, 轮次: {}, 错误: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), errorMessage);
            } else {
                failureReason = "Python脚本执行异常: " + errorMessage;
                log.error("Python脚本执行异常 - 用例ID: {}, 用例编号: {}, 轮次: {}, 错误: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), errorMessage);
            }
            
            // 上报BLOCKED状态和错误原因
            reportTestCaseResult(request, testCase, "BLOCKED", "用例执行失败", 0L, null, null, failureReason, null);
        }
    }
    
    /**
     * 分析用例执行结果
     * 
     * @param executionResult 执行结果
     * @param testCase 用例信息
     * @return 用例分析结果
     */
    private TestCaseAnalysis analyzeTestCaseResult(PythonExecutorUtil.PythonExecutionResult executionResult, 
                                                 TestCaseExecutionRequest.TestCaseInfo testCase) {
        String status = executionResult.getStatus();
        String result = executionResult.getResult();
        String failureReason = executionResult.getFailureReason();
        String logContent = executionResult.getLogContent();
        
        // 使用智能解析工具分析结果
        TestCaseResultParser.TestCaseParseResult parseResult = TestCaseResultParser.parseResult(logContent);
        
        // 如果解析工具能够识别出结果，使用解析结果
        if (!"BLOCKED".equals(parseResult.getStatus())) {
            status = parseResult.getStatus();
            result = parseResult.getResultMessage();
            
            // 构建详细的失败原因
            if (parseResult.getFailureDetails() != null) {
                failureReason = parseResult.getFailureDetails();
            } else if (parseResult.getFailedTests() > 0 || parseResult.getErrorTests() > 0) {
                failureReason = String.format("测试统计: 总测试数=%d, 成功=%d, 失败=%d, 错误=%d, 成功率=%.1f%%", 
                        parseResult.getTotalTests(), parseResult.getSuccessTests(), 
                        parseResult.getFailedTests(), parseResult.getErrorTests(), parseResult.getSuccessRate());
            }
        } else {
            // 降级到原有的分析逻辑
            if ("SUCCESS".equals(status)) {
                // 检查是否真的成功
                if (logContent.contains("FAIL") || logContent.contains("ERROR") || logContent.contains("失败")) {
                    status = "FAILED";
                    result = "用例执行失败";
                    failureReason = "日志分析发现失败信息: " + extractFailureDetails(logContent);
                } else if (logContent.contains("PASS") || logContent.contains("SUCCESS") || logContent.contains("成功")) {
                    status = "SUCCESS";
                    result = "用例执行成功";
                    failureReason = null;
                } else {
                    // 没有明确的成功或失败标识
                    status = "BLOCKED";
                    result = "用例执行被阻塞";
                    failureReason = "用例执行被阻塞: 日志中未包含明确的成功或失败标识，可能由于环境问题或脚本异常导致";
                }
            } else if ("FAILED".equals(status)) {
                // 分析失败原因
                failureReason = analyzeDetailedFailureReason(logContent, failureReason);
            } else if ("BLOCKED".equals(status)) {
                // 阻塞状态的处理 - 提供更具体的阻塞原因
                failureReason = analyzeDetailedFailureReason(logContent, failureReason);
            }
        }
        
        // 添加性能指标信息
        if (parseResult.getNetworkLatency() != null) {
            result += String.format(" (网络延迟: %.2fms)", parseResult.getNetworkLatency());
        }
        if (parseResult.getBandwidth() != null) {
            result += String.format(" (带宽: %.2f%s)", parseResult.getBandwidth(), parseResult.getBandwidthUnit());
        }
        if (parseResult.getSignalStrength() != null) {
            result += String.format(" (信号强度: %.2fdBm)", parseResult.getSignalStrength());
        }
        
        return new TestCaseAnalysis(status, result, failureReason);
    }
    
    /**
     * 提取失败详情
     * 
     * @param logContent 日志内容
     * @return 失败详情
     */
    private String extractFailureDetails(String logContent) {
        // 提取失败的具体信息
        String[] lines = logContent.split("\n");
        StringBuilder failureDetails = new StringBuilder();
        
        for (String line : lines) {
            if (line.contains("FAIL") || line.contains("ERROR") || line.contains("失败") || 
                line.contains("AssertionError") || line.contains("Exception")) {
                failureDetails.append(line.trim()).append("; ");
            }
        }
        
        return failureDetails.length() > 0 ? failureDetails.toString() : "阻塞失败原因";
    }
    
    /**
     * 分析详细失败原因
     * 
     * @param logContent 日志内容
     * @param originalReason 原始失败原因
     * @return 详细失败原因
     */
    private String analyzeDetailedFailureReason(String logContent, String originalReason) {
        if (logContent.contains("网络连接失败") || logContent.contains("Connection refused")) {
            return "网络连接失败: 无法连接到目标服务器，请检查网络配置和服务器状态";
        } else if (logContent.contains("超时") || logContent.contains("timeout")) {
            return "网络请求超时: 服务器响应时间过长，请检查网络连接和服务器负载";
        } else if (logContent.contains("DNS解析失败") || logContent.contains("Name or service not known")) {
            return "DNS解析失败: 无法解析域名，请检查DNS配置和网络连接";
        } else if (logContent.contains("权限不足") || logContent.contains("Permission denied")) {
            return "权限不足: 无法访问所需资源，请检查文件权限和用户权限设置";
        } else if (logContent.contains("文件不存在") || logContent.contains("No such file")) {
            return "文件不存在: 无法找到所需的文件或目录，请检查文件路径和文件是否存在";
        } else if (logContent.contains("模块导入失败") || logContent.contains("ImportError")) {
            return "模块导入失败: Python依赖包缺失，请检查Python环境和依赖包安装";
        } else if (logContent.contains("内存不足") || logContent.contains("out of memory")) {
            return "内存不足: 系统内存不足，无法执行用例，请检查系统资源";
        } else if (logContent.contains("磁盘空间不足") || logContent.contains("no space left")) {
            return "磁盘空间不足: 系统磁盘空间不足，无法写入文件，请清理磁盘空间";
        } else if (logContent.contains("Python执行器不可用")) {
            return "Python执行器不可用: 系统中未安装Python或Python不在PATH环境变量中，请检查Python安装";
        } else {
            return originalReason != null ? originalReason : "用例执行被阻塞: 未知原因导致执行失败";
        }
    }
    
    /**
     * 用例分析结果
     */
    private static class TestCaseAnalysis {
        private String status;
        private String result;
        private String failureReason;
        
        public TestCaseAnalysis(String status, String result, String failureReason) {
            this.status = status;
            this.result = result;
            this.failureReason = failureReason;
        }
        
        public String getStatus() { return status; }
        public String getResult() { return result; }
        public String getFailureReason() { return failureReason; }
    }
    
    /**
     * 上报用例执行结果
     */
    private void reportTestCaseResult(TestCaseExecutionRequest request,
                                    TestCaseExecutionRequest.TestCaseInfo testCase,
                                    String status,
                                    String result,
                                    Long executionTime,
                                    java.time.LocalDateTime startTime,
                                    java.time.LocalDateTime endTime) {
        reportTestCaseResult(request, testCase, status, result, executionTime, startTime, endTime, null);
    }
    
    /**
     * 上报用例执行结果（带失败原因和日志文件路径）
     */
    private void reportTestCaseResult(TestCaseExecutionRequest request,
                                    TestCaseExecutionRequest.TestCaseInfo testCase,
                                    String status,
                                    String result,
                                    Long executionTime,
                                    java.time.LocalDateTime startTime,
                                    java.time.LocalDateTime endTime,
                                    String failureReason,
                                    String logFilePath) {
        log.info("构建用例执行结果报告 - 用例ID: {}, 轮次: {}, 状态: {}, 结果: {}, 日志文件: {}", 
                testCase.getTestCaseId(), testCase.getRound(), status, result, logFilePath);
        
        TestCaseResultReport report = new TestCaseResultReport();
        report.setTaskId(request.getTaskId());
        report.setTestCaseId(testCase.getTestCaseId());
        report.setRound(testCase.getRound());
        report.setStatus(status);
        report.setResult(result);
        report.setExecutionTime(executionTime);
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setExecutorIp(request.getExecutorIp());
        report.setTestCaseSetId(request.getTestCaseSetId());
        report.setLogFilePath(logFilePath);
        
        if ("FAILED".equals(status) || "BLOCKED".equals(status)) {
            report.setFailureReason(failureReason != null ? failureReason : result);
            log.info("设置失败原因 - 用例ID: {}, 轮次: {}, 失败原因: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), report.getFailureReason());
        }
        
        log.info("用例执行结果报告构建完成 - 用例ID: {}, 轮次: {}, 任务ID: {}, 执行机IP: {}, 日志文件: {}", 
                testCase.getTestCaseId(), testCase.getRound(), request.getTaskId(), request.getExecutorIp(), logFilePath);
        
        httpReportUtil.reportTestCaseResult(request.getResultReportUrl(), report);
    }
    
    /**
     * 上报用例执行结果（带失败原因）
     */
    private void reportTestCaseResult(TestCaseExecutionRequest request,
                                    TestCaseExecutionRequest.TestCaseInfo testCase,
                                    String status,
                                    String result,
                                    Long executionTime,
                                    java.time.LocalDateTime startTime,
                                    java.time.LocalDateTime endTime,
                                    String failureReason) {
        reportTestCaseResult(request, testCase, status, result, executionTime, startTime, endTime, failureReason, null);
    }
    
    @Override
    public boolean cancelTaskExecution(String taskId) {
        log.info("开始取消任务执行 - 任务ID: {}", taskId);
        
        TaskExecutionInfo taskInfo = runningTasks.get(taskId);
        if (taskInfo == null) {
            log.warn("任务不存在或已完成 - 任务ID: {}", taskId);
            return false;
        }
        
        try {
            // 1. 使用PythonExecutorUtil终止所有相关的Python进程及其子进程
            log.info("开始终止任务ID为 {} 的所有Python进程", taskId);
            PythonExecutorUtil.terminateAllPythonProcessesByTaskId(taskId);
            
            // 2. 取消所有相关进程（Java层面的进程管理）
            taskInfo.cancelAllProcesses();
            
            // 3. 取消执行Future
            taskInfo.cancelExecution();
            
            // 4. 从运行任务列表中移除
            runningTasks.remove(taskId);
            
            log.info("任务取消成功 - 任务ID: {}", taskId);
            return true;
            
        } catch (Exception e) {
            log.error("取消任务失败 - 任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 递归查找脚本文件
     * 
     * @param scriptsDir scripts目录路径
     * @param scriptFileName 脚本文件名
     * @return 找到的脚本文件路径，如果未找到则返回null
     */
    private Path findScriptFileRecursively(Path scriptsDir, String scriptFileName) {
        if (!Files.exists(scriptsDir) || !Files.isDirectory(scriptsDir)) {
            log.warn("scripts目录不存在或不是目录: {}", scriptsDir);
            return null;
        }
        
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(scriptFileName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("递归查找脚本文件时发生错误 - 目录: {}, 文件名: {}, 错误: {}", 
                    scriptsDir, scriptFileName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 处理进程执行结果
     * 
     * @param process 进程对象
     * @param completed 是否完成
     * @param scriptPath 脚本路径
     * @param testCase 用例信息
     * @param timeoutMinutes 超时时间
     * @param request 执行请求
     * @return 执行结果
     */
    private PythonExecutorUtil.PythonExecutionResult handleProcessResult(Process process, boolean completed, 
                                                                        Path scriptPath, TestCaseExecutionRequest.TestCaseInfo testCase, 
                                                                        Integer timeoutMinutes, TestCaseExecutionRequest request) {
        try {
            // 读取日志文件
            String rootDir = fileStorageConfig != null ? fileStorageConfig.getRootDirectory() : System.getProperty("java.io.tmpdir");
            Path rootDirectory = java.nio.file.Paths.get(rootDir);
            Path taskDir = rootDirectory.resolve(request.getTaskId());
            Path logsDir = taskDir.resolve("logs");
            
            String logFileName;
            if (testCase.getTestCaseNumber() != null && !testCase.getTestCaseNumber().trim().isEmpty()) {
                logFileName = String.format("%s_%d.log", testCase.getTestCaseNumber(), testCase.getRound());
            } else {
                logFileName = String.format("%d_%d.log", testCase.getTestCaseId(), testCase.getRound());
            }
            Path logFilePath = logsDir.resolve(logFileName);
            
            String logContent = "";
            if (Files.exists(logFilePath)) {
                logContent = new String(Files.readAllBytes(logFilePath), java.nio.charset.StandardCharsets.UTF_8);
            }
            
            // 判断执行结果
            String status = "SUCCESS";
            String result = "用例执行成功";
            String failureReason = null;
            
            if (!completed) {
                status = "FAILED";  // 超时归类为FAILED
                result = "用例执行超时";
                failureReason = "用例执行超时: 超过配置的超时时间 " + timeoutMinutes + " 分钟";
                
                // 强制终止进程及其子进程
                terminateProcessAndChildren(process);
                
                log.error("用例执行超时 - 用例ID: {}, 轮次: {}, 超时时间: {}分钟", 
                        testCase.getTestCaseId(), testCase.getRound(), timeoutMinutes);
            } else if (process.exitValue() != 0) {
                // 检查是否是环境问题导致的阻塞
                if (isBlockedByEnvironment(logContent)) {
                    status = "BLOCKED";
                    result = "用例执行被阻塞（环境问题）";
                    failureReason = "环境问题导致用例无法执行: " + analyzeFailureReason(logContent, process.exitValue());
                } else {
                    status = "FAILED";
                    result = "用例执行失败，退出码: " + process.exitValue();
                    failureReason = analyzeFailureReason(logContent, process.exitValue());
                }
            } else {
                // 根据控制台输出判断结果
                TestResultAnalysis analysis = analyzeTestOutput(logContent);
                status = analysis.getStatus();
                result = analysis.getResult();
                failureReason = analysis.getFailureReason();
            }
            
            // 上传日志文件到gohttpserver（如果提供了gohttpserver地址）
            String uploadedLogUrl = null;
            if (request.getLogReportUrl() != null && !request.getLogReportUrl().trim().isEmpty()) {
                try {
                    com.caseexecute.util.GoHttpServerClient goHttpServerClient = new com.caseexecute.util.GoHttpServerClient();
                    uploadedLogUrl = goHttpServerClient.uploadLocalFile(logFilePath.toString(), logFileName, request.getLogReportUrl(), request.getTaskId());
                    log.info("日志文件上传成功 - 用例ID: {}, 轮次: {}, 上传URL: {}", testCase.getTestCaseId(), testCase.getRound(), uploadedLogUrl);
                } catch (Exception e) {
                    log.error("日志文件上传失败 - 用例ID: {}, 轮次: {}, 错误: {}", 
                            testCase.getTestCaseId(), testCase.getRound(), e.getMessage());
                }
            } else {
                log.info("未提供gohttpserver地址，跳过日志文件上传 - 用例ID: {}, 轮次: {}", testCase.getTestCaseId(), testCase.getRound());
            }
            
            return PythonExecutorUtil.PythonExecutionResult.builder()
                    .status(status)
                    .result(result)
                    .executionTime(System.currentTimeMillis() - System.currentTimeMillis()) // 简化处理
                    .startTime(java.time.LocalDateTime.now()) // 简化处理
                    .endTime(java.time.LocalDateTime.now()) // 简化处理
                    .logContent(logContent)
                    .logFilePath(uploadedLogUrl != null ? uploadedLogUrl : logFileName)
                    .failureReason(failureReason)
                    .build();
                    
        } catch (Exception e) {
            log.error("处理进程执行结果时发生错误 - 用例ID: {}, 轮次: {}, 错误: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), e.getMessage());
            
            return PythonExecutorUtil.PythonExecutionResult.builder()
                    .status("BLOCKED")
                    .result("用例执行失败")
                    .executionTime(0L)
                    .startTime(java.time.LocalDateTime.now())
                    .endTime(java.time.LocalDateTime.now())
                    .logContent("")
                    .logFilePath("")
                    .failureReason("处理执行结果时发生错误: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 强制终止进程及其子进程
     */
    private void terminateProcessAndChildren(Process process) {
        try {
            if (process != null && process.isAlive()) {
                process.destroy();
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                log.info("已强制终止进程");
            }
        } catch (Exception e) {
            log.error("终止进程失败: {}", e.getMessage());
        }
    }
    
    /**
     * 检查是否因环境问题被阻塞
     */
    private boolean isBlockedByEnvironment(String logContent) {
        if (logContent == null) {
            return false;
        }
        
        String lowerContent = logContent.toLowerCase();
        return lowerContent.contains("command not found") || 
               lowerContent.contains("no such file or directory") ||
               lowerContent.contains("permission denied") ||
               lowerContent.contains("cannot run program") ||
               lowerContent.contains("python") && lowerContent.contains("not found");
    }
    
    /**
     * 分析失败原因
     */
    private String analyzeFailureReason(String logContent, int exitCode) {
        if (logContent == null || logContent.trim().isEmpty()) {
            return "进程异常退出，退出码: " + exitCode;
        }
        
        // 提取最后几行错误信息
        String[] lines = logContent.split("\n");
        StringBuilder errorInfo = new StringBuilder();
        int startIndex = Math.max(0, lines.length - 5); // 取最后5行
        
        for (int i = startIndex; i < lines.length; i++) {
            if (lines[i].trim().length() > 0) {
                errorInfo.append(lines[i].trim()).append("; ");
            }
        }
        
        return errorInfo.length() > 0 ? errorInfo.toString() : "进程异常退出，退出码: " + exitCode;
    }
    
    /**
     * 分析测试输出
     */
    private TestResultAnalysis analyzeTestOutput(String logContent) {
        if (logContent == null) {
            return new TestResultAnalysis("BLOCKED", "无法读取执行日志", "日志内容为空");
        }
        
        String lowerContent = logContent.toLowerCase();
        
        if (lowerContent.contains("pass") || lowerContent.contains("success") || lowerContent.contains("成功")) {
            return new TestResultAnalysis("SUCCESS", "用例执行成功", null);
        } else if (lowerContent.contains("fail") || lowerContent.contains("error") || lowerContent.contains("失败")) {
            return new TestResultAnalysis("FAILED", "用例执行失败", extractFailureDetails(logContent));
        } else {
            return new TestResultAnalysis("BLOCKED", "无法确定执行结果", "日志内容无法解析");
        }
    }
    

    
    /**
     * 记录任务上下文信息（UE信息和采集策略信息）
     * 
     * @param request 用例执行任务请求
     */
    private void logTaskContextInfo(TestCaseExecutionRequest request) {
        log.info("=== 任务上下文信息 ===");
        
        // 记录UE信息
        if (request.getUeList() != null && !request.getUeList().isEmpty()) {
            log.info("执行机关联的UE设备信息:");
            log.info("  - UE设备数量: {}", request.getUeList().size());
            for (TestCaseExecutionRequest.UeInfo ue : request.getUeList()) {
                log.info("  - UE ID: {}, 名称: {}, 用途: {}, 网络类型: {}, 厂商: {}, 端口: {}, 状态: {}", 
                        ue.getUeId(), ue.getName(), ue.getPurpose(), 
                        ue.getNetworkTypeName(), ue.getVendor(), ue.getPort(), ue.getStatus());
                if (ue.getDescription() != null && !ue.getDescription().trim().isEmpty()) {
                    log.info("    - 描述: {}", ue.getDescription());
                }
            }
        } else {
            log.warn("执行机未关联UE设备信息");
        }
        
        // 记录采集策略信息
        if (request.getCollectStrategyInfo() != null) {
            TestCaseExecutionRequest.CollectStrategyInfo strategy = request.getCollectStrategyInfo();
            log.info("采集策略信息:");
            log.info("  - 策略ID: {}", strategy.getId());
            log.info("  - 策略名称: {}", strategy.getName());
            log.info("  - 采集次数: {}", strategy.getCollectCount());
            log.info("  - 业务大类: {}", strategy.getBusinessCategory());
            log.info("  - APP: {}", strategy.getApp());
            log.info("  - APPEN: {}", strategy.getAppEn());
            log.info("  - 意图: {}", strategy.getIntent());
            log.info("  - 策略状态: {}", strategy.getStatus());
            if (strategy.getCustomParams() != null && !strategy.getCustomParams().trim().isEmpty()) {
                log.info("  - 自定义参数: {}", strategy.getCustomParams());
            }
            if (strategy.getDescription() != null && !strategy.getDescription().trim().isEmpty()) {
                log.info("  - 策略描述: {}", strategy.getDescription());
            }
        } else {
            log.warn("未提供采集策略信息");
        }
        
        log.info("=== 任务上下文信息记录完成 ===");
    }
    
    /**
     * 测试结果分析
     */
    private static class TestResultAnalysis {
        private final String status;
        private final String result;
        private final String failureReason;
        
        public TestResultAnalysis(String status, String result, String failureReason) {
            this.status = status;
            this.result = result;
            this.failureReason = failureReason;
        }
        
        public String getStatus() { return status; }
        public String getResult() { return result; }
        public String getFailureReason() { return failureReason; }
    }
}
