package com.smart.office.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档统计VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatisticsVO {

    /**
     * 文档总数
     */
    private long totalCount;

    /**
     * 处理中的文档数
     */
    private long processingCount;

    /**
     * 处理完成的文档数
     */
    private long completedCount;

    /**
     * 处理失败的文档数
     */
    private long failedCount;

    /**
     * 已删除的文档数
     */
    private long deletedCount;

    /**
     * 总块数（所有已完成文档的块数之和）
     */
    private long totalChunks;

    /**
     * 总文件大小（字节）
     */
    private long totalSize;

    /**
     * 格式化后的文件大小（如：1.23 MB）
     */
    private String totalSizeFormatted;

    /**
     * 成功率（已完成数/总数，百分比）
     */
    public double getSuccessRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) completedCount / totalCount * 100;
    }

    /**
     * 失败率（失败数/总数，百分比）
     */
    public double getFailureRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) failedCount / totalCount * 100;
    }

    /**
     * 平均每个文档的块数
     */
    public double getAverageChunksPerDocument() {
        if (completedCount == 0) {
            return 0.0;
        }
        return (double) totalChunks / completedCount;
    }

    /**
     * 平均文件大小（字节）
     */
    public double getAverageFileSize() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) totalSize / totalCount;
    }

    /**
     * 详细统计信息（可选，用于更细致的分析）
     */
    private DetailedStatistics detailed;

    /**
     * 详细统计内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailedStatistics {
        /**
         * 按部门统计
         */
        private DepartmentStatistics byDepartment;

        /**
         * 按月份统计上传数量
         */
        private java.util.Map<String, Long> byMonth;

        /**
         * 按文件类型统计
         */
        private java.util.Map<String, Long> byFileType;
    }

    /**
     * 部门统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentStatistics {
        private long hrCount;      // 人力资源部
        private long itCount;      // 信息技术部
        private long rdCount;      // 研发部
        private long otherCount;   // 其他部门
    }
}
