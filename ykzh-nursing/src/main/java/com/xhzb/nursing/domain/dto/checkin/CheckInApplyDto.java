package com.xhzb.nursing.domain.dto.checkin;

import com.xhzb.nursing.domain.Elder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 申请入住入参（对应前端 details.vue 的提交结构）
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "申请入住入参")
public class CheckInApplyDto {

    @Schema(title = "健康评估ID")
    private Long healthAssessmentId;

    @Schema(title = "老人基本信息")
    private Elder checkInElderDto;

    @Schema(title = "家属信息列表")
    private List<ElderFamilyDto> elderFamilyDtoList;

    @Schema(title = "入住配置")
    private CheckInConfigApplyDto checkInConfigDto;

    @Schema(title = "签约办理")
    private CheckInContractDto checkInContractDto;
}
