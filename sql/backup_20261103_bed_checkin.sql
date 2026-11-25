-- 备份快照（执行前现状，2026-11-03），用于核验与回退
-- 来源：MySQL localhost / 库 xhzb
--
-- bed
-- 1 | 101-1 | bed_status=1
-- 2 | 102-1 | bed_status=2
-- elder
-- 97 | 张博 | bed_id=NULL | bed_number=NULL
-- 98 | 王五 | bed_id=NULL | bed_number=NULL
-- check_in
-- 10 | 张博 | 101-1 | status=0   | start=2026-10-01 | end=NULL
-- 11 | 王五 | 102-1 | status=1   | start=2026-08-01 | end=2026-09-30
-- health_assessment
-- 23 | 张博 | check_in_status=0 | elder_id=NULL
-- 29 | 王五 | check_in_status=0 | elder_id=NULL
-- health_assessment_report
-- 11 | health_assessment_id=23 | check_in_status=0
-- 14 | health_assessment_id=29 | check_in_status=0

-- ================= 回滚语句 =================
-- 如需撤销本次回填，按以下语句执行：
update bed set bed_status = 2 where id = 2;
update bed set bed_status = 1 where id = 1;
update elder set bed_id = null, bed_number = null where id in (97, 98);
update check_in set status = 1, end_date = '2026-09-30' where id = 11;
update health_assessment set check_in_status = 0, elder_id = null where id in (23, 29);
update health_assessment_report set check_in_status = 0 where id in (11, 14);
