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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Service
public class TestCaseExecutionServiceImpl implements TestCaseExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCaseExecutionServiceImpl.class);

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
                        LOGGER.info("Process terminated - Task ID: {}", taskId);
                    } catch (Exception e) {
                        LOGGER.error("Failed to terminate process - Task ID: {}, Error: {}", taskId, e.getMessage());
                    }
                }
            }
            processes.clear();
        }
        
        public void cancelExecution() {
            if (executionFuture != null && !executionFuture.isDone()) {
                executionFuture.cancel(true);
                LOGGER.info("Task execution cancelled - Task ID: {}", taskId);
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
        LOGGER.info("Starting test case execution task - Task ID: {}", request.getTaskId());
        
        // 记录UE信息和采集策略信息
        logTaskContextInfo(request);
        
        // 先创建任务执行信息并存储，确保在异步执行开始前就可用
        TaskExecutionInfo taskInfo = new TaskExecutionInfo(request.getTaskId(), null);
        runningTasks.put(request.getTaskId(), taskInfo);
        LOGGER.info("Task added to running list - Task ID: {}", request.getTaskId());
        
        // 异步执行，避免阻塞接口响应
        CompletableFuture<Void> executionFuture = CompletableFuture.runAsync(() -> {
            Path zipFilePath = null;
            Path extractPath = null;
            
            try {
                LOGGER.info("Starting to download test case set file - Task ID: {}, URL: {}", request.getTaskId(), request.getTestCaseSetPath());
                
                // 1. 下载用例集文件到/opt目录下的taskId子目录
                zipFilePath = FileDownloadUtil.downloadFile(request.getTestCaseSetPath(), request.getTaskId());
                LOGGER.info("Test case set file download completed - Task ID: {}, File path: {}", request.getTaskId(), zipFilePath);
                
                // 2. 解压用例集到/opt目录下的taskId子目录
                LOGGER.info("Starting to extract test case set file - Task ID: {}, File path: {}", request.getTaskId(), zipFilePath);
                extractPath = FileDownloadUtil.extractZipFile(zipFilePath, request.getTaskId());
                LOGGER.info("Test case set file extraction completed - Task ID: {}, Extract path: {}", request.getTaskId(), extractPath);
                
                // 3. 执行用例列表
                LOGGER.info("Starting to execute test case list - Task ID: {}, Test case count: {}", request.getTaskId(), request.getTestCaseList().size());
                executeTestCaseList(request, extractPath);
                
                LOGGER.info("Test case execution task processing completed - Task ID: {}", request.getTaskId());
                
            } catch (Exception e) {
                LOGGER.error("Test case execution task processing failed - Task ID: {}, Error: {}", request.getTaskId(), e.getMessage(), e);
            } finally {
                // 4. 清理任务目录
                LOGGER.info("Starting to cleanup task directory - Task ID: {}", request.getTaskId());
                try {
                    FileDownloadUtil.cleanupTaskDirectory(request.getTaskId());
                    LOGGER.info("Task directory cleaned up - Task ID: {}", request.getTaskId());
                } catch (Exception e) {
                    LOGGER.warn("Failed to cleanup task directory - Task ID: {}, Error: {}", request.getTaskId(), e.getMessage());
                }
                LOGGER.info("Task directory cleanup completed - Task ID: {}", request.getTaskId());
                
                // 5. 从运行任务列表中移除
                runningTasks.remove(request.getTaskId());
                LOGGER.info("Task removed from running list - Task ID: {}", request.getTaskId());
            }
        });
        
        // 更新任务执行信息中的Future
        taskInfo.setExecutionFuture(executionFuture);
        LOGGER.info("Task execution Future set - Task ID: {}", request.getTaskId());
    }
    
    /**
     * 执行用例列表
     */
    private void executeTestCaseList(TestCaseExecutionRequest request, Path extractPath) {
        LOGGER.info("Starting to execute test case list - Test case count: {}", request.getTestCaseList().size());
        
        int successCount = 0;
        int failedCount = 0;
        int cancelledCount = 0;
        
        for (TestCaseExecutionRequest.TestCaseInfo testCase : request.getTestCaseList()) {
            if (isTaskCancelled(request, testCase)) {
                cancelledCount++;
                continue;
            }
            
            try {
                LOGGER.info("Starting to execute test case - Test case ID: {}, Test case number: {}, Round: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
                
                executeSingleTestCase(request, testCase, extractPath);
                successCount++;
                
                LOGGER.info("Test case execution completed - Test case ID: {}, Test case number: {}, Round: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
                
            } catch (Exception e) {
                failedCount++;
                handleTestCaseExecutionException(request, testCase, e);
            }
        }
        
        LOGGER.info("Test case list execution completed - Success: {}, Failed: {}, Cancelled: {}, Total: {}", 
                successCount, failedCount, cancelledCount, request.getTestCaseList().size());
    }
    
    /**
     * 检查任务是否已被取消
     */
    private boolean isTaskCancelled(TestCaseExecutionRequest request, TestCaseExecutionRequest.TestCaseInfo testCase) {
        TaskExecutionInfo taskInfo = runningTasks.get(request.getTaskId());
        if (taskInfo == null) {
            LOGGER.warn("Task has been cancelled, stopping execution of remaining test cases - Task ID: {}", request.getTaskId());
            reportCancelledTestCase(request, testCase);
            return true;
        }
        
        if (taskInfo.getExecutionFuture() != null && taskInfo.getExecutionFuture().isCancelled()) {
            LOGGER.warn("Task execution has been cancelled, stopping execution of remaining test cases - Task ID: {}", request.getTaskId());
            reportCancelledTestCase(request, testCase);
            return true;
        }
        
        return false;
    }
    
    /**
     * 上报被取消的用例状态
     */
    private void reportCancelledTestCase(TestCaseExecutionRequest request, TestCaseExecutionRequest.TestCaseInfo testCase) {
        try {
            reportTestCaseResult(request, testCase, "BLOCKED", "Test case execution cancelled", 0L, null, null, "Task cancelled by user", null);
        } catch (Exception reportException) {
            LOGGER.error("Failed to report cancelled test case status - Test case ID: {}, Error: {}", testCase.getTestCaseId(), reportException.getMessage());
        }
    }
    
    /**
     * 处理用例执行异常
     */
    private void handleTestCaseExecutionException(TestCaseExecutionRequest request, TestCaseExecutionRequest.TestCaseInfo testCase, Exception e) {
        LOGGER.error("Test case execution exception - Test case ID: {}, Test case number: {}, Round: {}, Error: {}", 
                testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), e.getMessage(), e);
        
        try {
            reportTestCaseResult(request, testCase, "FAILED", "Execution exception: " + e.getMessage(), 0L, null, null, "Execution exception: " + e.getMessage(), null);
        } catch (Exception reportException) {
            LOGGER.error("Failed to report test case execution result - Test case ID: {}, Error: {}", testCase.getTestCaseId(), reportException.getMessage());
        }
    }
    
    /**
     * 查找脚本文件
     */
    private Path findScriptFile(Path extractPath, TestCaseExecutionRequest.TestCaseInfo testCase) {
        String testCaseNumber = testCase.getTestCaseNumber();
        Long testCaseId = testCase.getTestCaseId();
        
        if (testCaseNumber != null && !testCaseNumber.trim().isEmpty()) {
            Path scriptPath = extractPath.resolve("scripts").resolve(testCaseNumber + ".py");
            if (java.nio.file.Files.exists(scriptPath)) {
                LOGGER.info("Found exact match script file: {}", scriptPath);
                return scriptPath;
            }
            
            scriptPath = extractPath.resolve("cases").resolve(testCaseNumber + ".py");
            if (java.nio.file.Files.exists(scriptPath)) {
                LOGGER.info("Found exact match script file: {}", scriptPath);
                return scriptPath;
            }
        }
        
        Path scriptPath = extractPath.resolve("scripts").resolve(testCaseId + ".py");
        if (java.nio.file.Files.exists(scriptPath)) {
            LOGGER.info("Found test case ID match script file: {}", scriptPath);
            return scriptPath;
        }
        
        scriptPath = extractPath.resolve("cases").resolve(testCaseId + ".py");
        if (java.nio.file.Files.exists(scriptPath)) {
            LOGGER.info("Found test case ID match script file: {}", scriptPath);
            return scriptPath;
        }
        
        if (testCaseNumber != null && !testCaseNumber.trim().isEmpty()) {
            String[] possibleScriptNames = getPossibleScriptNames(testCaseNumber);
            for (String scriptName : possibleScriptNames) {
                scriptPath = extractPath.resolve("scripts").resolve(scriptName);
                if (java.nio.file.Files.exists(scriptPath)) {
                    LOGGER.info("Found intelligent match script file: {} -> {}", testCaseNumber, scriptPath);
                    return scriptPath;
                }
                
                scriptPath = extractPath.resolve("cases").resolve(scriptName);
                if (java.nio.file.Files.exists(scriptPath)) {
                    LOGGER.info("Found intelligent match script file: {} -> {}", testCaseNumber, scriptPath);
                    return scriptPath;
                }
            }
        }
        
        try {
            java.nio.file.Files.walk(extractPath)
                .filter(path -> path.toString().endsWith(".py"))
                .findFirst()
                .ifPresent(path -> {
                    LOGGER.warn("No matching script file found, using first available Python script: {} -> {}", testCaseNumber, path);
                });
        } catch (Exception e) {
            LOGGER.error("Error occurred while searching for Python script file: {}", e.getMessage());
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
        LOGGER.info("Starting to execute test case - Test case ID: {}, Test case number: {}, Round: {}", 
                testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
        
        Path scriptPath = findAndValidateScriptFile(request, testCase, extractPath);
        if (scriptPath == null) {
            return;
        }
        
        try {
            executePythonScript(request, testCase, scriptPath);
        } catch (Exception e) {
            handlePythonExecutionException(request, testCase, e);
        }
    }
    
    /**
     * 查找并验证脚本文件
     */
    private Path findAndValidateScriptFile(TestCaseExecutionRequest request, 
                                          TestCaseExecutionRequest.TestCaseInfo testCase, 
                                          Path extractPath) {
        if (testCase.getTestCaseNumber() == null || testCase.getTestCaseNumber().trim().isEmpty()) {
            String failureReason = "Test case number is empty, cannot find script file";
            LOGGER.error("Test case number is empty - Test case ID: {}, Round: {}", testCase.getTestCaseId(), testCase.getRound());
            reportTestCaseResult(request, testCase, "BLOCKED", "Test case execution failed", 0L, null, null, failureReason, null);
            return null;
        }
        
        String scriptFileName = testCase.getTestCaseNumber() + ".py";
        Path scriptsDir = extractPath.resolve("scripts");
        Path scriptPath = findScriptFileRecursively(scriptsDir, scriptFileName);
        
        if (scriptPath == null) {
            String failureReason = "Python script file does not exist: " + scriptFileName + " not found in scripts directory and subdirectories (Test case number: " + testCase.getTestCaseNumber() + ")";
            LOGGER.error("Script file does not exist - Test case ID: {}, Test case number: {}, Round: {}, Error: {}", 
                    testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), failureReason);
            reportTestCaseResult(request, testCase, "BLOCKED", "Test case execution failed", 0L, null, null, failureReason, null);
            return null;
        }
        
        LOGGER.info("Script file found - Test case ID: {}, Test case number: {}, Script path: {}", 
                testCase.getTestCaseId(), testCase.getTestCaseNumber(), scriptPath);
        return scriptPath;
    }
    
    /**
     * 执行Python脚本
     */
    private void executePythonScript(TestCaseExecutionRequest request, 
                                    TestCaseExecutionRequest.TestCaseInfo testCase, 
                                    Path scriptPath) throws Exception {
        Integer timeoutMinutes = caseExecutionConfig.getTimeoutMinutes();
        LOGGER.info("Executing test case with configured timeout - Test case ID: {}, Test case number: {}, Round: {}, Timeout: {} minutes", 
                testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), timeoutMinutes);
        
        TaskExecutionInfo taskInfo = runningTasks.get(request.getTaskId());
        if (taskInfo == null) {
            LOGGER.warn("Task execution info does not exist, cannot manage process - Task ID: {}", request.getTaskId());
        }
        
        Process process = startPythonProcess(request, testCase, scriptPath, taskInfo);
        boolean completed = waitForProcessCompletion(request, testCase, process, taskInfo, timeoutMinutes);
        
        if (taskInfo != null) {
            taskInfo.removeProcess(process);
            LOGGER.info("Python process removed from task management - Task ID: {}, Test case ID: {}, Round: {}", 
                    request.getTaskId(), testCase.getTestCaseId(), testCase.getRound());
        }
        
        processExecutionResult(request, testCase, process, completed, timeoutMinutes);
    }
    
    /**
     * 启动Python进程
     */
    private Process startPythonProcess(TestCaseExecutionRequest request, 
                                      TestCaseExecutionRequest.TestCaseInfo testCase, 
                                      Path scriptPath, 
                                      TaskExecutionInfo taskInfo) throws Exception {
        Process process = PythonExecutorUtil.startPythonProcess(scriptPath, testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), request.getLogReportUrl(), request.getTaskId(), request.getExecutorIp(), request.getCollectStrategyInfo(), request.getUeList(), request.getTaskCustomParams());
        
        if (taskInfo != null) {
            taskInfo.addProcess(process);
            LOGGER.info("Python process added to task management - Task ID: {}, Test case ID: {}, Round: {}", 
                    request.getTaskId(), testCase.getTestCaseId(), testCase.getRound());
        }
        
        return process;
    }
    
    /**
     * 等待进程完成
     */
    private boolean waitForProcessCompletion(TestCaseExecutionRequest request, 
                                            TestCaseExecutionRequest.TestCaseInfo testCase, 
                                            Process process, 
                                            TaskExecutionInfo taskInfo, 
                                            Integer timeoutMinutes) throws Exception {
        boolean completed = false;
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutMinutes * 60 * 1000L;
        
        while (!completed && (System.currentTimeMillis() - startTime) < timeoutMillis) {
            if (isTaskExecutionCancelled(request, testCase, taskInfo, process, startTime)) {
                return false;
            }
            
            completed = process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
        }
        
        return completed;
    }
    
    /**
     * 检查任务执行是否被取消
     */
    private boolean isTaskExecutionCancelled(TestCaseExecutionRequest request, 
                                           TestCaseExecutionRequest.TestCaseInfo testCase, 
                                           TaskExecutionInfo taskInfo, 
                                           Process process, 
                                           long startTime) throws Exception {
        if (taskInfo != null && taskInfo.getExecutionFuture() != null && taskInfo.getExecutionFuture().isCancelled()) {
            LOGGER.warn("Task has been cancelled, terminating executing test case - Task ID: {}, Test case ID: {}", 
                    request.getTaskId(), testCase.getTestCaseId());
            
            if (process != null && process.isAlive()) {
                process.destroy();
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                LOGGER.info("Process of cancelled task forcefully terminated - Task ID: {}, Test case ID: {}", 
                        request.getTaskId(), testCase.getTestCaseId());
            }
            
            reportTestCaseResult(request, testCase, "BLOCKED", "Test case execution cancelled", 
                    System.currentTimeMillis() - startTime, null, null, "Task cancelled by user", null);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理执行结果
     */
    private void processExecutionResult(TestCaseExecutionRequest request, 
                                       TestCaseExecutionRequest.TestCaseInfo testCase, 
                                       Process process, 
                                       boolean completed, 
                                       Integer timeoutMinutes) throws Exception {
        PythonExecutorUtil.PythonExecutionResult executionResult = handleProcessResult(process, completed, null, testCase, timeoutMinutes, request);
        TestCaseAnalysis analysis = analyzeTestCaseResult(executionResult, testCase);
        
        LOGGER.info("Preparing to report test case execution result - Test case ID: {}, Round: {}, Status: {}, Result: {}, Failure reason: {}", 
                testCase.getTestCaseId(), testCase.getRound(), analysis.getStatus(), analysis.getResult(), analysis.getFailureReason());
        LOGGER.info("Result report URL: {}", request.getResultReportUrl());
        
        reportTestCaseResult(request, testCase, analysis.getStatus(), analysis.getResult(), 
                executionResult.getExecutionTime(), executionResult.getStartTime(), executionResult.getEndTime(), analysis.getFailureReason(), executionResult.getLogFilePath());
    }
    
    /**
     * 处理Python执行异常
     */
    private void handlePythonExecutionException(TestCaseExecutionRequest request, 
                                               TestCaseExecutionRequest.TestCaseInfo testCase, 
                                               Exception e) {
        String errorMessage = e.getMessage();
        String failureReason;
        
        if (errorMessage != null && errorMessage.contains("Cannot run program \"python\"") && errorMessage.contains("No such file or directory")) {
            failureReason = "Python executor unavailable: Python is not installed in the system or not in PATH environment variable";
            LOGGER.error("Python executor unavailable - Test case ID: {}, Test case number: {}, Round: {}, Error: {}", 
                    testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), errorMessage);
        } else {
            failureReason = "Python script execution exception: " + errorMessage;
            LOGGER.error("Python script execution exception - Test case ID: {}, Test case number: {}, Round: {}, Error: {}", 
                    testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound(), errorMessage);
        }
        
        reportTestCaseResult(request, testCase, "BLOCKED", "Test case execution failed", 0L, null, null, failureReason, null);
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
        
        TestCaseResultParser.TestCaseParseResult parseResult = TestCaseResultParser.parseResult(logContent);
        
        if (!"BLOCKED".equals(parseResult.getStatus())) {
            return buildAnalysisFromParseResult(parseResult, result);
        } else {
            return buildAnalysisFromFallbackLogic(status, result, failureReason, logContent, parseResult);
        }
    }
    
    /**
     * 从解析结果构建分析结果
     */
    private TestCaseAnalysis buildAnalysisFromParseResult(TestCaseResultParser.TestCaseParseResult parseResult, String originalResult) {
        String status = parseResult.getStatus();
        String result = parseResult.getResultMessage();
        String failureReason = buildFailureReasonFromParseResult(parseResult);
        result = addPerformanceMetrics(result, parseResult);
        return new TestCaseAnalysis(status, result, failureReason);
    }
    
    /**
     * 从解析结果构建失败原因
     */
    private String buildFailureReasonFromParseResult(TestCaseResultParser.TestCaseParseResult parseResult) {
        if (parseResult.getFailureDetails() != null) {
            return parseResult.getFailureDetails();
        }
        
        if (parseResult.getFailedTests() > 0 || parseResult.getErrorTests() > 0) {
            return String.format("Test statistics: Total=%d, Success=%d, Failed=%d, Error=%d, Success rate=%.1f%%", 
                    parseResult.getTotalTests(), parseResult.getSuccessTests(), 
                    parseResult.getFailedTests(), parseResult.getErrorTests(), parseResult.getSuccessRate());
        }
        
        return null;
    }
    
    /**
     * 添加性能指标信息
     */
    private String addPerformanceMetrics(String result, TestCaseResultParser.TestCaseParseResult parseResult) {
        if (parseResult.getNetworkLatency() != null) {
            result += String.format(" (Network latency: %.2fms)", parseResult.getNetworkLatency());
        }
        if (parseResult.getBandwidth() != null) {
            result += String.format(" (Bandwidth: %.2f%s)", parseResult.getBandwidth(), parseResult.getBandwidthUnit());
        }
        if (parseResult.getSignalStrength() != null) {
            result += String.format(" (Signal strength: %.2fdBm)", parseResult.getSignalStrength());
        }
        return result;
    }
    
    /**
     * 使用降级逻辑构建分析结果
     */
    private TestCaseAnalysis buildAnalysisFromFallbackLogic(String status, String result, String failureReason, 
                                                           String logContent, TestCaseResultParser.TestCaseParseResult parseResult) {
        if ("SUCCESS".equals(status)) {
            return analyzeSuccessStatus(logContent);
        } else if ("FAILED".equals(status)) {
            failureReason = analyzeDetailedFailureReason(logContent, failureReason);
        } else if ("BLOCKED".equals(status)) {
            failureReason = analyzeDetailedFailureReason(logContent, failureReason);
        }
        
        result = addPerformanceMetrics(result, parseResult);
        return new TestCaseAnalysis(status, result, failureReason);
    }
    
    /**
     * 分析成功状态
     */
    private TestCaseAnalysis analyzeSuccessStatus(String logContent) {
        if (logContent.contains("case failed")) {
            String failureReason = "Log analysis found failure information: " + extractFailureDetails(logContent);
            return new TestCaseAnalysis("FAILED", "Test case execution failed", failureReason);
        } else if (logContent.contains("case success")) {
            return new TestCaseAnalysis("SUCCESS", "Test case execution succeeded", null);
        } else {
            String failureReason = "Test case execution blocked: Log does not contain clear success or failure identifier, may be caused by environment issues or script exceptions";
            return new TestCaseAnalysis("BLOCKED", "Test case execution blocked", failureReason);
        }
    }
    
    /**
     * 提取失败详情
     * 
     * @param logContent 日志内容
     * @return 失败详情
     */
    private String extractFailureDetails(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder failureDetails = new StringBuilder();
        
        for (String line : lines) {
            if (line.contains("FAIL") || line.contains("ERROR") || line.contains("失败") || 
                line.contains("AssertionError") || line.contains("Exception")) {
                failureDetails.append(line.trim()).append("; ");
            }
        }
        
        return failureDetails.length() > 0 ? failureDetails.toString() : "Blocked failure reason";
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
            return "Network connection failed: Cannot connect to target server, please check network configuration and server status";
        } else if (logContent.contains("超时") || logContent.contains("timeout")) {
            return "Network request timeout: Server response time is too long, please check network connection and server load";
        } else if (logContent.contains("DNS解析失败") || logContent.contains("Name or service not known")) {
            return "DNS resolution failed: Cannot resolve domain name, please check DNS configuration and network connection";
        } else if (logContent.contains("权限不足") || logContent.contains("Permission denied")) {
            return "Insufficient permissions: Cannot access required resources, please check file permissions and user permission settings";
        } else if (logContent.contains("文件不存在") || logContent.contains("No such file")) {
            return "File does not exist: Cannot find required file or directory, please check file path and file existence";
        } else if (logContent.contains("模块导入失败") || logContent.contains("ImportError")) {
            return "Module import failed: Python dependency package missing, please check Python environment and dependency package installation";
        } else if (logContent.contains("内存不足") || logContent.contains("out of memory")) {
            return "Insufficient memory: System memory is insufficient, cannot execute test case, please check system resources";
        } else if (logContent.contains("磁盘空间不足") || logContent.contains("no space left")) {
            return "Insufficient disk space: System disk space is insufficient, cannot write files, please clean up disk space";
        } else if (logContent.contains("Python执行器不可用")) {
            return "Python executor unavailable: Python is not installed in the system or not in PATH environment variable, please check Python installation";
        } else {
            return originalReason != null ? originalReason : "Test case execution blocked: Unknown reason caused execution failure";
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
        LOGGER.info("Building test case execution result report - Test case ID: {}, Round: {}, Status: {}, Result: {}, Log file: {}", 
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
            LOGGER.info("Setting failure reason - Test case ID: {}, Round: {}, Failure reason: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), report.getFailureReason());
        }
        
        LOGGER.info("Test case execution result report built - Test case ID: {}, Round: {}, Task ID: {}, Executor IP: {}, Log file: {}", 
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
        LOGGER.info("Starting to cancel task execution - Task ID: {}", taskId);
        
        TaskExecutionInfo taskInfo = runningTasks.get(taskId);
        if (taskInfo == null) {
            LOGGER.warn("Task does not exist or has been completed - Task ID: {}", taskId);
            return false;
        }
        
        try {
            LOGGER.info("Starting to terminate all Python processes for task ID: {}", taskId);
            PythonExecutorUtil.terminateAllPythonProcessesByTaskId(taskId);
            
            taskInfo.cancelAllProcesses();
            taskInfo.cancelExecution();
            runningTasks.remove(taskId);
            
            LOGGER.info("Task cancellation succeeded - Task ID: {}", taskId);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Task cancellation failed - Task ID: {}, Error: {}", taskId, e.getMessage(), e);
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
            LOGGER.warn("scripts directory does not exist or is not a directory: {}", scriptsDir);
            return null;
        }
        
        try (Stream<Path> paths = Files.walk(scriptsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(scriptFileName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.error("Error occurred while recursively searching for script file - Directory: {}, File name: {}, Error: {}", 
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
            Path logFilePath = getLogFilePath(request, testCase);
            String logContent = readLogContent(logFilePath);
            ExecutionResultInfo resultInfo = determineExecutionResult(process, completed, logContent, timeoutMinutes, testCase);
            String uploadedLogUrl = uploadLogFileIfNeeded(request, testCase, logFilePath, logFilePath.getFileName().toString());
            
            return buildPythonExecutionResult(resultInfo, logContent, uploadedLogUrl, logFilePath.getFileName().toString());
        } catch (Exception e) {
            LOGGER.error("Error occurred while processing process execution result - Test case ID: {}, Round: {}, Error: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), e.getMessage());
            
            return PythonExecutorUtil.PythonExecutionResult.builder()
                    .status("BLOCKED")
                    .result("Test case execution failed")
                    .executionTime(0L)
                    .startTime(java.time.LocalDateTime.now())
                    .endTime(java.time.LocalDateTime.now())
                    .logContent("")
                    .logFilePath("")
                    .failureReason("Error occurred while processing execution result: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 获取日志文件路径
     */
    private Path getLogFilePath(TestCaseExecutionRequest request, TestCaseExecutionRequest.TestCaseInfo testCase) {
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
        return logsDir.resolve(logFileName);
    }
    
    /**
     * 读取日志内容
     */
    private String readLogContent(Path logFilePath) {
        try {
            if (Files.exists(logFilePath)) {
                return new String(Files.readAllBytes(logFilePath), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to read log content from file: {}, Error: {}", logFilePath, e.getMessage());
        }
        return "";
    }
    
    /**
     * 确定执行结果
     */
    private ExecutionResultInfo determineExecutionResult(Process process, boolean completed, String logContent, 
                                                         Integer timeoutMinutes, TestCaseExecutionRequest.TestCaseInfo testCase) {
        if (!completed) {
            terminateProcessAndChildren(process);
            LOGGER.error("Test case execution timeout - Test case ID: {}, Round: {}, Timeout: {} minutes", 
                    testCase.getTestCaseId(), testCase.getRound(), timeoutMinutes);
            return new ExecutionResultInfo("FAILED", "Test case execution timeout", 
                    "Test case execution timeout: Exceeded configured timeout " + timeoutMinutes + " minutes");
        }
        
        if (process.exitValue() != 0) {
            return determineFailureResult(logContent, process.exitValue());
        }
        
        return determineResultFromOutput(logContent);
    }
    
    /**
     * 确定失败结果
     */
    private ExecutionResultInfo determineFailureResult(String logContent, int exitCode) {
        if (isBlockedByEnvironment(logContent)) {
            String failureReason = "Environment issue caused test case execution failure: " + analyzeFailureReason(logContent, exitCode);
            return new ExecutionResultInfo("BLOCKED", "Test case execution blocked (environment issue)", failureReason);
        } else {
            String failureReason = analyzeFailureReason(logContent, exitCode);
            return new ExecutionResultInfo("FAILED", "Test case execution failed, exit code: " + exitCode, failureReason);
        }
    }
    
    /**
     * 从输出确定结果
     */
    private ExecutionResultInfo determineResultFromOutput(String logContent) {
        TestResultAnalysis analysis = analyzeTestOutput(logContent);
        return new ExecutionResultInfo(analysis.getStatus(), analysis.getResult(), analysis.getFailureReason());
    }
    
    /**
     * 上传日志文件（如果需要）
     */
    private String uploadLogFileIfNeeded(TestCaseExecutionRequest request, TestCaseExecutionRequest.TestCaseInfo testCase, 
                                        Path logFilePath, String logFileName) {
        if (request.getLogReportUrl() == null || request.getLogReportUrl().trim().isEmpty()) {
            LOGGER.info("GoHttpServer address not provided, skipping log file upload - Test case ID: {}, Round: {}", 
                    testCase.getTestCaseId(), testCase.getRound());
            return null;
        }
        
        try {
            // LogUploadDiagnosticTool.diagnoseLogFilePath(logFilePath.toString(), request.getTaskId(), 
            //         testCase.getTestCaseId(), testCase.getRound());
            // LogUploadDiagnosticTool.diagnoseGoHttpServer(request.getLogReportUrl(), request.getTaskId());
            
            if (!Files.exists(logFilePath)) {
                LOGGER.warn("Log file does not exist, skipping upload - Test case ID: {}, Round: {}, File path: {}", 
                        testCase.getTestCaseId(), testCase.getRound(), logFilePath.toString());
                return null;
            }
            
            long fileSize = Files.size(logFilePath);
            if (fileSize == 0) {
                LOGGER.warn("Log file is empty, skipping upload - Test case ID: {}, Round: {}, File path: {}", 
                        testCase.getTestCaseId(), testCase.getRound(), logFilePath.toString());
                return null;
            }
            
            LOGGER.info("Preparing to upload log file - Test case ID: {}, Round: {}, File path: {}, File size: {} bytes", 
                    testCase.getTestCaseId(), testCase.getRound(), logFilePath.toString(), fileSize);
            
            com.caseexecute.util.GoHttpServerClient goHttpServerClient = new com.caseexecute.util.GoHttpServerClient();
            String uploadedLogUrl = goHttpServerClient.uploadLocalFile(logFilePath.toString(), logFileName, request.getLogReportUrl(), request.getTaskId());
            LOGGER.info("Log file upload succeeded - Test case ID: {}, Round: {}, Upload URL: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), uploadedLogUrl);
            return uploadedLogUrl;
        } catch (Exception e) {
            LOGGER.error("Log file upload failed - Test case ID: {}, Round: {}, File path: {}, Error: {}", 
                    testCase.getTestCaseId(), testCase.getRound(), logFilePath.toString(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 构建Python执行结果
     */
    private PythonExecutorUtil.PythonExecutionResult buildPythonExecutionResult(ExecutionResultInfo resultInfo, 
                                                                               String logContent, String uploadedLogUrl, String logFileName) {
        return PythonExecutorUtil.PythonExecutionResult.builder()
                .status(resultInfo.getStatus())
                .result(resultInfo.getResult())
                .executionTime(System.currentTimeMillis() - System.currentTimeMillis())
                .startTime(java.time.LocalDateTime.now())
                .endTime(java.time.LocalDateTime.now())
                .logContent(logContent)
                .logFilePath(uploadedLogUrl != null ? uploadedLogUrl : logFileName)
                .failureReason(resultInfo.getFailureReason())
                .build();
    }
    
    /**
     * 执行结果信息
     */
    private static class ExecutionResultInfo {
        private final String status;
        private final String result;
        private final String failureReason;
        
        public ExecutionResultInfo(String status, String result, String failureReason) {
            this.status = status;
            this.result = result;
            this.failureReason = failureReason;
        }
        
        public String getStatus() { return status; }
        public String getResult() { return result; }
        public String getFailureReason() { return failureReason; }
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
                LOGGER.info("Process forcefully terminated");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to terminate process: {}", e.getMessage());
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
            return "Process exited abnormally, exit code: " + exitCode;
        }
        
        String[] lines = logContent.split("\n");
        StringBuilder errorInfo = new StringBuilder();
        int startIndex = Math.max(0, lines.length - 5);
        
        for (int i = startIndex; i < lines.length; i++) {
            if (lines[i].trim().length() > 0) {
                errorInfo.append(lines[i].trim()).append("; ");
            }
        }
        
        return errorInfo.length() > 0 ? errorInfo.toString() : "Process exited abnormally, exit code: " + exitCode;
    }
    
    /**
     * 分析测试输出
     */
    private TestResultAnalysis analyzeTestOutput(String logContent) {
        if (logContent == null) {
            return new TestResultAnalysis("BLOCKED", "Cannot read execution log", "Log content is empty");
        }
        
        String lowerContent = logContent.toLowerCase();
        
        if (lowerContent.contains("case success")) {
            return new TestResultAnalysis("SUCCESS", "Test case execution succeeded", null);
        } else if (lowerContent.contains("case failed")) {
            return new TestResultAnalysis("FAILED", "Test case execution failed", extractFailureDetails(logContent));
        } else {
            return new TestResultAnalysis("BLOCKED", "Cannot determine execution result", "Log content cannot be parsed");
        }
    }
    

    
    /**
     * 记录任务上下文信息（UE信息和采集策略信息）
     * 
     * @param request 用例执行任务请求
     */
    private void logTaskContextInfo(TestCaseExecutionRequest request) {
        LOGGER.info("=== Task Context Information ===");
        
        if (request.getUeList() != null && !request.getUeList().isEmpty()) {
            LOGGER.info("UE device information associated with executor:");
            LOGGER.info("  - UE device count: {}", request.getUeList().size());
            for (TestCaseExecutionRequest.UeInfo ue : request.getUeList()) {
                LOGGER.info("  - UE ID: {}, Name: {}, Purpose: {}, Network type: {}, Vendor: {}, Port: {}, Status: {}", 
                        ue.getUeId(), ue.getName(), ue.getPurpose(), 
                        ue.getNetworkTypeName(), ue.getVendor(), ue.getPort(), ue.getStatus());
                if (ue.getDescription() != null && !ue.getDescription().trim().isEmpty()) {
                    LOGGER.info("    - Description: {}", ue.getDescription());
                }
            }
        } else {
            LOGGER.warn("Executor is not associated with UE device information");
        }
        
        if (request.getCollectStrategyInfo() != null) {
            TestCaseExecutionRequest.CollectStrategyInfo strategy = request.getCollectStrategyInfo();
            LOGGER.info("Collection strategy information:");
            LOGGER.info("  - Strategy ID: {}", strategy.getId());
            LOGGER.info("  - Strategy name: {}", strategy.getName());
            LOGGER.info("  - Collection count: {}", strategy.getCollectCount());
            LOGGER.info("  - Business category: {}", strategy.getBusinessCategory());
            LOGGER.info("  - APP: {}", strategy.getApp());
            LOGGER.info("  - APPEN: {}", strategy.getAppEn());
            LOGGER.info("  - Intent: {}", strategy.getIntent());
            LOGGER.info("  - Strategy status: {}", strategy.getStatus());
            if (strategy.getCustomParams() != null && !strategy.getCustomParams().trim().isEmpty()) {
                LOGGER.info("  - Custom parameters: {}", strategy.getCustomParams());
            }
            if (strategy.getDescription() != null && !strategy.getDescription().trim().isEmpty()) {
                LOGGER.info("  - Strategy description: {}", strategy.getDescription());
            }
        } else {
            LOGGER.warn("Collection strategy information not provided");
        }
        
        LOGGER.info("=== Task Context Information Logging Completed ===");
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
