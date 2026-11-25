package com.xhzb.nursing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 老人信息详情（入住流程字段填充）
 * <p>
 * 数据来源：健康评估主表（姓名/身份证/核心建议）+ 评估采集表的 basicInfo JSON。
 * 说明：elder（老人档案）表目前为空且项目内暂无对应实体/Mapper，
 * 等办理入住时创建老人档案后，可将本接口改为优先读 elder 表。
 *
 * @author lzh
 * @date 2026-10-28
 */
@Data
@Schema(description = "老人信息详情（入住流程字段填充）")
public class ElderInfoVO {

    @Schema(title = "联系电话")
    private String phone;

    @Schema(title = "医疗费用支付方式")
    private String medicalPaymentMethod;

    @Schema(title = "核心建议，0-不建议入住，1-建议入住")
    private Integer coreSuggestion;

    @Schema(title = "民族")
    private String nation;

    @Schema(title = "文化程度")
    private String educationLevel;

    @Schema(title = "身份证号")
    private String idCardNo;

    @Schema(title = "老人姓名")
    private String name;

    @Schema(title = "社保卡号")
    private String socialSecurityCard;

    @Schema(title = "居住情况")
    private String livingSituation;

    @Schema(title = "宗教信仰")
    private String religiousBelief;

    @Schema(title = "经济来源")
    private String economicSource;

    @Schema(title = "婚姻状况")
    private String maritalStatus;
}
