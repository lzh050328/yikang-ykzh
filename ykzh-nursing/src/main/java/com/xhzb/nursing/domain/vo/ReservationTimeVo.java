package com.xhzb.nursing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预约时段剩余名额
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "预约时段剩余名额")
public class ReservationTimeVo {

    /** 时段，如 2026-10-31 10:30:00 */
    @Schema(title = "时段")
    private String time;

    /** 剩余可预约次数 */
    @Schema(title = "剩余可预约次数")
    private Integer count;
}
