package com.caseexecute.dto;

import lombok.Data;

/**
 * 执行机注册消息DTO
 * 
 * @author system
 * @since 2024-01-01
 */
@Data
public class ExecutorRegisterMessage {
    
    /**
     * 消息类型：REGISTER
     */
    private String type = "REGISTER";
    
    /**
     * 执行机IP地址
     */
    private String executorIp;
    
    /**
     * 执行机名称
     */
    private String executorName;
    
    /**
     * 执行机状态（0: 离线, 1: 在线, 2: 故障）
     */
    private Integer status;
    
    /**
     * 执行机描述
     */
    private String description;
    
    /**
     * 时间戳
     */
    private Long timestamp;
}

