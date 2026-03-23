package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文档 Mapper 接口
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 自定义查询：根据上传者查询文档
     */
    @Select("SELECT * FROM document WHERE uploader = #{uploader} ORDER BY upload_time DESC")
    List<Document> selectByUploader(@Param("uploader") String uploader);

    /**
     * 自定义查询：根据状态查询文档
     */
    @Select("SELECT * FROM document WHERE status = #{status}")
    List<Document> selectByStatus(@Param("status") Integer status);

    /**
     * 自定义更新：更新文档状态
     */
    @Update("UPDATE document SET status = #{status}, error_message = #{errorMessage} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status,
                     @Param("errorMessage") String errorMessage);

    /**
     * 自定义更新：更新文档处理结果
     */
    @Update("UPDATE document SET status = #{status}, chunk_count = #{chunkCount}, " +
            "process_time = #{processTime} WHERE id = #{id}")
    int updateProcessResult(@Param("id") Long id, @Param("status") Integer status,
                            @Param("chunkCount") Integer chunkCount,
                            @Param("processTime") Integer processTime);

    /**
     * 统计用户文档数量
     */
    @Select("SELECT COUNT(*) FROM document WHERE uploader = #{uploader} AND deleted = 0")
    long countByUploader(@Param("uploader") String uploader);
}