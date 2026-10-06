-- ======================================================================
-- 复测与巡查安排单·夜间走表：作业挂点 + 存量老条目兜底
--
-- 可重复执行：作业一行用 INSERT IGNORE，兜底两句是幂等 UPDATE。
-- 适用库：各环境业务库（与 t_sys_quartz_job、t_nslp_task_item 同库）。
--
-- 口径只在代码一处（com.fc.v2.service.support.TaskDispatchRules），
-- 本文件只管「把作业挂上」与「把老系统带过来的条目兜住」，不另立口径。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 一、夜间走表作业挂点（Quartz 启动时从 t_sys_quartz_job 读作业并注册）
-- ----------------------------------------------------------------------
-- invoke_target : Spring 受管 Bean「nslpDispatchTask」的无参方法 runNightly()
-- cron          : 每天 02:07 唤起一趟（避开整点扎堆；定额 500 装满可再加一趟）
-- misfire_policy: 3 = 错过不补（与 QuartzSchedulerUtil 里写死的
--                 withMisfireHandlingInstructionDoNothing 一致）：夜里没跑成就等
--                 明晚，不在白天补跑——白天唤起也只会答话不动单
-- concurrent    : 1 = 禁止并发（同一时刻只许一趟在单上；并着两趟也由条件更新兜住
--                 幂等，这里再加一道闸）
-- status        : 0 = 正常（启用）；要停就把它改成 1，不要删 Bean
-- 注意：作业只在应用启动时从本表注册一次；本表插入后需重启应用，或在调度管理页
-- 新增同样的作业，Quartz 才会挂上。
INSERT IGNORE INTO `t_sys_quartz_job`
  (`id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`,
   `misfire_policy`, `concurrent`, `status`,
   `create_by`, `create_time`, `remark`)
VALUES
  (900100000000000001, '安排单夜间走表', 'NSLP', 'nslpDispatchTask.runNightly()',
   '0 7 2 * * ?', '3', '1', 0,
   '程序夜发', NOW(),
   '复测与巡查安排单夜里走一趟；只认02:00-05:00窗口，窗外唤起只答话不动单；一趟至多500行');

-- 若定额 500 不够装，可在窗口内再加一趟（同一 invoke_target，换一个钟点与新 id）：
-- INSERT IGNORE INTO `t_sys_quartz_job`
--   (`id`,`job_name`,`job_group`,`invoke_target`,`cron_expression`,`misfire_policy`,`concurrent`,`status`,`create_by`,`create_time`,`remark`)
-- VALUES
--   (900100000000000002, '安排单夜间走表(第二趟)', 'NSLP', 'nslpDispatchTask.runNightly()',
--    '0 37 3 * * ?', '3', '1', 0, '程序夜发', NOW(), '窗口内第二趟，接着挑上一趟剩下的');

-- ----------------------------------------------------------------------
-- 二、存量老条目兜底（老系统带过来的行，字段空法五花八门）
--
-- 新逻辑的取舍（钉死，与 TaskDispatchRules 一致）：
--   · status 空着：新逻辑不认它是「搁着」，会永远捞不着 → 统一补成 0（搁着），
--     之后与今年新建的行走同一条路径，不因进库早晚分两条道；
--   · del_flag 空着：代码本来就当正常，这里同样落成 0，免得看着悬；
--   · amount 空着：语义就是「不许提前」，与 0 完全等价，保留 NULL 不动；
--   · due_at 空着：没有到期那一刻，永不参与走表（也不会报错），需业务补日期；
--   · create_by 空着：挑得中但发不成——本趟标 status=2「发不成」并在 remark 写原由，
--     其余行不受影响。补好人名后按下面第三节把行放回「搁着」，下趟重发。
-- ----------------------------------------------------------------------
UPDATE `t_nslp_task_item` SET `status` = 0 WHERE `status` IS NULL;
UPDATE `t_nslp_task_item` SET `del_flag` = 0 WHERE `del_flag` IS NULL;

-- ----------------------------------------------------------------------
-- 三、发不成行的恢复（运维手册用，不要无脑执行）
--
-- status=2 表示这一行本趟站不住（如没人名），以后各趟不再碰它。
-- 缺什么补什么（典型是补 create_by），补完把这一行放回「搁着」：
--
-- UPDATE t_nslp_task_item
--    SET status = 0, remark = NULL, update_by = '<经手岗>', update_time = NOW()
--  WHERE id = <行主键> AND status = 2;
-- ----------------------------------------------------------------------
