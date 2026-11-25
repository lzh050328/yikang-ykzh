package com.xhzb.nursing.domain.vo;

import com.xhzb.nursing.domain.Contract;
import com.xhzb.nursing.domain.Elder;
import com.xhzb.nursing.domain.dto.checkin.ElderFamilyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 入住详情（对应前端 details.vue 只读模式的数据结构）
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "入住详情")
public class CheckInDetailVo {

    @Schema(title = "老人基本信息")
    private Elder checkInElderVo;

    @Schema(title = "家属信息列表")
    private List<ElderFamilyDto> elderFamilyVoList;

    @Schema(title = "入住配置")
    private CheckInConfigVo checkInConfigVo;

    @Schema(title = "合同信息")
    private Contract contract;
}
