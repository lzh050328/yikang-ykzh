package com.xhzb.nursing.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhzb.nursing.domain.Elder;

/**
 * 老人Mapper接口
 * 
 * @author lzh
 * @date 2026-10-30
 */
@Mapper
public interface ElderMapper extends BaseMapper<Elder>
{
    /**
     * 查询老人
     * 
     * @param id 老人主键
     * @return 老人
     */
    public Elder selectElderById(Long id);

    /**
     * 查询老人列表
     * 
     * @param elder 老人
     * @return 老人集合
     */
    public List<Elder> selectElderList(Elder elder);

    /**
     * 新增老人
     * 
     * @param elder 老人
     * @return 结果
     */
    public int insertElder(Elder elder);

    /**
     * 修改老人
     * 
     * @param elder 老人
     * @return 结果
     */
    public int updateElder(Elder elder);

    /**
     * 删除老人
     * 
     * @param id 老人主键
     * @return 结果
     */
    public int deleteElderById(Long id);

    /**
     * 查询当前家属绑定的老人（小程序端“我的老人”）
     *
     * @param memberId 家属id
     * @return 老人列表
     */
    @Select("select e.id as elderId, e.name as elderName, e.sex as elderSex, e.image as elderImage,"
            + " e.phone as elderPhone, e.bed_number as bedNumber, e.status as elderStatus"
            + " from family_member_elder fe inner join elder e on e.id = fe.elder_id"
            + " where fe.family_member_id = #{memberId} order by e.id")
    List<Map<String, Object>> selectElderByMemberId(@Param("memberId") Long memberId);

    /**
     * 小程序端家人列表（分页）：家属-老人关系 + 老人档案 + 床位房型 + 随身设备
     *
     * @param memberId 家属id
     * @param offset   偏移量
     * @param pageSize 每页条数
     * @return 家人列表
     */
    @Select("select fe.id as mid, fe.remark as mremark, e.id as elderId, e.name as name, e.image as image,"
            + " e.bed_number as bedNumber, r.type_name as typeName, d.iot_id as iotId,"
            + " d.device_name as deviceName, d.product_key as productKey"
            + " from family_member_elder fe"
            + " inner join elder e on e.id = fe.elder_id"
            + " left join bed b on b.id = e.bed_id"
            + " left join room r on r.id = b.room_id"
            + " left join device d on d.id = (select max(dd.id) from device dd"
            + " where dd.binding_location = cast(e.id as char) collate utf8mb4_unicode_ci"
            + " and dd.location_type = 0)"
            + " where fe.family_member_id = #{memberId}"
            + " order by fe.id limit #{offset}, #{pageSize}")
    List<Map<String, Object>> selectFamilyListByMemberId(@Param("memberId") Long memberId,
                                                         @Param("offset") int offset,
                                                         @Param("pageSize") int pageSize);

    /**
     * 按身份证号查询老人（小程序端绑定家人）
     *
     * @param idCard 身份证号
     * @return 老人（仅 id、name）
     */
    @Select("select id, name from elder where id_card_no = #{idCard} limit 1")
    Elder selectElderByIdCard(@Param("idCard") String idCard);

    /**
     * 统计家属与老人是否已绑定
     *
     * @param memberId 家属id
     * @param elderId  老人id
     * @return 绑定条数
     */
    @Select("select count(1) from family_member_elder where family_member_id = #{memberId} and elder_id = #{elderId}")
    int countMemberElder(@Param("memberId") Long memberId, @Param("elderId") Long elderId);

    /**
     * 新增家属-老人绑定关系
     *
     * @param memberId   家属id
     * @param elderId    老人id
     * @param createTime 创建时间（应用侧时间，避免数据库时区差异）
     * @param remark     家属对老人的称呼
     * @return 结果
     */
    @Insert("insert into family_member_elder(family_member_id, elder_id, create_time, create_by, remark)"
            + " values(#{memberId}, #{elderId}, #{createTime}, #{memberId}, #{remark})")
    int insertMemberElder(@Param("memberId") Long memberId, @Param("elderId") Long elderId,
                          @Param("createTime") java.util.Date createTime, @Param("remark") String remark);

    /**
     * 批量删除老人
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteElderByIds(Long[] ids);
}
