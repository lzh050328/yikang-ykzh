package com.xhzb.nursing.controller.mamber;

import java.util.List;

import com.xhzb.common.core.controller.BaseController;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.nursing.domain.dto.member.ReservationCreateDto;
import com.xhzb.nursing.domain.vo.ReservationTimeVo;
import com.xhzb.nursing.service.IReservationService;
import com.xhzb.nursing.util.MemberHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端参观预约
 *
 * @author lzh
 * @date 2026-10-30
 */
@RestController
@RequestMapping("/member/reservation")
@Tag(name = "小程序端参观预约")
public class MemberReservationController extends BaseController {

    @Autowired
    private IReservationService reservationService;

    /**
     * 查询取消预约数量（当天）
     */
    @GetMapping("/cancelled-count")
    @Operation(summary = "查询取消预约数量")
    public AjaxResult cancelledCount() {
        return success(reservationService.getCancelledCount(MemberHolder.getMemberId()));
    }

    /**
     * 查询每个时间段剩余预约次数
     *
     * @param date 日期 yyyy-MM-dd（可选）
     * @param time 日期时间戳，小程序端传的是毫秒时间戳（可选，与 date 二选一）
     */
    @GetMapping("/countByTime")
    @Operation(summary = "查询每个时间段剩余预约次数")
    public AjaxResult countByTime(@RequestParam(value = "date", required = false) String date,
                                  @RequestParam(value = "time", required = false) String time) {
        List<ReservationTimeVo> list = reservationService.countByTime(date, time);
        return success(list);
    }

    /**
     * 新增预约
     */
    @PostMapping({"", "/"})
    @Operation(summary = "新增预约")
    public AjaxResult add(@Validated @RequestBody ReservationCreateDto dto) {
        reservationService.createReservation(MemberHolder.getMemberId(), dto);
        return success();
    }

    /**
     * 分页查询预约（只看自己的）
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预约")
    public AjaxResult page(@RequestParam(value = "status", required = false) Integer status) {
        startPage();
        return success(getDataTable(
                reservationService.selectMemberReservationList(MemberHolder.getMemberId(), status)));
    }

    /**
     * 取消预约
     */
    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消预约")
    public AjaxResult cancel(@PathVariable("id") Long id) {
        reservationService.cancelReservation(MemberHolder.getMemberId(), id);
        return success();
    }
}
