package com.xhzb.nursing.controller.mamber;

import java.util.List;
import java.util.Map;

import com.xhzb.common.core.controller.BaseController;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.nursing.domain.dto.member.MemberBindDto;
import com.xhzb.nursing.domain.dto.member.WxLoginDto;
import com.xhzb.nursing.domain.vo.WxLoginVo;
import com.xhzb.nursing.mapper.ElderMapper;
import com.xhzb.nursing.service.IDeviceDataService;
import com.xhzb.nursing.service.IDeviceService;
import com.xhzb.nursing.service.IFamilyMemberService;
import com.xhzb.nursing.util.MemberHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端用户登录
 *
 * @author lzh
 * @date 2026-10-30
 */
@RestController
@RequestMapping("/member/user")
@Tag(name = "小程序用户登录")
public class MemberUserController extends BaseController {

    @Autowired
    private IFamilyMemberService familyMemberService;

    @Autowired
    private ElderMapper elderMapper;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private IDeviceDataService deviceDataService;

    /**
     * 微信小程序登录
     */
    @PostMapping("/login")
    @Operation(summary = "微信小程序登录")
    public AjaxResult login(@Validated @RequestBody WxLoginDto dto) {
        WxLoginVo vo = familyMemberService.wxLogin(dto);
        return success(vo);
    }

    /**
     * 查询当前家属绑定的老人（我的老人 / 探访预约选老人）
     */
    @GetMapping("/my")
    @Operation(summary = "查询当前家属绑定的老人")
    public AjaxResult my() {
        List<Map<String, Object>> list = elderMapper.selectElderByMemberId(MemberHolder.getMemberId());
        return success(list);
    }

    /**
     * 绑定家人：按老人身份证号建立家属与老人的绑定关系
     */
    @PostMapping("/add")
    @Operation(summary = "绑定家人")
    public AjaxResult add(@Validated @RequestBody MemberBindDto dto) {
        familyMemberService.bindElder(MemberHolder.getMemberId(), dto);
        return success();
    }

    /**
     * 家人列表（分页）：床位、房型、随身设备一并返回
     */
    @GetMapping("/list-by-page")
    @Operation(summary = "家人列表（分页查询）")
    public AjaxResult listByPage(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                 @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return success(familyMemberService.listFamilyByPage(MemberHolder.getMemberId(), pageNum, pageSize));
    }

    /**
     * 查询健康数据：返回设备最近一次上报的全部属性
     */
    @GetMapping("/queryServiceProperties/{iotId}")
    @Operation(summary = "查询健康数据")
    public AjaxResult queryServiceProperties(@PathVariable("iotId") String iotId) {
        return success(deviceService.queryServiceProperties(iotId));
    }

    /**
     * 按天查询指标数据：按小时聚合，返回 24 个点
     */
    @GetMapping("/queryDeviceDataListByDay")
    @Operation(summary = "按天统计查询指标数据")
    public AjaxResult queryDeviceDataListByDay(
            @RequestParam("iotId") String iotId,
            @RequestParam(value = "functionId", required = false, defaultValue = "HeartRate") String functionId,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime) {
        return success(deviceDataService.selectAvgDataByHour(iotId, functionId, startTime, endTime));
    }

    /**
     * 按周查询指标数据：按天聚合，返回 7 个点
     */
    @GetMapping("/queryDeviceDataListByWeek")
    @Operation(summary = "按周统计查询指标数据")
    public AjaxResult queryDeviceDataListByWeek(
            @RequestParam("iotId") String iotId,
            @RequestParam(value = "functionId", required = false, defaultValue = "HeartRate") String functionId,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime) {
        return success(deviceDataService.selectAvgDataByDay(iotId, functionId, startTime, endTime));
    }
}
