-- ============================================================
-- 星海智询 -> Dify-AI 智能体对话菜单
-- 前端页面：xhzb-ui/src/views/nursing/difyAi/index.vue
-- 后端接口：/ai/dify/**（已在 SecurityConfig 中放行）
-- 依赖：「星海智询」目录已存在（menu_id = 2052，见 xhzb.sql）
-- ============================================================

-- 0. 先确认父级目录存在
SELECT menu_id, menu_name, parent_id, path FROM sys_menu WHERE menu_id = 2052;

-- 1. 新增 Dify-AI 菜单（若同一父级下已有 difyAi 路径则可跳过本步）
--    menu_id 交给自增分配，避免与线上库已有主键冲突
INSERT INTO `sys_menu`
(`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `route_name`, `is_frame`, `is_cache`,
 `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT 'Dify-AI', 2052, 3, 'difyAi', 'nursing/difyAi/index', NULL, '', 1, 0,
       'C', '0', '0', '', 'education', 'admin', NOW(), '', NULL, 'Dify-AI 智能体对话'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE parent_id = 2052 AND path = 'difyAi') t);

-- 2. 若菜单已存在（历史数据），只修正前端组件路径即可
UPDATE `sys_menu`
SET `component` = 'nursing/difyAi/index', `menu_name` = 'Dify-AI', `visible` = '0', `status` = '0', `update_time` = NOW()
WHERE `parent_id` = 2052 AND `path` = 'difyAi';

-- 3. 给超级管理员角色授权（role_id = 1）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.menu_id
FROM `sys_menu` m
WHERE m.`parent_id` = 2052 AND m.`path` = 'difyAi'
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu WHERE role_id = 1) t WHERE t.menu_id = m.menu_id);

-- 4. 校验结果
SELECT m.menu_id, m.menu_name, m.parent_id, m.path, m.component, m.visible, m.status
FROM sys_menu m
WHERE m.parent_id = 2052
ORDER BY m.order_num;
