package com.smart.office.base.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.domain.document.entity.DocumentGroup;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档-用户组关联表 Mapper。
 */
@Mapper
public interface DocumentGroupMapper extends BaseMapper<DocumentGroup> {

    /**
     * 根据用户组编码集合查询可访问的文档 ID。
     *
     * @param groupCodes 用户组编码集合
     * @return 文档 ID 列表
     */
    @Select(
            "<script>"
                    + "SELECT DISTINCT doc_id FROM document_group "
                    + "WHERE "
                    + "<if test='groupCodes != null and groupCodes.size() > 0'>"
                    + "group_code IN "
                    + "<foreach collection='groupCodes' item='code' open='(' separator=',' close=')'>"
                    + "#{code}"
                    + "</foreach>"
                    + "</if>"
                    + "<if test='groupCodes == null or groupCodes.size() == 0'>"
                    + "1 = 0"
                    + "</if>"
                    + "</script>")
    List<Long> selectDocIdsByGroupCodes(@Param("groupCodes") Collection<String> groupCodes);

    /**
     * 根据文档 ID 查询关联的用户组编码。
     *
     * @param docId 文档 ID
     * @return 用户组编码列表
     */
    @Select("SELECT group_code FROM document_group WHERE doc_id = #{docId}")
    List<String> selectGroupCodesByDocId(@Param("docId") Long docId);

    /**
     * 根据文档 ID 删除所有用户组关联关系。
     *
     * @param docId 文档 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM document_group WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);
}
