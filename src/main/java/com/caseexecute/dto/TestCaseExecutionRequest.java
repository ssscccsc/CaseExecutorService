package com.caseexecute.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 用例执行任务请求DTO
 * 
 * @author system
 * @since 2024-01-01
 */
@Data
public class TestCaseExecutionRequest {
    
    /**
     * 用例执行任务ID
     */
    @NotBlank(message = "用例执行任务ID不能为空")
    private String taskId;
    
    /**
     * 执行机IP
     */
    @NotBlank(message = "执行机IP不能为空")
    private String executorIp;
    
    /**
     * 用例集ID
     */
    @NotNull(message = "用例集ID不能为空")
    private Long testCaseSetId;
    
    /**
     * 用例集存储路径
     */
    @NotBlank(message = "用例集存储路径不能为空")
    private String testCaseSetPath;
    
    /**
     * 用例列表
     */
    @NotEmpty(message = "用例列表不能为空")
    private List<TestCaseInfo> testCaseList;
    
    /**
     * 结果上报URL
     */
    @NotBlank(message = "结果上报URL不能为空")
    private String resultReportUrl;
    
    /**
     * 日志上报URL
     */
    @NotBlank(message = "日志上报URL不能为空")
    private String logReportUrl;
    
    /**
     * 执行机关联的UE全部信息
     */
    private List<UeInfo> ueList;
    
    /**
     * 采集策略的所有信息
     */
    private CollectStrategyInfo collectStrategyInfo;
    
    /**
     * 采集任务的自定义参数（JSON格式）
     */
    private String taskCustomParams;
    
    /**
     * 网元信息列表
     */
    private List<NetworkElementInfo> networkElementInfoList;
    
    /**
     * 用例信息
     */
    @Data
    public static class TestCaseInfo {
        
        /**
         * 用例ID
         */
        @NotNull(message = "用例ID不能为空")
        private Long testCaseId;
        
        /**
         * 用例编号
         */
        private String testCaseNumber;
        
        /**
         * 轮次
         */
        @NotNull(message = "轮次不能为空")
        private Integer round;
    }
    
    /**
     * UE信息
     */
    @Data
    public static class UeInfo {
        
        /**
         * UE ID
         */
        private Long id;
        
        /**
         * UE唯一标识
         */
        private String ueId;
        
        /**
         * UE名称
         */
        private String name;
        
        /**
         * UE用途
         */
        private String purpose;
        
        /**
         * 网络类型ID
         */
        private Long networkTypeId;
        
        /**
         * 网络类型名称
         */
        private String networkTypeName;
        
        /**
         * UE厂商
         */
        private String vendor;

        /**
         * UE型号
         */
        private String model;
        
        /**
         * UE端口
         */
        private String port;
        
        /**
         * UE描述
         */
        private String description;
        
        /**
         * UE状态
         */
        private Integer status;
    }
    
    /**
     * 采集策略信息
     */
    @Data
    public static class CollectStrategyInfo {
        
        /**
         * 策略ID
         */
        private Long id;
        
        /**
         * 策略名称
         */
        private String name;
        
        /**
         * 采集次数
         */
        private Integer collectCount;
        
        /**
         * 用例集ID
         */
        private Long testCaseSetId;
        
        /**
         * 业务大类
         */
        private String businessCategory;
        
        /**
         * APP
         */
        private String app;
        
        /**
         * APP英文名
         */
        private String appEn;
        
        /**
         * 意图
         */
        private String intent;
        
        /**
         * 自定义参数
         */
        private String customParams;
        
        /**
         * 策略描述
         */
        private String description;
        
        /**
         * 策略状态
         */
        private Integer status;
    }
    
    /**
     * 网元信息
     */
    @Data
    public static class NetworkElementInfo {
        
        /**
         * 网元ID
         */
        private Long id;
        
        /**
         * 网元名称
         */
        private String name;
        
        /**
         * 网元描述
         */
        private String description;
        
        /**
         * 网元状态
         */
        private Integer status;
        
        /**
         * 网元属性列表
         */
        private List<AttributeInfo> attributes;
        
        /**
         * 网元属性信息
         */
        @Data
        public static class AttributeInfo {
            /**
             * 属性名称
             */
            private String name;
            
            /**
             * 属性值
             */
            private String value;
        }
    }
}
