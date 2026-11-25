package com.xhzb.nursing.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 小程序端预约相关配置（application.yml 中的 member.reservation 节点）
 *
 * 注意：时段必须与小程序端 TimeSelect 组件（utils/commonData.js 里的 timeBaseData）保持一致，
 * 否则前端能选、后端会以“不在可预约时段内”拒掉。
 *
 * @author lzh
 */
@Data
@Component
@ConfigurationProperties(prefix = "member.reservation")
public class MemberReservationProperties {

    /** 每个时段可预约人数 */
    private int capacity = 6;

    /** 每日可预约时段（HH:mm） */
    private List<String> slots = new ArrayList<>(Arrays.asList(
            "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00",
            "11:30", "12:00", "12:30", "13:00", "13:30", "14:00", "14:30",
            "15:00", "15:30", "16:00", "16:30", "17:00", "17:30", "18:00"));

    /** 当天取消预约次数上限，超过则不允许再预约 */
    private int cancelledLimit = 3;

    /** 最多可提前几天预约，0 表示不限制 */
    private int maxAdvanceDays = 0;
}
