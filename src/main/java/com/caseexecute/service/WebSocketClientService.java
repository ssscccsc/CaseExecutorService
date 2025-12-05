package com.caseexecute.service;

/**
 * WebSocket客户端服务接口
 * 
 * @author system
 * @since 2024-01-01
 */
public interface WebSocketClientService {
    
    /**
     * 启动WebSocket客户端并连接到后台
     */
    void start();
    
    /**
     * 停止WebSocket客户端
     */
    void stop();
    
    /**
     * 检查连接状态
     * 
     * @return 是否已连接
     */
    boolean isConnected();
    
    /**
     * 发送消息到后台
     * 
     * @param message 消息内容（JSON字符串）
     */
    void sendMessage(String message);
    
    /**
     * 注册执行机信息
     */
    void registerExecutor();
}






























