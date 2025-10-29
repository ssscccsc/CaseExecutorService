package com.caseexecute.util;

import com.caseexecute.config.FileStorageConfig;
import com.caseexecute.dto.TestCaseExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Python脚本执行工具类
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@Component
public class PythonExecutorUtil implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    private static FileStorageConfig fileStorageConfig;
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        PythonExecutorUtil.applicationContext = applicationContext;
        // 从ApplicationContext中获取FileStorageConfig
        PythonExecutorUtil.fileStorageConfig = applicationContext.getBean(FileStorageConfig.class);
        log.info("PythonExecutorUtil - FileStorageConfig注入成功 - 根目录: {}", fileStorageConfig.getRootDirectory());
    }
    
    /**
     * 获取FileStorageConfig实例
     */
    private static FileStorageConfig getFileStorageConfig() {
        if (fileStorageConfig == null && applicationContext != null) {
            fileStorageConfig = applicationContext.getBean(FileStorageConfig.class);
            log.info("PythonExecutorUtil - 重新获取FileStorageConfig - 根目录: {}", fileStorageConfig.getRootDirectory());
        }
        return fileStorageConfig;
    }
    
    /**
     * 设置文件存储配置
     */
    public static void setFileStorageConfig(FileStorageConfig config) {
        PythonExecutorUtil.fileStorageConfig = config;
    }
    
    /**
     * 获取GoHttpServerClient实例
     */
    private static GoHttpServerClient getGoHttpServerClient() {
        if (applicationContext != null) {
            return applicationContext.getBean(GoHttpServerClient.class);
        }
        // 如果无法获取Spring bean，则创建新实例（不推荐，因为无法获取配置）
        log.warn("无法获取Spring ApplicationContext，创建新的GoHttpServerClient实例");
        return new GoHttpServerClient();
    }
    

    

    /**
     * 启动Python脚本进程（不等待完成）
     * 
     * @param scriptPath 脚本路径
     * @param testCaseId 用例ID
     * @param testCaseNumber 用例编号
     * @param round 轮次
     * @param goHttpServerUrl gohttpserver地址（可选）
     * @param taskId 任务ID（可选）
     * @param executorIp 执行机IP地址
     * @param collectStrategyInfo 采集策略信息（可选）
     * @param ueList UE列表信息（可选）
     * @param taskCustomParams 采集任务的自定义参数（可选）
     * @return 进程对象
     */
    public static Process startPythonProcess(Path scriptPath, Long testCaseId, String testCaseNumber, Integer round, String goHttpServerUrl, String taskId, String executorIp, TestCaseExecutionRequest.CollectStrategyInfo collectStrategyInfo, List<TestCaseExecutionRequest.UeInfo> ueList, String taskCustomParams) throws Exception {
        log.info("启动Python脚本进程 - 脚本路径: {}, 用例ID: {}, 轮次: {}", 
                scriptPath, testCaseId, round);
        
        // 检查脚本文件是否存在
        if (!Files.exists(scriptPath)) {
            throw new RuntimeException("Python脚本文件不存在: " + scriptPath);
        }
        
        // 创建日志目录 - 使用临时目录或配置的目录
        String rootDir = getFileStorageConfig() != null ? getFileStorageConfig().getRootDirectory() : System.getProperty("java.io.tmpdir");
        Path rootDirectory = Paths.get(rootDir);
        Path taskDir = rootDirectory.resolve(taskId);
        Path logsDir = taskDir.resolve("logs");
        
        // 确保目录存在
        if (!Files.exists(rootDirectory)) {
            Files.createDirectories(rootDirectory);
        }
        if (!Files.exists(taskDir)) {
            Files.createDirectories(taskDir);
        }
        if (!Files.exists(logsDir)) {
            Files.createDirectories(logsDir);
        }
        
        // 创建日志文件路径 - 使用用例编号_轮次.log格式
        String logFileName;
        if (testCaseNumber != null && !testCaseNumber.trim().isEmpty()) {
            logFileName = String.format("%s_%d.log", testCaseNumber, round);
        } else {
            // 如果没有用例编号，则使用用例ID
            logFileName = String.format("%d_%d.log", testCaseId, round);
        }
        Path logFilePath = logsDir.resolve(logFileName);
        
        // 执行Python脚本 - 直接使用系统python命令
        String pythonCommand = "python";
        log.info("使用系统Python执行器: {}", pythonCommand);
        
        log.info("执行机IP地址: {}", executorIp);
        
        // 记录采集策略信息
        if (collectStrategyInfo != null) {
            log.info("采集策略信息 - 业务大类: {}, APP: {}, APPEN: {}, 意图: {}", 
                    collectStrategyInfo.getBusinessCategory(), collectStrategyInfo.getApp(), 
                    collectStrategyInfo.getAppEn(), collectStrategyInfo.getIntent());
        } else {
            log.warn("未提供采集策略信息");
        }
        
        // 记录UE信息
        if (ueList != null && !ueList.isEmpty()) {
            log.info("UE信息 - UE数量: {}", ueList.size());
            for (TestCaseExecutionRequest.UeInfo ue : ueList) {
                log.info("  - UE ID: {}, 名称: {}, 用途: {}, 网络类型: {}, 厂商: {}, 状态: {}", 
                        ue.getUeId(), ue.getName(), ue.getPurpose(), 
                        ue.getNetworkTypeName(), ue.getVendor(), ue.getStatus());
            }
        } else {
            log.warn("未提供UE信息");
        }
        
        // 构建命令参数列表
        List<String> commandArgs = new ArrayList<>();
        commandArgs.add(pythonCommand);
        commandArgs.add(scriptPath.toString());
        commandArgs.add("--ip");
        commandArgs.add("\"" + executorIp + "\"");
        
        // 添加采集策略参数
        if (collectStrategyInfo != null) {
            if (collectStrategyInfo.getBusinessCategory() != null && !collectStrategyInfo.getBusinessCategory().trim().isEmpty()) {
                commandArgs.add("--category");
                commandArgs.add("\"" + collectStrategyInfo.getBusinessCategory() + "\"");
            }
            if (collectStrategyInfo.getAppEn() != null && !collectStrategyInfo.getAppEn().trim().isEmpty()) {
                commandArgs.add("--app");
                commandArgs.add("\"" + collectStrategyInfo.getAppEn() + "\"");
            }
            if (collectStrategyInfo.getIntent() != null && !collectStrategyInfo.getIntent().trim().isEmpty()) {
                commandArgs.add("--dataset_round");
                commandArgs.add("\"" + collectStrategyInfo.getIntent() + "\"");
            }
            
            // 添加采集任务的自定义参数
            if (taskCustomParams != null && !taskCustomParams.trim().isEmpty()) {
                try {
                    // 解析自定义参数字符串，支持JSON格式
                    Map<String, String> customParamsMap = parseCustomParams(taskCustomParams);
                    for (Map.Entry<String, String> entry : customParamsMap.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (key != null && !key.trim().isEmpty() && value != null) {
                            commandArgs.add("--" + key);
                            commandArgs.add("\"" + value + "\"");
                            log.info("添加采集任务自定义参数: --{} \"{}\"", key, value);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析采集任务自定义参数失败: {}, 错误: {}", taskCustomParams, e.getMessage());
                }
            }
        }
        
        // 添加UE列表参数
        if (ueList != null && !ueList.isEmpty()) {
            try {
                // 将UE列表转换为JSON字符串
                ObjectMapper objectMapper = new ObjectMapper();
                String ueListJson = objectMapper.writeValueAsString(ueList);
                
                // 对JSON字符串进行转义处理，避免命令行解析错误
                String escapedUeListJson = escapeJsonForCommandLine(ueListJson);
                
                commandArgs.add("--uelist");
                commandArgs.add("\"" + escapedUeListJson + "\"");
                log.info("添加UE列表参数: --uelist \"{}\"", escapedUeListJson);
            } catch (Exception e) {
                log.warn("序列化UE列表失败: {}, 错误: {}", ueList, e.getMessage());
            }
        }
        
        // 构建命令参数：python3 script_path --ip executor_ip --category business_category --app app_value --dataset_round intent --key value --uelist ue_json
        ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
        processBuilder.redirectErrorStream(true);
        
        // 打印完整的Python命令
        StringBuilder commandString = new StringBuilder();
        for (String arg : commandArgs) {
            if (commandString.length() > 0) {
                commandString.append(" ");
            }
            // 如果参数包含空格，用引号包围
            if (arg.contains(" ")) {
                commandString.append("\"").append(arg).append("\"");
            } else {
                commandString.append(arg);
            }
        }
        log.info("执行Python命令: {}", commandString.toString());
        
        // 设置环境变量以确保正确的字符编码
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("LANG", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_ALL", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_CTYPE", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_MESSAGES", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_MONETARY", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_NUMERIC", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_TIME", "zh_CN.UTF-8");
        processBuilder.environment().put("LC_COLLATE", "zh_CN.UTF-8");
        
        Process process = processBuilder.start();
        log.info("Python脚本进程已启动 - 用例ID: {}, 轮次: {}, 日志文件: {}", 
                testCaseId, round, logFilePath);
        
        // 启动异步线程来读取Python进程的输出，同时输出到控制台和日志文件
        startOutputReader(process, logFilePath, testCaseId, round);
        
        return process;
    }
    
    /**
     * 启动输出读取器，同时将Python进程的输出输出到控制台和日志文件
     * 
     * @param process Python进程
     * @param logFilePath 日志文件路径
     * @param testCaseId 用例ID
     * @param round 轮次
     */
    private static void startOutputReader(Process process, Path logFilePath, Long testCaseId, Integer round) {
        executorService.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(logFilePath), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // 输出到控制台
                    System.out.println(String.format("[Python-用例%d-轮次%d] %s", testCaseId, round, line));
                    
                    // 输出到日志文件
                    fileWriter.write(line);
                    fileWriter.newLine();
                    fileWriter.flush();
                    
                    // 输出到服务日志
                    log.info("[Python-用例{}-轮次{}] {}", testCaseId, round, line);
                }
                
                log.info("Python进程输出读取完成 - 用例ID: {}, 轮次: {}", testCaseId, round);
                
            } catch (IOException e) {
                log.error("读取Python进程输出时发生错误 - 用例ID: {}, 轮次: {}, 错误: {}", testCaseId, round, e.getMessage(), e);
            }
        });
    }
    
    /**
     * 解析自定义参数字符串
     * 支持JSON格式和key=value格式
     * 
     * @param customParams 自定义参数字符串
     * @return 解析后的参数Map
     */
    private static Map<String, String> parseCustomParams(String customParams) {
        Map<String, String> paramsMap = new HashMap<>();
        
        if (customParams == null || customParams.trim().isEmpty()) {
            return paramsMap;
        }
        
        String trimmedParams = customParams.trim();
        
        // 解析JSON数组格式
        if (trimmedParams.startsWith("[") && trimmedParams.endsWith("]")) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonArray = objectMapper.readTree(trimmedParams);
                
                if (jsonArray.isArray()) {
                    for (JsonNode item : jsonArray) {
                        if (item.isObject()) {
                            // 处理{"key":"a","value":"高清"}格式的参数
                            String key = null;
                            String value = null;
                            
                            if (item.has("key")) {
                                key = item.get("key").asText();
                            }
                            if (item.has("value")) {
                                value = item.get("value").asText();
                            }
                            
                            // 如果key和value都存在且不为空，则添加到参数映射中
                            if (key != null && !key.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
                                paramsMap.put(key.trim(), value.trim());
                                log.debug("解析到参数: key={}, value={}", key, value);
                            }
                        } else if (item.isTextual()) {
                            // 处理字符串格式的参数，尝试解析为key=value
                            String itemStr = item.asText();
                            String[] keyValue = itemStr.split("=", 2);
                            if (keyValue.length == 2) {
                                paramsMap.put(keyValue[0].trim(), keyValue[1].trim());
                            }
                        }
                    }
                    log.info("成功解析JSON数组格式自定义参数: {}, 解析出{}个参数", customParams, paramsMap.size());
                    return paramsMap;
                }
            } catch (Exception e) {
                log.warn("JSON数组格式解析失败: {}, 错误: {}", customParams, e.getMessage());
            }
        } else {
            log.warn("自定义参数不是JSON数组格式，跳过解析: {}", customParams);
        }
        
        return paramsMap;
    }
    
    /**
     * 强制终止进程及其子进程
     * 
     * @param process 要终止的进程
     */
    private static void terminateProcessAndChildren(Process process) {
        if (process == null) {
            return;
        }
        
        try {
            log.info("开始终止进程及其子进程");
            
            String osName = System.getProperty("os.name").toLowerCase();
            
            if (osName.contains("windows")) {
                // Windows系统
                terminateProcessTreeWindows(process);
            } else if (osName.contains("mac") || osName.contains("linux")) {
                // macOS/Linux系统
                terminateProcessTreeUnix(process);
            } else {
                // 其他系统，使用Java API
                terminateProcessJava(process);
            }
            
        } catch (Exception e) {
            log.error("强制终止进程失败: {}", e.getMessage());
            // 降级到Java API
            terminateProcessJava(process);
        }
    }
    
    /**
     * 在Windows系统上终止进程树
     * 
     * @param process 进程对象
     */
    private static void terminateProcessTreeWindows(Process process) {
        try {
            // 获取进程ID
            long pid = getProcessId(process);
            if (pid > 0) {
                log.info("终止进程树，主进程PID: {}", pid);
                
                // 使用taskkill命令终止整个进程树
                ProcessBuilder taskkillBuilder = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid));
                Process taskkillProcess = taskkillBuilder.start();
                taskkillProcess.waitFor(5, TimeUnit.SECONDS);
                
                log.info("已使用taskkill命令终止进程树");
            } else {
                // 如果无法获取PID，使用Java API
                terminateProcessJava(process);
            }
        } catch (Exception e) {
            log.error("终止进程树失败 - 错误: {}", e.getMessage());
            // 降级到Java API
            terminateProcessJava(process);
        }
    }
    
    /**
     * 在Unix系统上终止进程树
     * 
     * @param process 进程对象
     */
    private static void terminateProcessTreeUnix(Process process) {
        try {
            // 获取进程ID
            long pid = getProcessId(process);
            if (pid > 0) {
                log.info("终止进程树，主进程PID: {}", pid);
                
                // 使用pkill命令终止整个进程树
                ProcessBuilder pkillBuilder = new ProcessBuilder("pkill", "-P", String.valueOf(pid));
                Process pkillProcess = pkillBuilder.start();
                pkillProcess.waitFor(5, TimeUnit.SECONDS);
                
                // 使用kill命令终止主进程
                ProcessBuilder killBuilder = new ProcessBuilder("kill", "-9", String.valueOf(pid));
                Process killProcess = killBuilder.start();
                killProcess.waitFor(5, TimeUnit.SECONDS);
                
                log.info("已使用系统命令终止进程树");
            } else {
                // 如果无法获取PID，使用Java API
                terminateProcessJava(process);
            }
        } catch (Exception e) {
            log.error("终止进程树失败 - 错误: {}", e.getMessage());
            // 降级到Java API
            terminateProcessJava(process);
        }
    }
    
    /**
     * 获取进程ID（Java 8兼容方法）
     * 
     * @param process 进程对象
     * @return 进程ID，如果无法获取则返回-1
     */
    private static long getProcessId(Process process) {
        try {
            // 在Java 8中，我们无法直接获取进程ID
            // 但我们可以通过反射尝试获取
            Class<?> processClass = process.getClass();
            String className = processClass.getName();
            
            if (className.equals("java.lang.UNIXProcess")) {
                // Unix/Linux/macOS系统
                Field pidField = processClass.getDeclaredField("pid");
                pidField.setAccessible(true);
                return pidField.getLong(process);
            } else if (className.equals("java.lang.ProcessImpl")) {
                // Windows系统
                Field pidField = processClass.getDeclaredField("handle");
                pidField.setAccessible(true);
                long handle = pidField.getLong(process);
                // 在Windows上，handle通常就是进程ID
                return handle;
            }
        } catch (Exception e) {
            log.debug("无法获取进程ID: {}", e.getMessage());
        }
        return -1;
    }
    
    /**
     * 使用Java API终止进程
     * 
     * @param process 要终止的进程
     */
    private static void terminateProcessJava(Process process) {
        try {
            // 首先尝试优雅终止
            process.destroy();
            
            // 等待5秒
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                // 如果还在运行，强制终止
                process.destroyForcibly();
                
                // 再等待5秒
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("进程可能仍在运行，无法完全终止");
                }
            }
            
            log.info("已终止进程");
        } catch (Exception e) {
            log.error("使用Java API终止进程失败: {}", e.getMessage());
        }
    }
    
    /**
     * 根据任务ID终止所有相关的Python进程
     * 
     * @param taskId 任务ID
     */
    public static void terminateAllPythonProcessesByTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            log.warn("任务ID为空，无法终止Python进程");
            return;
        }
        
        try {
            log.info("开始终止任务ID为 {} 的所有Python进程", taskId);
            
            String osName = System.getProperty("os.name").toLowerCase();
            
            if (osName.contains("windows")) {
                terminatePythonProcessesByTaskIdWindows(taskId);
            } else if (osName.contains("mac") || osName.contains("linux")) {
                terminatePythonProcessesByTaskIdUnix(taskId);
            } else {
                log.warn("当前系统不支持按任务ID终止Python进程");
            }
            
        } catch (Exception e) {
            log.error("终止任务ID为 {} 的Python进程失败: {}", taskId, e.getMessage());
        }
    }
    
    /**
     * 在Windows系统上根据任务ID终止Python进程
     * 
     * @param taskId 任务ID
     */
    private static void terminatePythonProcessesByTaskIdWindows(String taskId) {
        try {
            log.info("开始查找任务ID为 {} 的所有相关进程", taskId);
            
            // 第一步：查找直接包含任务ID的Python进程
            Set<Long> pythonPids = new HashSet<>();
            Set<Long> allRelatedPids = new HashSet<>();
            
            // 使用wmic命令获取所有Python进程的详细信息，包括命令行参数
            ProcessBuilder wmicBuilder = new ProcessBuilder("wmic", "process", "where", "name='python.exe'", "get", "ProcessId,CommandLine", "/format:csv");
            Process wmicProcess = wmicBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(wmicProcess.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                boolean firstLine = true;
                
                while ((line = reader.readLine()) != null) {
                    // 跳过标题行
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    
                    // 跳过空行
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    
                    // 解析CSV格式的进程信息：Node,CommandLine,ProcessId
                    // 由于CommandLine可能包含逗号，需要特殊处理
                    String[] parts = parseWmicCsvLine(line);
                    if (parts.length >= 3) {
                        String node = parts[0].trim();
                        String commandLine = parts[1].trim();
                        String processId = parts[2].trim();
                        
                        // 检查命令行是否包含任务ID
                        if (commandLine.contains(taskId)) {
                            try {
                                long pid = Long.parseLong(processId);
                                pythonPids.add(pid);
                                allRelatedPids.add(pid);
                                log.info("找到包含任务ID的Python进程，PID: {}, 命令行: {}", processId, commandLine);
                                
                            } catch (NumberFormatException e) {
                                log.warn("无法解析进程ID: {}", processId);
                            }
                        }
                    }
                }
            }
            
            // 设置较短的超时时间，因为wmic命令通常很快完成
            boolean completed = wmicProcess.waitFor(3, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("wmic命令执行超时，强制终止进程");
                wmicProcess.destroyForcibly();
            }
            
            // 第二步：查找这些Python进程的子进程
            for (Long pythonPid : pythonPids) {
                findAndAddChildProcesses(pythonPid, allRelatedPids);
            }
            
            // 第三步：终止所有相关进程
            for (Long pid : allRelatedPids) {
                try {
                    log.info("终止进程，PID: {}", pid);
                    terminateProcessTreeByPidWindows(pid);
                } catch (Exception e) {
                    log.warn("终止进程失败，PID: {} - 错误: {}", pid, e.getMessage());
                }
            }
            
            log.info("已终止任务ID为 {} 的所有相关进程，共 {} 个进程", taskId, allRelatedPids.size());
            
        } catch (Exception e) {
            log.error("在Windows系统上终止Python进程失败: {}", e.getMessage());
        }
    }
    
    /**
     * 解析wmic命令的CSV输出，处理CommandLine字段中可能包含的逗号
     * 
     * @param line CSV格式的行
     * @return 解析后的字段数组
     */
    private static String[] parseWmicCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                // 遇到逗号且不在引号内，表示字段结束
                fields.add(currentField.toString());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        
        // 添加最后一个字段
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
    

    
    /**
     * 在Unix系统上根据任务ID终止Python进程
     * 
     * @param taskId 任务ID
     */
    private static void terminatePythonProcessesByTaskIdUnix(String taskId) {
        try {
            // 查找包含任务ID的Python进程
            ProcessBuilder psBuilder = new ProcessBuilder("ps", "aux");
            Process psProcess = psBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(psProcess.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // 查找包含python3和任务ID的进程
                    if (line.contains("python3") && line.contains(taskId)) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 1) {
                            String pid = parts[1];
                            try {
                                long processId = Long.parseLong(pid);
                                log.info("找到相关Python进程，PID: {}, 命令行: {}", pid, line);
                                
                                // 终止进程及其子进程
                                terminateProcessTreeByPid(processId);
                                
                            } catch (NumberFormatException e) {
                                log.warn("无法解析进程ID: {}", pid);
                            }
                        }
                    }
                }
            }
            
            psProcess.waitFor(10, TimeUnit.SECONDS);
            log.info("已终止任务ID为 {} 的所有Python进程", taskId);
            
        } catch (Exception e) {
            log.error("在Unix系统上终止Python进程失败: {}", e.getMessage());
        }
    }
    
    /**
     * 查找并添加子进程到集合中（Windows系统）
     * 
     * @param parentPid 父进程ID
     * @param allPids 所有相关进程ID集合
     */
    private static void findAndAddChildProcesses(Long parentPid, Set<Long> allPids) {
        try {
            // 使用wmic命令查找子进程
            ProcessBuilder wmicBuilder = new ProcessBuilder("wmic", "process", "where", 
                    "ParentProcessId=" + parentPid, "get", "ProcessId", "/format:csv");
            Process wmicProcess = wmicBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(wmicProcess.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("ProcessId") && !line.equals("Node,ProcessId")) {
                        String[] parts = line.split(",");
                        if (parts.length > 1) {
                            try {
                                long childPid = Long.parseLong(parts[1].trim());
                                if (childPid != parentPid) {
                                    allPids.add(childPid);
                                    log.info("找到子进程，父进程PID: {}, 子进程PID: {}", parentPid, childPid);
                                    
                                    // 递归查找子进程的子进程
                                    findAndAddChildProcesses(childPid, allPids);
                                }
                            } catch (NumberFormatException e) {
                                log.warn("无法解析子进程ID: {}", parts[1]);
                            }
                        }
                    }
                }
            }
            
            wmicProcess.waitFor(5, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.warn("查找子进程失败，父进程PID: {} - 错误: {}", parentPid, e.getMessage());
        }
    }
    
    /**
     * 根据进程ID终止进程树（Windows系统）
     * 
     * @param pid 进程ID
     */
    private static void terminateProcessTreeByPidWindows(long pid) {
        try {
            log.info("终止进程树（Windows），PID: {}", pid);
            
            // 使用taskkill命令终止进程树
            ProcessBuilder taskkillBuilder = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid));
            Process taskkillProcess = taskkillBuilder.start();
            taskkillProcess.waitFor(5, TimeUnit.SECONDS);
            
            log.info("已终止进程树（Windows），PID: {}", pid);
            
        } catch (Exception e) {
            log.error("终止进程树失败（Windows），PID: {} - 错误: {}", pid, e.getMessage());
        }
    }
    
    /**
     * 根据进程ID终止进程树（Unix系统）
     * 
     * @param pid 进程ID
     */
    private static void terminateProcessTreeByPid(long pid) {
        try {
            log.info("终止进程树（Unix），PID: {}", pid);
            
            // 首先终止所有子进程
            ProcessBuilder pkillBuilder = new ProcessBuilder("pkill", "-P", String.valueOf(pid));
            Process pkillProcess = pkillBuilder.start();
            pkillProcess.waitFor(5, TimeUnit.SECONDS);
            
            // 然后终止主进程
            ProcessBuilder killBuilder = new ProcessBuilder("kill", "-9", String.valueOf(pid));
            Process killProcess = killBuilder.start();
            killProcess.waitFor(5, TimeUnit.SECONDS);
            
            log.info("已终止进程树（Unix），PID: {}", pid);
            
        } catch (Exception e) {
            log.error("终止进程树失败（Unix），PID: {} - 错误: {}", pid, e.getMessage());
        }
    }
    
    /**
     * 根据脚本路径终止所有相关的Python进程
     * 
     * @param scriptPath 脚本路径
     */
    public static void terminateAllPythonProcessesByScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.trim().isEmpty()) {
            log.warn("脚本路径为空，无法终止Python进程");
            return;
        }
        
        try {
            log.info("开始终止脚本路径为 {} 的所有Python进程", scriptPath);
            
            String osName = System.getProperty("os.name").toLowerCase();
            
            if (osName.contains("windows")) {
                terminatePythonProcessesByScriptPathWindows(scriptPath);
            } else if (osName.contains("mac") || osName.contains("linux")) {
                terminatePythonProcessesByScriptPathUnix(scriptPath);
            } else {
                log.warn("当前系统不支持按脚本路径终止Python进程");
            }
            
        } catch (Exception e) {
            log.error("终止脚本路径为 {} 的Python进程失败: {}", scriptPath, e.getMessage());
        }
    }
    
    /**
     * 在Windows系统上根据脚本路径终止Python进程
     * 
     * @param scriptPath 脚本路径
     */
    private static void terminatePythonProcessesByScriptPathWindows(String scriptPath) {
        try {
            // 获取脚本文件名
            String scriptName = Paths.get(scriptPath).getFileName().toString();
            log.info("开始查找脚本 {} 的所有相关进程", scriptName);
            
            // 第一步：查找直接包含脚本名称的Python进程
            Set<Long> pythonPids = new HashSet<>();
            Set<Long> allRelatedPids = new HashSet<>();
            
            // 查找包含脚本名称的Python进程
            ProcessBuilder tasklistBuilder = new ProcessBuilder("tasklist", "/FO", "CSV", "/V");
            Process tasklistProcess = tasklistBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(tasklistProcess.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // 查找包含python和脚本名称的进程
                    if (line.contains("python") && line.contains(scriptName)) {
                        // 解析CSV格式的进程信息
                        String[] parts = line.split(",");
                        if (parts.length > 1) {
                            String processName = parts[0].replace("\"", "");
                            String pidStr = parts[1].replace("\"", "");
                            
                            try {
                                long processId = Long.parseLong(pidStr);
                                pythonPids.add(processId);
                                allRelatedPids.add(processId);
                                log.info("找到Python主进程，PID: {}, 进程名: {}, 脚本: {}", pidStr, processName, scriptName);
                                
                            } catch (NumberFormatException e) {
                                log.warn("无法解析进程ID: {}", pidStr);
                            }
                        }
                    }
                }
            }
            
            tasklistProcess.waitFor(10, TimeUnit.SECONDS);
            
            // 第二步：查找这些Python进程的子进程
            for (Long pythonPid : pythonPids) {
                findAndAddChildProcesses(pythonPid, allRelatedPids);
            }
            
            // 第三步：终止所有相关进程
            for (Long pid : allRelatedPids) {
                try {
                    log.info("终止进程，PID: {}", pid);
                    terminateProcessTreeByPidWindows(pid);
                } catch (Exception e) {
                    log.warn("终止进程失败，PID: {} - 错误: {}", pid, e.getMessage());
                }
            }
            
            log.info("已终止脚本 {} 的所有相关进程，共 {} 个进程", scriptName, allRelatedPids.size());
            
        } catch (Exception e) {
            log.error("在Windows系统上终止Python进程失败: {}", e.getMessage());
        }
    }
    
    /**
     * 在Unix系统上根据脚本路径终止Python进程
     * 
     * @param scriptPath 脚本路径
     */
    private static void terminatePythonProcessesByScriptPathUnix(String scriptPath) {
        try {
            // 获取脚本文件名
            String scriptName = Paths.get(scriptPath).getFileName().toString();
            
            // 查找包含脚本名称的Python进程
            ProcessBuilder psBuilder = new ProcessBuilder("ps", "aux");
            Process psProcess = psBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(psProcess.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // 查找包含python3和脚本名称的进程
                    if (line.contains("python3") && line.contains(scriptName)) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 1) {
                            String pid = parts[1];
                            try {
                                long processId = Long.parseLong(pid);
                                log.info("找到相关Python进程，PID: {}, 命令行: {}", pid, line);
                                
                                // 终止进程及其子进程
                                terminateProcessTreeByPid(processId);
                                
                            } catch (NumberFormatException e) {
                                log.warn("无法解析进程ID: {}", pid);
                            }
                        }
                    }
                }
            }
            
            psProcess.waitFor(10, TimeUnit.SECONDS);
            log.info("已终止脚本路径为 {} 的所有Python进程", scriptPath);
            
        } catch (Exception e) {
            log.error("在Unix系统上终止Python进程失败: {}", e.getMessage());
        }
    }
    
    /**
     * 分析失败原因
     * 
     * @param logContent 日志内容
     * @param exitCode 进程退出码
     * @return 失败原因
     */
    private static String analyzeFailureReason(String logContent, int exitCode) {
        // 检查具体的环境问题
        if (logContent.contains("ImportError") || logContent.contains("ModuleNotFoundError")) {
            return "Python模块导入失败: 缺少必要的依赖包，请检查Python环境和依赖安装";
        } else if (logContent.contains("Permission denied") || logContent.contains("access denied")) {
            return "权限不足: 无法访问文件或目录，请检查文件权限设置";
        } else if (logContent.contains("No such file") || logContent.contains("file not found")) {
            return "文件不存在: 无法找到所需的文件或目录，请检查文件路径";
        } else if (logContent.contains("Connection refused") || logContent.contains("network is unreachable")) {
            return "网络连接失败: 无法连接到目标服务器，请检查网络配置";
        } else if (logContent.contains("Connection timeout") || logContent.contains("dns resolution failed")) {
            return "网络超时: DNS解析失败或连接超时，请检查网络连接";
        } else if (logContent.contains("out of memory") || logContent.contains("memory error")) {
            return "内存不足: 系统内存不足，无法执行用例";
        } else if (logContent.contains("disk space") || logContent.contains("no space left")) {
            return "磁盘空间不足: 系统磁盘空间不足，无法写入文件";
        } else if (logContent.contains("FAIL") || logContent.contains("ERROR") || logContent.contains("失败")) {
            return "用例执行失败: 日志中包含FAIL、ERROR或失败信息";
        } else if (logContent.contains("PASS") || logContent.contains("SUCCESS") || logContent.contains("成功")) {
            return "用例执行成功，但退出码非0: 可能存在其他问题，退出码: " + exitCode;
        } else {
            return "用例执行被阻塞: 未知原因导致执行失败，退出码: " + exitCode;
        }
    }
    
    /**
     * 分析测试输出结果
     * 
     * @param logContent 日志内容
     * @return 测试结果分析
     */
    private static TestResultAnalysis analyzeTestOutput(String logContent) {
        String status = "SUCCESS";
        String result = "用例执行成功";
        String failureReason = null;

        if (logContent.contains("FAIL") || logContent.contains("ERROR") || logContent.contains("失败")) {
            status = "FAILED";
            result = "用例执行失败";
            failureReason = "用例执行失败，日志中包含FAIL、ERROR或失败";
        } else if (logContent.contains("PASS") || logContent.contains("SUCCESS") || logContent.contains("成功")) {
            status = "SUCCESS";
            result = "用例执行成功";
            failureReason = null; // 成功时没有失败原因
        } else {
            status = "BLOCKED";
            result = "用例执行被阻塞";
            failureReason = "用例执行被阻塞: 日志中未包含明确的成功或失败标识，可能由于环境问题或脚本异常导致";
        }
        return new TestResultAnalysis(status, result, failureReason);
    }

    /**
     * Python执行结果
     */
    public static class PythonExecutionResult {
        private String status;
        private String result;
        private Long executionTime;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String logContent;
        private String logFilePath;
        private String failureReason;
        
        // 使用Builder模式
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private PythonExecutionResult executionResult = new PythonExecutionResult();
            
            public Builder status(String status) {
                executionResult.status = status;
                return this;
            }
            
            public Builder result(String resultValue) {
                executionResult.result = resultValue;
                return this;
            }
            
            public Builder executionTime(Long executionTime) {
                executionResult.executionTime = executionTime;
                return this;
            }
            
            public Builder startTime(LocalDateTime startTime) {
                executionResult.startTime = startTime;
                return this;
            }
            
            public Builder endTime(LocalDateTime endTime) {
                executionResult.endTime = endTime;
                return this;
            }
            
            public Builder logContent(String logContent) {
                executionResult.logContent = logContent;
                return this;
            }
            
            public Builder logFilePath(String logFilePath) {
                executionResult.logFilePath = logFilePath;
                return this;
            }

            public Builder failureReason(String failureReason) {
                executionResult.failureReason = failureReason;
                return this;
            }
            
            public PythonExecutionResult build() {
                return executionResult;
            }
        }
        
        // Getters
        public String getStatus() { return status; }
        public String getResult() { return result; }
        public Long getExecutionTime() { return executionTime; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public String getLogContent() { return logContent; }
        public String getLogFilePath() { return logFilePath; }
        public String getFailureReason() { return failureReason; }
    }

    /**
     * 检查是否因环境问题被阻塞
     * 
     * @param logContent 日志内容
     * @return 是否被阻塞
     */
    private static boolean isBlockedByEnvironment(String logContent) {
        if (logContent == null) {
            return false;
        }
        
        String lowerLog = logContent.toLowerCase();
        
        // 检查Python环境问题
        if (lowerLog.contains("importerror") || lowerLog.contains("modulenotfounderror") ||
            lowerLog.contains("no module named") || lowerLog.contains("python executable")) {
            return true;
        }
        
        // 检查文件权限问题
        if (lowerLog.contains("permission denied") || lowerLog.contains("access denied")) {
            return true;
        }
        
        // 检查文件不存在
        if (lowerLog.contains("no such file") || lowerLog.contains("file not found")) {
            return true;
        }
        
        // 检查网络连接问题
        if (lowerLog.contains("connection refused") || lowerLog.contains("network is unreachable") ||
            lowerLog.contains("connection timeout") || lowerLog.contains("dns resolution failed")) {
            return true;
        }
        
        // 检查系统资源问题
        if (lowerLog.contains("out of memory") || lowerLog.contains("disk space") ||
            lowerLog.contains("resource temporarily unavailable")) {
            return true;
        }
        
        return false;
    }

    /**
     * 测试结果分析
     */
    public static class TestResultAnalysis {
        private String status;
        private String result;
        private String failureReason;

        public TestResultAnalysis(String status, String result, String failureReason) {
            this.status = status;
            this.result = result;
            this.failureReason = failureReason;
        }

        public String getStatus() { return status; }
        public String getResult() { return result; }
        public String getFailureReason() { return failureReason; }
    }
    
    /**
     * 对JSON字符串进行转义处理，避免命令行解析错误
     * 
     * @param jsonString 原始JSON字符串
     * @return 转义后的JSON字符串
     */
    private static String escapeJsonForCommandLine(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }
        
        // 对JSON字符串中的特殊字符进行转义
        String escaped = jsonString
            // 转义双引号
            .replace("\"", "\\\"")
            // 转义单引号（在某些shell中可能需要）
            .replace("'", "\\'")
            // 转义换行符
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            // 转义制表符
            .replace("\t", "\\t");
        
        log.debug("JSON转义前: {}", jsonString);
        log.debug("JSON转义后: {}", escaped);
        
        return escaped;
    }
}
