package com.xhzb.nursing.domain.vo;

import com.xhzb.nursing.domain.CheckInConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 入住配置展示对象
 * <p>
 * 在 CheckInConfig 基础上补充入住起止日期、床位编号（这些字段在 check_in 表里），
 * 供前端「入住配置」只读区块使用。
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "入住配置展示对象")
public class CheckInConfigVo extends CheckInConfig {

    @Schema(title = "入住开始日期")
    private String startDate;

    @Schema(title = "入住结束日期")
    private String endDate;

    @Schema(title = "床位/房间编号")
    private String bedNumber;
}
