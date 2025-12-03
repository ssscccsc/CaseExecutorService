package com.caseexecute.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebSocket客户端配置
 * 
 * @author system
 * @since 2024-01-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "websocket.client")
public class WebSocketClientConfig {
    
    /**
     * 后台服务地址（如：ws://localhost:8080）
     */
    private String serverUrl = "ws://localhost:8080/api/ws/executor";
    
    /**
     * 是否启用WebSocket连接
     */
    private Boolean enabled = true;
    
    /**
     * 重连间隔（秒）
     */
    private Long reconnectInterval = 30L;
    
    /**
     * 心跳间隔（秒）
     */
    private Long heartbeatInterval = 30L;
    
    /**
     * 连接超时时间（秒）
     */
    private Long connectionTimeout = 10L;
    
    /**
     * 执行机地域信息配置
     */
    private RegionConfig region = new RegionConfig();
    
    /**
     * 地域信息配置类
     */
    @Data
    public static class RegionConfig {
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
    }
}




























