package com.xhzb.nursing.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.xhzb.common.exception.base.BaseException;
import com.xhzb.common.utils.DateUtils;
import com.xhzb.framework.web.service.TokenService;
import com.xhzb.nursing.domain.Elder;
import com.xhzb.nursing.domain.dto.member.MemberBindDto;
import com.xhzb.nursing.domain.dto.member.WxLoginDto;
import com.xhzb.nursing.domain.vo.WxLoginVo;
import com.xhzb.nursing.mapper.ElderMapper;
import com.xhzb.nursing.service.IWechatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xhzb.nursing.mapper.FamilyMemberMapper;
import com.xhzb.nursing.domain.FamilyMember;
import com.xhzb.nursing.service.IFamilyMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.Arrays;

/**
 * 老人家属Service业务层处理
 * 
 * @author lzh
 * @date 2026-10-30
 */
@Service
public class FamilyMemberServiceImpl extends ServiceImpl<FamilyMemberMapper, FamilyMember> implements IFamilyMemberService
{
    @Autowired
    private FamilyMemberMapper familyMemberMapper;

    @Autowired
    private IWechatService wechatService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ElderMapper elderMapper;

    /** 用户昵称前缀集合 */
    private static final List<String> NICK_NAME_LIST = Arrays.asList(
            "大吉大利", "今晚吃鸡", "福星高照", "好事发生", "匆匆忙忙", "连滚带爬", "从从容容", "游刃有余");

    /**
     * 查询老人家属
     * 
     * @param id 老人家属主键
     * @return 老人家属
     */
    @Override
    public FamilyMember selectFamilyMemberById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询老人家属列表
     * 
     * @param familyMember 老人家属
     * @return 老人家属
     */
    @Override
    public List<FamilyMember> selectFamilyMemberList(FamilyMember familyMember)
    {
        return familyMemberMapper.selectFamilyMemberList(familyMember);
    }

    /**
     * 新增老人家属
     * 
     * @param familyMember 老人家属
     * @return 结果
     */
    @Override
    public int insertFamilyMember(FamilyMember familyMember)
    {
        return save(familyMember)? 1 : 0;
    }

    /**
     * 修改老人家属
     * 
     * @param familyMember 老人家属
     * @return 结果
     */
    @Override
    public int updateFamilyMember(FamilyMember familyMember)
    {
        return updateById(familyMember)? 1 : 0;
    }

    /**
     * 批量删除老人家属
     * 
     * @param ids 需要删除的老人家属主键
     * @return 结果
     */
    @Override
    public int deleteFamilyMemberByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids))? 1 : 0;
    }

    /**
     * 删除老人家属信息
     * 
     * @param id 老人家属主键
     * @return 结果
     */
    @Override
    public int deleteFamilyMemberById(Long id)
    {
        return removeById(id)? 1 : 0;
    }

    /**
     * 微信小程序登录：
     * 1、code 换 openid
     * 2、按 openid 查家属
     * 3、phoneCode 换手机号
     * 4、家属为空则新增（openid、手机号、随机昵称）
     * 5、家属不为空且手机号不一致则更新手机号
     * 6、用家属 id 和昵称生成 token
     *
     * @param dto 登录参数
     * @return token 与昵称
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WxLoginVo wxLogin(WxLoginDto dto)
    {
        // 1、临时登录凭证 code 换 openid
        String openId = wechatService.getOpenId(dto.getCode());

        // 2、根据 openid 查询家属信息
        FamilyMember familyMember = lambdaQuery()
                .eq(FamilyMember::getOpenId, openId)
                .last("limit 1")
                .one();

        // 3、手机号凭证换手机号
        String phone = wechatService.getPhone(dto.getPhoneCode());

        if (familyMember == null)
        {
            // 4、家属为空，直接新增
            familyMember = new FamilyMember();
            familyMember.setOpenId(openId);
            familyMember.setPhone(phone);
            familyMember.setName(buildNickName(phone));
            save(familyMember);
        }
        else if (!StrUtil.equals(phone, familyMember.getPhone()))
        {
            // 5、家属不为空，手机号和之前不一致则更新
            familyMember.setPhone(phone);
            familyMember.setUpdateTime(DateUtils.getNowDate());
            updateById(familyMember);
        }

        // 6、基于家属 id 和昵称生成 token
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("userId", familyMember.getId());
        paramMap.put("nickName", familyMember.getName());
        String token = tokenService.createToken(paramMap);

        WxLoginVo vo = new WxLoginVo();
        vo.setToken(token);
        vo.setNickName(familyMember.getName());
        return vo;
    }

    /**
     * 小程序端绑定家人：
     * 1、校验登录状态
     * 2、按身份证号查老人
     * 3、姓名（若传了）需与老人档案一致
     * 4、已绑定则提示重复
     * 5、写入 family_member_elder
     *
     * @param memberId 当前登录家属id
     * @param dto      绑定参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindElder(Long memberId, MemberBindDto dto)
    {
        if (memberId == null)
        {
            throw new BaseException("登录状态已失效，请重新登录");
        }
        String idCard = StrUtil.trim(dto.getIdCard());
        if (StrUtil.isBlank(idCard))
        {
            throw new BaseException("身份证号不能为空");
        }
        Elder elder = elderMapper.selectElderByIdCard(idCard);
        if (elder == null)
        {
            throw new BaseException("未找到该老人，请核对身份证号");
        }
        if (StrUtil.isNotBlank(dto.getName()) && !StrUtil.equals(StrUtil.trim(dto.getName()), elder.getName()))
        {
            throw new BaseException("姓名与身份证号不匹配");
        }
        if (elderMapper.countMemberElder(memberId, elder.getId()) > 0)
        {
            throw new BaseException("该老人已绑定，请勿重复绑定");
        }
        elderMapper.insertMemberElder(memberId, elder.getId(), DateUtils.getNowDate(), StrUtil.trim(dto.getRemark()));
    }

    /**
     * 小程序端家人列表（分页）
     *
     * @param memberId 当前登录家属id
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 家人列表
     */
    @Override
    public List<Map<String, Object>> listFamilyByPage(Long memberId, Integer pageNum, Integer pageSize)
    {
        if (memberId == null)
        {
            throw new BaseException("登录状态已失效，请重新登录");
        }
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return elderMapper.selectFamilyListByMemberId(memberId, (page - 1) * size, size);
    }

    /**
     * 生成昵称：随机四字前缀 + 手机号后四位，如「好事发生8915」
     *
     * @param phone 手机号
     * @return 昵称
     */
    private String buildNickName(String phone)
    {
        List<String> nickNameList = new ArrayList<>(NICK_NAME_LIST);
        // 打乱集合中全部元素的顺序
        Collections.shuffle(nickNameList);
        String prefix = nickNameList.get(0);
        // 手机号后四位，取不到时退化为 4 位随机数字
        String postfix = StrUtil.isNotBlank(phone) && phone.length() >= 4
                ? phone.substring(phone.length() - 4)
                : RandomUtil.randomNumbers(4);
        return prefix + postfix;
    }
}
