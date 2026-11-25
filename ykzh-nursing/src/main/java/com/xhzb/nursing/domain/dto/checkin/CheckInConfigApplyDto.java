package com.xhzb.nursing.domain.dto.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 入住配置（申请入住时的费用配置）
 * <p>
 * 日期统一用字符串接收（前端 el-date-picker 传的是 yyyy-MM-dd），
 * 由后端解析成 LocalDateTime，避免 Jackson 直接反序列化失败。
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "入住配置")
public class CheckInConfigApplyDto {

    @Schema(title = "护理等级ID")
    private Long nursingLevelId;

    @Schema(title = "护理等级名称")
    private String nursingLevelName;

    @Schema(title = "床位ID（级联选择的第三级）")
    private Long bedId;

    @Schema(title = "床位/房间编号")
    private String bedNumber;

    @Schema(title = "楼层ID")
    private Long floorId;

    @Schema(title = "房间ID")
    private Long roomId;

    @Schema(title = "押金")
    private BigDecimal deposit;

    @Schema(title = "护理费")
    private BigDecimal nursingFee;

    @Schema(title = "床位费")
    private BigDecimal bedFee;

    @Schema(title = "医保支付")
    private BigDecimal insurancePayment;

    @Schema(title = "政府补贴")
    private BigDecimal governmentSubsidy;

    @Schema(title = "其他费用")
    private BigDecimal otherFees;

    @Schema(title = "入住开始日期")
    private String startDate;

    @Schema(title = "入住结束日期")
    private String endDate;

    @Schema(title = "费用开始日期")
    private String feeStartDate;

    @Schema(title = "费用结束日期")
    private String feeEndDate;
}
