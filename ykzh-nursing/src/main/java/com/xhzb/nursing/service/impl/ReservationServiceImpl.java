package com.xhzb.nursing.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhzb.common.exception.ServiceException;
import com.xhzb.nursing.config.MemberReservationProperties;
import com.xhzb.nursing.domain.Reservation;
import com.xhzb.nursing.domain.dto.member.ReservationCreateDto;
import com.xhzb.nursing.domain.vo.ReservationTimeVo;
import com.xhzb.nursing.mapper.ReservationMapper;
import com.xhzb.nursing.service.IReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约信息Service业务层处理
 *
 * @author lzh
 * @date 2026-10-30
 */
@Slf4j
@Service
public class ReservationServiceImpl extends ServiceImpl<ReservationMapper, Reservation> implements IReservationService
{
    /** 预约状态：待报道 */
    private static final int STATUS_WAITING = 0;

    /** 预约状态：已完成 */
    private static final int STATUS_FINISHED = 1;

    /** 预约状态：取消 */
    private static final int STATUS_CANCELLED = 2;

    /** 预约状态：过期 */
    private static final int STATUS_EXPIRED = 3;

    /** 新增预约支持的时间格式：空格分隔（文档）与 ISO 的 T 分隔 */
    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    /** 返回给小程序端的时段格式：ISO 的 T 分隔，小程序 new Date() 在 iOS 上也能正确解析 */
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");


    @Autowired
    private MemberReservationProperties reservationProperties;

    /**
     * 查询当天取消预约次数（按取消动作发生的时间统计）
     *
     * @param memberId 家属id
     * @return 次数
     */
    @Override
    public long getCancelledCount(Long memberId)
    {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.eq("create_by", memberId)
                .eq("status", STATUS_CANCELLED)
                .ge("update_time", todayStart);
        return count(wrapper);
    }

    /**
     * 查询某天各时段剩余预约次数（返回当天全部时段，前端只根据 count==0 判断“已约满”）
     *
     * @param date 日期，可空
     * @param time 时间戳，可空
     * @return 时段剩余次数
     */
    @Override
    public List<ReservationTimeVo> countByTime(String date, String time)
    {
        LocalDate day = resolveDay(date, time);
        log.info("countByTime 入参 date={}, time={}, 解析日期={}", date, time, day);

        // 已占用名额：该天、待报道/已完成 的预约
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.ge("time", day.atStartOfDay())
                .lt("time", day.plusDays(1).atStartOfDay())
                .in("status", STATUS_WAITING, STATUS_FINISHED);
        List<Reservation> booked = list(wrapper);

        // 按“时分”聚合，忽略秒
        Map<LocalTime, Long> usedMap = new HashMap<>();
        for (Reservation reservation : booked)
        {
            LocalTime slot = reservation.getTime().toLocalTime().withSecond(0).withNano(0);
            usedMap.merge(slot, 1L, Long::sum);
        }

        int capacity = reservationProperties.getCapacity();
        List<ReservationTimeVo> result = new ArrayList<>();
        for (String slotText : reservationProperties.getSlots()) 
        {
            LocalTime slot = parseSlot(slotText);
            if (slot == null)
            {
                continue;
            }
            long used = usedMap.getOrDefault(slot, 0L);
            long remain = Math.max(capacity - used, 0);
            LocalDateTime slotTime = LocalDateTime.of(day, slot);
            result.add(new ReservationTimeVo(slotTime.format(OUTPUT_FORMAT), (int) remain));
        }
        return result;
    }

    /**
     * 新增预约
     *
     * @param memberId 家属id
     * @param dto      预约参数
     * @return 预约id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReservation(Long memberId, ReservationCreateDto dto)
    {
        log.info("createReservation 入参 memberId={}, dto={}", memberId, dto);
        LocalDateTime wanted = parseTime(dto.getTime());
        validateBookableTime(wanted);

        // 1、当天取消次数已达上限
        long cancelled = getCancelledCount(memberId);
        if (cancelled > reservationProperties.getCancelledLimit())
        {
            throw new ServiceException("今日取消预约次数已达上限，请明日再试");
        }

        // 2、该时段名额是否充足
        QueryWrapper<Reservation> usedWrapper = new QueryWrapper<>();
        usedWrapper.eq("time", wanted).in("status", STATUS_WAITING, STATUS_FINISHED);
        long used = count(usedWrapper);
        if (used >= reservationProperties.getCapacity())
        {
            throw new ServiceException("该时段已约满，请选择其他时间");
        }

        // 3、同一手机号同一时段唯一（库里有唯一键，先查一次给出友好提示）
        QueryWrapper<Reservation> existWrapper = new QueryWrapper<>();
        existWrapper.eq("mobile", dto.getMobile()).eq("time", wanted).last("limit 1");
        Reservation exist = getOne(existWrapper, false);
        if (exist != null)
        {
            if (exist.getStatus() == STATUS_CANCELLED || exist.getStatus() == STATUS_EXPIRED)
            {
                // 取消/过期后重约同一时段：复用原记录，避免撞唯一键
                exist.setName(dto.getName());
                exist.setVisitor(dto.getVisitor());
                exist.setType(dto.getType());
                exist.setStatus(STATUS_WAITING);
                exist.setUpdateBy(String.valueOf(memberId));
                exist.setUpdateTime(new Date());
                updateById(exist);
                return exist.getId();
            }
            throw new ServiceException("该手机号在该时段已有预约，请勿重复提交");
        }

        Reservation reservation = new Reservation();
        reservation.setName(dto.getName());
        reservation.setMobile(dto.getMobile());
        reservation.setTime(wanted);
        reservation.setVisitor(dto.getVisitor());
        reservation.setType(dto.getType());
        reservation.setStatus(STATUS_WAITING);
        reservation.setCreateBy(String.valueOf(memberId));
        reservation.setCreateTime(new Date());
        try
        {
            save(reservation);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("该手机号在该时段已有预约，请勿重复提交");
        }
        return reservation.getId();
    }

    /**
     * 查询当前家属的预约列表
     *
     * @param memberId 家属id
     * @param status   预约状态，可空
     * @return 预约集合
     */
    @Override
    public List<Reservation> selectMemberReservationList(Long memberId, Integer status)
    {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.eq("create_by", memberId)
                .eq(status != null, "status", status)
                .orderByDesc("time")
                .orderByDesc("id");
        return list(wrapper);
    }

    /**
     * 取消预约
     *
     * @param memberId 家属id
     * @param id       预约id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReservation(Long memberId, Long id)
    {
        Reservation reservation = getById(id);
        // 越权与不存在返回同一提示，避免泄露预约是否存在
        if (reservation == null || !String.valueOf(memberId).equals(reservation.getCreateBy()))
        {
            throw new ServiceException("预约不存在");
        }
        if (reservation.getStatus() == STATUS_CANCELLED)
        {
            throw new ServiceException("该预约已取消，请勿重复操作");
        }
        if (reservation.getStatus() != STATUS_WAITING)
        {
            throw new ServiceException("该预约已完成或已过期，无法取消");
        }
        reservation.setStatus(STATUS_CANCELLED);
        reservation.setUpdateBy(String.valueOf(memberId));
        reservation.setUpdateTime(new Date());
        updateById(reservation);
    }

    /**
     * 校验预约时间：必须落在配置的时段内、必须是将来、且不超过最大提前天数
     */
    private void validateBookableTime(LocalDateTime wanted)
    {
        LocalTime slot = wanted.toLocalTime().withSecond(0).withNano(0);
        boolean matched = false;
        for (String slotText : reservationProperties.getSlots())
        {
            if (slot.equals(parseSlot(slotText)))
            {
                matched = true;
                break;
            }
        }
        if (!matched)
        {
            throw new ServiceException("预约时间不在可预约时段内，请重新选择");
        }
        if (!wanted.isAfter(LocalDateTime.now()))
        {
            throw new ServiceException("预约时间已过，请选择其他时间");
        }
        // 0 表示不限制
        if (reservationProperties.getMaxAdvanceDays() > 0)
        {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), wanted.toLocalDate());
            if (days > reservationProperties.getMaxAdvanceDays())
            {
                throw new ServiceException("最多可提前" + reservationProperties.getMaxAdvanceDays() + "天预约");
            }
        }
    }

    /**
     * 解析预约时间，兼容空格分隔与 ISO 的 T 分隔
     */
    private LocalDateTime parseTime(String text)
    {
        if (StrUtil.isBlank(text))
        {
            throw new ServiceException("预约时间不能为空");
        }
        String value = text.trim();
        for (DateTimeFormatter format : TIME_FORMATS)
        {
            try
            {
                return LocalDateTime.parse(value, format);
            }
            catch (DateTimeParseException ignored)
            {
                // 尝试下一种格式
            }
        }
        throw new ServiceException("预约时间格式不正确，应为 yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 解析查询日期，支持 yyyy-MM-dd 或完整时间
     */
    private LocalDate parseDay(String date)
    {
        if (StrUtil.isBlank(date))
        {
            return LocalDate.now();
        }
        String value = date.trim();
        if (value.length() >= 10)
        {
            value = value.substring(0, 10);
        }
        try
        {
            return LocalDate.parse(value);
        }
        catch (DateTimeParseException e)
        {
            throw new ServiceException("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    /**
     * 解析查询日期：优先 date（yyyy-MM-dd），其次 time（小程序端传的是毫秒时间戳）
     */
    private LocalDate resolveDay(String date, String time)
    {
        if (StrUtil.isNotBlank(date))
        {
            return parseDay(date);
        }
        if (StrUtil.isBlank(time))
        {
            return LocalDate.now();
        }
        String value = time.trim();
        // 10 位是秒级时间戳，13 位是毫秒级
        if (value.matches("\\d{10,13}"))
        {
            long epoch = Long.parseLong(value);
            if (value.length() == 10)
            {
                epoch = epoch * 1000;
            }
            return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return parseDay(value);
    }

    /**
     * 解析配置中的时段（HH:mm），非法配置返回 null
     */
    private LocalTime parseSlot(String slotText)
    {
        if (StrUtil.isBlank(slotText))
        {
            return null;
        }
        try
        {
            return LocalTime.parse(slotText.trim());
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }
}
