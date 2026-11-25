package com.xhzb.nursing.service;

import java.util.List;
import java.util.Map;
import com.xhzb.nursing.domain.FamilyMember;
import com.xhzb.nursing.domain.dto.member.MemberBindDto;
import com.xhzb.nursing.domain.dto.member.WxLoginDto;
import com.xhzb.nursing.domain.vo.WxLoginVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 老人家属Service接口
 * 
 * @author lzh
 * @date 2026-10-30
 */
public interface IFamilyMemberService extends IService<FamilyMember>
{
    /**
     * 查询老人家属
     * 
     * @param id 老人家属主键
     * @return 老人家属
     */
    public FamilyMember selectFamilyMemberById(Long id);

    /**
     * 查询老人家属列表
     * 
     * @param familyMember 老人家属
     * @return 老人家属集合
     */
    public List<FamilyMember> selectFamilyMemberList(FamilyMember familyMember);

    /**
     * 新增老人家属
     * 
     * @param familyMember 老人家属
     * @return 结果
     */
    public int insertFamilyMember(FamilyMember familyMember);

    /**
     * 修改老人家属
     * 
     * @param familyMember 老人家属
     * @return 结果
     */
    public int updateFamilyMember(FamilyMember familyMember);

    /**
     * 批量删除老人家属
     * 
     * @param ids 需要删除的老人家属主键集合
     * @return 结果
     */
    public int deleteFamilyMemberByIds(Long[] ids);

    /**
     * 删除老人家属信息
     * 
     * @param id 老人家属主键
     * @return 结果
     */
    public int deleteFamilyMemberById(Long id);

    /**
     * 微信小程序登录
     *
     * @param dto 登录参数（code、nickName、phoneCode）
     * @return token 与家属昵称
     */
    public WxLoginVo wxLogin(WxLoginDto dto);

    /**
     * 小程序端绑定家人：按身份证号找到老人并建立家属-老人关系
     *
     * @param memberId 当前登录家属id
     * @param dto      绑定参数（身份证号、老人姓名、称呼）
     */
    public void bindElder(Long memberId, MemberBindDto dto);

    /**
     * 小程序端家人列表（分页）
     *
     * @param memberId 当前登录家属id
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 家人列表（含床位、房型、随身设备）
     */
    public List<Map<String, Object>> listFamilyByPage(Long memberId, Integer pageNum, Integer pageSize);
}
