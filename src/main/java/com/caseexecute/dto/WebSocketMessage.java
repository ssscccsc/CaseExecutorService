package com.caseexecute.dto;

import lombok.Data;

/**
 * WebSocket通用消息DTO
 * 
 * @author system
 * @since 2024-01-01
 */
@Data
public class WebSocketMessage {
    
    /**
     * 消息类型：REGISTER, HEARTBEAT, TASK, RESPONSE等
     */
    private String type;
    
    /**
     * 消息数据（JSON格式字符串）
     */
    private Object data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 消息ID（用于请求-响应匹配）
     */
    private String messageId;
    
    /**
     * 执行机IP（用于标识发送者）
     */
    private String executorIp;
}




























