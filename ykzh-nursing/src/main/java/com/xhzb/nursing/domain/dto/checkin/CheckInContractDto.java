package com.xhzb.nursing.domain.dto.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 签约办理信息（申请入住时提交的合同信息）
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "签约办理信息")
public class CheckInContractDto {

    @Schema(title = "合同名称")
    private String contractName;

    @Schema(title = "签约日期 yyyy-MM-dd")
    private String signDate;

    @Schema(title = "第三方（家属）姓名")
    private String thirdPartyName;

    @Schema(title = "第三方（家属）电话")
    private String thirdPartyPhone;

    @Schema(title = "合同文件地址")
    private String agreementPath;
}
