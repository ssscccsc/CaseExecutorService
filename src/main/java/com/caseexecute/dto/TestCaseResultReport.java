package com.caseexecute.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用例执行结果上报DTO
 * 
 * @author system
 * @since 2024-01-01
 */
@Data
public class TestCaseResultReport {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 用例ID
     */
    private Long testCaseId;
    
    /**
     * 轮次
     */
    private Integer round;
    
    /**
     * 执行状态 (SUCCESS/FAILED/BLOCKED)
     */
    private String status;
    
    /**
     * 执行结果描述
     */
    private String result;
    
    /**
     * 执行耗时（毫秒）
     */
    private Long executionTime;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 失败原因（详细分析）
     */
    private String failureReason;
    
    /**
     * 执行机IP
     */
    private String executorIp;
    
    /**
     * 用例集ID
     */
    private Long testCaseSetId;
    
    /**
     * 日志文件路径或HTTP链接
     */
    private String logFilePath;
    
    /**
     * 用例采集路径输出（从日志中解析 "save log in xxx" 后面的信息）
     */
    private String collectPath;
    
    /**
     * 质检结果（从日志中解析 "===QC_Result===" 到 "===End" 中间的信息）
     */
    private String qcResult;
}
