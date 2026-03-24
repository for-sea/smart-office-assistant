package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.DocumentDept;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档-部门关联表 Mapper。
 */
@Mapper
public interface DocumentDeptMapper extends BaseMapper<DocumentDept> {

    /**
     * 根据部门编码集合查询可访问的文档 ID。
     *
     * @param departments 部门编码集合
     * @return 文档 ID 列表
     */
    @Select(
            "<script>"
                    + "SELECT DISTINCT doc_id FROM document_dept "
                    + "WHERE "
                    + "<if test='departments != null and departments.size() > 0'>"
                    + "department IN "
                    + "<foreach collection='departments' item='dept' open='(' separator=',' close=')'>"
                    + "#{dept}"
                    + "</foreach>"
                    + "</if>"
                    + "<if test='departments == null or departments.size() == 0'>"
                    + "1 = 0"
                    + "</if>"
                    + "</script>")
    List<Long> selectDocIdsByDepartments(@Param("departments") Collection<String> departments);

    /**
     * 根据文档 ID 查询所有关联部门。
     *
     * @param docId 文档 ID
     * @return 部门编码列表
     */
    @Select("SELECT department FROM document_dept WHERE doc_id = #{docId}")
    List<String> selectDepartmentsByDocId(@Param("docId") Long docId);

    /**
     * 根据文档 ID 删除全部部门关联。
     *
     * @param docId 文档 ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM document_dept WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);
}
