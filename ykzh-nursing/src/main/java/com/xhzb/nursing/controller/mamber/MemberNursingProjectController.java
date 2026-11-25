package com.xhzb.nursing.controller.mamber;

import java.util.List;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xhzb.common.core.controller.BaseController;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.common.exception.ServiceException;
import com.xhzb.nursing.domain.NursingProject;
import com.xhzb.nursing.service.INursingProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端服务项（护理项目）
 *
 * @author lzh
 * @date 2026-10-30
 */
@RestController
@RequestMapping("/member/orders/project")
@Tag(name = "小程序端服务项")
public class MemberNursingProjectController extends BaseController {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private INursingProjectService nursingProjectService;

    /**
     * 分页查询护理项目列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询护理项目列表")
    public AjaxResult page(NursingProject query) {
        startPage();
        QueryWrapper<NursingProject> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getName()), "name", query.getName())
                .eq("status", query.getStatus() == null ? STATUS_ENABLED : query.getStatus())
                .orderByAsc("order_no")
                .orderByAsc("id");
        List<NursingProject> list = nursingProjectService.list(wrapper);
        return success(getDataTable(list));
    }

    /**
     * 根据编号查询护理项目信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询护理项目信息")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        NursingProject project = nursingProjectService.getById(id);
        if (project == null || project.getStatus() == null || project.getStatus() != STATUS_ENABLED) {
            throw new ServiceException("护理项目不存在或已下架");
        }
        return success(project);
    }
}
