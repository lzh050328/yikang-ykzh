package com.xhzb.nursing.domain.dto.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 家属信息（申请入住时提交，最终以 JSON 形式存到 check_in.remark）
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "家属信息")
public class ElderFamilyDto {

    @Schema(title = "家属姓名")
    private String name;

    @Schema(title = "家属电话")
    private String phone;

    @Schema(title = "与老人关系 0子女 1配偶 2亲属 3朋友 4社工 5其他")
    private String kinship;
}
