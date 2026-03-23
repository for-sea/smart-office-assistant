package com.smart.office.repository.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.DocChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档块 Mapper 接口
 */
@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunk> {

    /**
     * 根据文档ID查询所有块
     */
    @Select("SELECT * FROM doc_chunk WHERE doc_id = #{docId} ORDER BY chunk_index")
    List<DocChunk> selectByDocId(@Param("docId") Long docId);

    /**
     * 根据文档ID和MilvusID查询
     */
    @Select("SELECT * FROM doc_chunk WHERE doc_id = #{docId} AND milvus_id = #{milvusId}")
    DocChunk selectByDocIdAndMilvusId(@Param("docId") Long docId, @Param("milvusId") String milvusId);

    /**
     * 批量插入（使用XML配置）
     */
    int batchInsert(@Param("list") List<DocChunk> chunkList);

    /**
     * 删除文档的所有块
     */
    int deleteByDocId(@Param("docId") Long docId);
}