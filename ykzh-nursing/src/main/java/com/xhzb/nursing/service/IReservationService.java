package com.xhzb.nursing.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhzb.nursing.domain.Reservation;
import com.xhzb.nursing.domain.dto.member.ReservationCreateDto;
import com.xhzb.nursing.domain.vo.ReservationTimeVo;

/**
 * 预约信息Service接口
 *
 * @author lzh
 * @date 2026-10-30
 */
public interface IReservationService extends IService<Reservation>
{
    /**
     * 查询当天取消预约次数
     *
     * @param memberId 家属id
     * @return 次数
     */
    long getCancelledCount(Long memberId);

    /**
     * 查询某天各时段剩余预约次数
     *
     * @param date 日期，yyyy-MM-dd，可空
     * @param time 日期毫秒/秒时间戳（小程序端传的是时间戳），可空
     * @return 该天全部时段的剩余次数
     */
    List<ReservationTimeVo> countByTime(String date, String time);

    /**
     * 新增预约
     *
     * @param memberId 家属id
     * @param dto      预约参数
     * @return 预约id
     */
    Long createReservation(Long memberId, ReservationCreateDto dto);

    /**
     * 查询当前家属的预约列表（配合 startPage 分页）
     *
     * @param memberId 家属id
     * @param status   预约状态，可空
     * @return 预约集合
     */
    List<Reservation> selectMemberReservationList(Long memberId, Integer status);

    /**
     * 取消预约
     *
     * @param memberId 家属id
     * @param id       预约id
     */
    void cancelReservation(Long memberId, Long id);
}
