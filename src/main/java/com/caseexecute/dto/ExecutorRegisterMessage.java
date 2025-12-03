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
     * 执行机MAC地址
     */
    private String executorMac;
    
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
     * 地域名称（片区，level=1）
     */
    private String regionName;
    
    /**
     * 国家名称（level=2）
     */
    private String countryName;
    
    /**
     * 省份名称（level=3）
     */
    private String provinceName;
    
    /**
     * 城市名称（level=4）
     */
    private String cityName;
    
    /**
     * 时间戳
     */
    private Long timestamp;
}














