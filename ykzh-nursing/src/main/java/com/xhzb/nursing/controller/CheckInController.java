package com.xhzb.nursing.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.xhzb.common.annotation.Log;
import com.xhzb.common.annotation.RepeatSubmit;
import com.xhzb.common.core.controller.BaseController;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.common.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.xhzb.nursing.domain.CheckIn;
import com.xhzb.nursing.domain.dto.checkin.CheckInApplyDto;
import com.xhzb.nursing.domain.vo.CheckInDetailVo;
import com.xhzb.nursing.service.ICheckInService;
import com.xhzb.common.utils.poi.ExcelUtil;
import com.xhzb.common.core.page.TableDataInfo;

/**
 * 入住信息Controller
 * 
 * @author lzh
 * @date 2026-10-30
 */
@RestController
@RequestMapping("/nursing/checkIn")
@Tag(name = "入住信息相关接口")
public class CheckInController extends BaseController
{
    @Autowired
    private ICheckInService checkInService;

    /**
     * 查询入住信息列表
     */
    @PreAuthorize("@ss.hasPermi('nursing:checkIn:list')")
    @GetMapping("/list")
    @Operation(summary = "查询入住信息列表")
    public TableDataInfo list(CheckIn checkIn)
    {
        startPage();
        List<CheckIn> list = checkInService.selectCheckInList(checkIn);
        return getDataTable(list);
    }

    /**
     * 导出入住信息列表
     */
    @PreAuthorize("@ss.hasPermi('nursing:checkIn:export')")
    @Log(title = "入住信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    @Operation(summary = "导出入住信息列表")
    public void export(HttpServletResponse response, CheckIn checkIn)
    {
        List<CheckIn> list = checkInService.selectCheckInList(checkIn);
        ExcelUtil<CheckIn> util = new ExcelUtil<CheckIn>(CheckIn.class);
        util.exportExcel(response, list, "入住信息数据");
    }

    /**
     * 获取入住信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('nursing:checkIn:query')")
    @GetMapping(value = "/{id}")
    @Operation(summary = "获取入住信息详细信息")
    public AjaxResult getInfo(@Schema(name = "入住信息ID", requiredMode = Schema.RequiredMode.REQUIRED)
            @PathVariable("id") Long id)
    {
        return success(checkInService.selectCheckInById(id));
    }

    /**
     * 新增入住信息
     */
    @PreAuthorize("@ss.hasPermi('nursing:checkIn:add')")
    @Log(title = "入住信息", businessType = BusinessType.INSERT)
    @PostMapping
    @Operation(summary = "新增入住信息")
    public AjaxResult add(@RequestBody CheckIn checkIn)
    {
        return toAjax(checkInService.insertCheckIn(checkIn));
    }

    /**
     * 修改入住信息
     */
    @PreAuthorize("@ss.hasPermi('nursing:checkIn:edit')")
    @Log(title = "入住信息", businessType = BusinessType.UPDATE)
    @PutMapping
    @Operation(summary = "修改入住信息")
    public AjaxResult edit(@RequestBody CheckIn checkIn)
    {
        return toAjax(checkInService.updateCheckIn(checkIn));
    }

    /**
     * 删除入住信息
     */
    @PreAuthorize("@ss.hasPermi('nursing:checkIn:remove')")
    @Log(title = "入住信息", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    @Operation(summary = "删除入住信息")
    public AjaxResult remove(@Schema(name = "入住信息ID", requiredMode = Schema.RequiredMode.REQUIRED) @PathVariable Long[] ids)
    {
        return toAjax(checkInService.deleteCheckInByIds(ids));
    }

    /**
     * 申请入住
     * <p>
     * 校验评估是否完成 → 校验老人是否已入住 → 校验床位状态 → 占床 → 老人建档/更新
     * → 生成合同 → 新增入住（家属JSON）→ 新增入住配置 → 回写评估表与评估结果表
     * <p>
     * 防重复提交第一层：@RepeatSubmit 基于（URL + token + 请求参数）在 Redis 里做 10 秒窗口去重，
     * 连续点击「提交」只会执行一次。
     */
    @RepeatSubmit(interval = 10000, message = "请勿重复提交，正在办理入住")
    @Log(title = "申请入住", businessType = BusinessType.INSERT)
    @PostMapping("/apply")
    @Operation(summary = "申请入住")
    public AjaxResult apply(@RequestBody CheckInApplyDto dto)
    {
        return success(checkInService.applyCheckIn(dto));
    }

    /**
     * 查询入住详情（老人 + 家属 + 入住配置 + 合同）
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "查询入住详情")
    public AjaxResult detail(@Schema(name = "入住信息ID", requiredMode = Schema.RequiredMode.REQUIRED)
            @PathVariable("id") Long id)
    {
        CheckInDetailVo vo = checkInService.selectCheckInDetail(id);
        return success(vo);
    }
}
