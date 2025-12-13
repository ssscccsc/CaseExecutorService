package com.caseexecute.controller;

import com.caseexecute.common.Result;
import com.caseexecute.dto.TestCaseExecutionRequest;
import com.caseexecute.service.TestCaseExecutionService;
import com.caseexecute.util.PythonExecutorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 用例执行任务控制器
 * 
 * @author system
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/test-case-execution")
@Validated
public class TestCaseExecutionController {

    @Autowired
    private TestCaseExecutionService testCaseExecutionService;

    /**
     * 接收用例执行任务
     * 
     * @param request 用例执行任务请求
     * @return 执行结果
     */
    @PostMapping("/receive")
    public Result<Map<String, Object>> receiveTestCaseExecution(@Valid @RequestBody TestCaseExecutionRequest request) {
        log.info("接收到用例执行任务 - 任务ID: {}, 执行机IP: {}, 用例集ID: {}", 
                request.getTaskId(), request.getExecutorIp(), request.getTestCaseSetId());
        
        try {
            // 记录任务详情
            log.info("任务详情:");
            log.info("  - 任务ID: {}", request.getTaskId());
            log.info("  - 执行机IP: {}", request.getExecutorIp());
            log.info("  - 用例集ID: {}", request.getTestCaseSetId());
            log.info("  - 用例集路径: {}", request.getTestCaseSetPath());
            log.info("  - 用例数量: {}", request.getTestCaseList().size());
            log.info("  - 结果上报URL: {}", request.getResultReportUrl());
            log.info("  - 日志上报URL: {}", request.getLogReportUrl());
            
            // 记录UE信息详情
            if (request.getUeList() != null && !request.getUeList().isEmpty()) {
                log.info("执行机关联的UE信息:");
                log.info("  - UE数量: {}", request.getUeList().size());
                for (TestCaseExecutionRequest.UeInfo ue : request.getUeList()) {
                                    log.info("  - UE ID: {}, 名称: {}, 用途: {}, 网络类型: {}, 厂商: {}, 型号: {}, 状态: {}", 
                        ue.getUeId(), ue.getName(), ue.getPurpose(), 
                        ue.getNetworkTypeName(), ue.getVendor(), ue.getModel(), ue.getStatus());
                }
            } else {
                log.warn("执行机未关联UE信息");
            }
            
            // 记录采集策略信息详情
            if (request.getCollectStrategyInfo() != null) {
                TestCaseExecutionRequest.CollectStrategyInfo strategy = request.getCollectStrategyInfo();
                log.info("采集策略信息:");
                log.info("  - 策略ID: {}", strategy.getId());
                log.info("  - 策略名称: {}", strategy.getName());
                log.info("  - 采集次数: {}", strategy.getCollectCount());
                log.info("  - 业务大类: {}", strategy.getBusinessCategory());
                log.info("  - APP: {}", strategy.getApp());
                log.info("  - 意图: {}", strategy.getIntent());
                log.info("  - 自定义参数: {}", strategy.getCustomParams());
                log.info("  - 策略状态: {}", strategy.getStatus());
            } else {
                log.warn("未提供采集策略信息");
            }
            
            // 记录采集任务自定义参数信息
            if (request.getTaskCustomParams() != null && !request.getTaskCustomParams().trim().isEmpty()) {
                log.info("采集任务自定义参数: {}", request.getTaskCustomParams());
            } else {
                log.info("采集任务无自定义参数");
            }
            
            // 记录用例列表详情
            log.info("用例列表:");
            for (TestCaseExecutionRequest.TestCaseInfo testCase : request.getTestCaseList()) {
                log.info("  - 用例ID: {}, 用例编号: {}, 轮次: {}", 
                        testCase.getTestCaseId(), testCase.getTestCaseNumber(), testCase.getRound());
            }
            
            // TODO: 这里可以添加具体的任务处理逻辑
            // 例如：
            // 1. 验证用例集文件是否存在
            // 2. 创建执行任务记录
            // 3. 启动异步执行线程
            // 4. 返回任务接收确认
            
            // 调用服务处理任务
            testCaseExecutionService.processTestCaseExecution(request);
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", request.getTaskId());
            result.put("status", "RECEIVED");
            result.put("message", "用例执行任务已接收");
            result.put("executorIp", request.getExecutorIp());
            result.put("testCaseCount", request.getTestCaseList().size());
            result.put("ueCount", request.getUeList() != null ? request.getUeList().size() : 0);
            result.put("hasCollectStrategy", request.getCollectStrategyInfo() != null);
            result.put("hasTaskCustomParams", request.getTaskCustomParams() != null && !request.getTaskCustomParams().trim().isEmpty());
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("用例执行任务接收成功 - 任务ID: {}", request.getTaskId());
            return Result.success("用例执行任务接收成功", result);
            
        } catch (Exception e) {
            log.error("用例执行任务接收失败 - 任务ID: {}, 错误: {}", request.getTaskId(), e.getMessage(), e);
            return Result.error("用例执行任务接收失败: " + e.getMessage());
        }
    }
    

    
    /**
     * 获取任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        log.info("查询任务状态 - 任务ID: {}", taskId);
        
        try {
            // 查询数据库中的执行结果来获取真实状态
            Map<String, Object> status = new HashMap<>();
            status.put("taskId", taskId);
            
            // TODO: 这里应该查询数据库获取真实的执行状态
            // 暂时返回一个基本状态
            status.put("status", "BLOCKED");
            status.put("progress", 0);
            status.put("completedCases", 0);
            status.put("totalCases", 0);
            status.put("startTime", System.currentTimeMillis());
            status.put("estimatedEndTime", System.currentTimeMillis() + 300000); // 5分钟后
            
            log.info("任务状态查询完成 - 任务ID: {}, 状态: {}", taskId, status.get("status"));
            return Result.success("查询成功", status);
            
        } catch (Exception e) {
            log.error("查询任务状态失败 - 任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
            return Result.error("查询任务状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消任务执行
     * 
     * @param taskId 任务ID
     * @return 取消结果
     */
    @PostMapping("/cancel/{taskId}")
    public Result<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        log.info("取消任务执行 - 任务ID: {}", taskId);
        
        try {
            boolean cancelled = testCaseExecutionService.cancelTaskExecution(taskId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            
            if (cancelled) {
                result.put("status", "CANCELLED");
                result.put("message", "任务已成功取消");
                log.info("任务取消成功 - 任务ID: {}", taskId);
                return Result.success("任务取消成功", result);
            } else {
                result.put("status", "NOT_FOUND");
                result.put("message", "任务不存在或已完成");
                log.warn("任务不存在或已完成 - 任务ID: {}", taskId);
                return Result.error("任务不存在或已完成");
            }
            
        } catch (Exception e) {
            log.error("取消任务失败 - 任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
            return Result.error("取消任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 紧急终止所有Python进程（按任务ID）
     * 
     * @param taskId 任务ID
     * @return 终止结果
     */
    @PostMapping("/emergency-stop/{taskId}")
    public Result<Map<String, Object>> emergencyStopPythonProcesses(@PathVariable String taskId) {
        log.info("紧急终止Python进程 - 任务ID: {}", taskId);
        
        try {
            // 使用PythonExecutorUtil终止所有相关的Python进程
            PythonExecutorUtil.terminateAllPythonProcessesByTaskId(taskId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", "TERMINATED");
            result.put("message", "已紧急终止所有相关的Python进程");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("紧急终止Python进程成功 - 任务ID: {}", taskId);
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("紧急终止Python进程失败 - 任务ID: {}, 错误: {}", taskId, e.getMessage(), e);
            return Result.error("紧急终止Python进程失败: " + e.getMessage());
        }
    }
    
    /**
     * 紧急终止所有Python进程（按脚本路径）
     * 
     * @param scriptPath 脚本路径
     * @return 终止结果
     */
    @PostMapping("/emergency-stop-script")
    public Result<Map<String, Object>> emergencyStopPythonProcessesByScript(@RequestParam String scriptPath) {
        log.info("紧急终止Python进程 - 脚本路径: {}", scriptPath);
        
        try {
            // 使用PythonExecutorUtil终止所有相关的Python进程
            PythonExecutorUtil.terminateAllPythonProcessesByScriptPath(scriptPath);
            
            Map<String, Object> result = new HashMap<>();
            result.put("scriptPath", scriptPath);
            result.put("status", "TERMINATED");
            result.put("message", "已紧急终止所有相关的Python进程");
            result.put("timestamp", System.currentTimeMillis());
            
            log.info("紧急终止Python进程成功 - 脚本路径: {}", scriptPath);
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("紧急终止Python进程失败 - 脚本路径: {}, 错误: {}", scriptPath, e.getMessage(), e);
            return Result.error("紧急终止Python进程失败: " + e.getMessage());
        }
    }
}
