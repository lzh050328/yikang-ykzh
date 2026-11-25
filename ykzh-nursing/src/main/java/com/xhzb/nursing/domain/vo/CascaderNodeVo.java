package com.xhzb.nursing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 通用级联节点（楼层 → 房间 → 床位）
 * <p>
 * 前端 el-cascader 默认按 value / label / children 三个字段渲染，
 * device 页面也是按这三个字段遍历（见 device/index.vue 的 handleFloor），
 * 因此这里统一用这套命名，而不是 id / name / roomVoList。
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "级联节点（楼层/房间/床位）")
public class CascaderNodeVo {

    @Schema(title = "节点值（楼层ID/房间ID/床位ID）")
    private String value;

    @Schema(title = "显示名称（楼层名/房间编号/床位编号）")
    private String label;

    @Schema(title = "子节点")
    private List<CascaderNodeVo> children;
}
