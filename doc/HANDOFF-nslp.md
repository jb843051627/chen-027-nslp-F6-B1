# NSLP 业务接手说明（口径·走表·运维）

> 给新接手的人：这个模块的规矩是「**一件事只有一处口径**」。
> 每个业务都有一个不碰库、不沾 Spring 的纯口径类（`service/support/*Rules.java`），
> Service 只落库与装配，Controller/页面/定时作业只是把手。改规则只动 Rules，
> 并先看对应测试；在别处另算一套就是历次事故的根。

最后更新：2026-10-06

---

## 1. 复测与巡查安排单·夜间走表（本次重灾区，先读这节）

复测通知与现场巡查**同一张表** `t_nslp_task_item`，一行一事，夜里走一趟。

### 1.1 链路（只有这一条，没有第二条）

```
Quartz 作业 nslpDispatchTask.runNightly()  ──┐
                                              ├─→ ITNslpTaskItemDispatchService.runOnce(Date)
页面 POST /nslpTaskItemDispatch/run ─────────┘        └─ TNslpTaskItemDispatchServiceImpl
                                                              └─ 口径只问 TaskDispatchRules
```

- 定时挂点：`t_sys_quartz_job` 里 `invoke_target = 'nslpDispatchTask.runNightly()'`，
  挂点 SQL 在 `doc/schema/nslp_task_dispatch_seed.sql`（cron `0 7 2 * * ?`，禁并发，错过不补）。
  作业只在**应用启动时**注册一次：insert 后要重启应用，或在调度管理页手工新增。
- 手工把手只有页面那一个（权限点 `nslpTaskItemDispatch:run`）。不要新写第二个触发口。
- 老的 `ITNslpTaskItemService / TNslpTaskItemServiceImpl`（scheduling-job 形状那套）
  **已整根删除**。历史版本里它窗口外也动单、空单 `get(0)` 越界、已处理的行重复扫、
  恰好到点用 `<` 判否、一条异常整批中断——以后谁也别从 git 历史里把它捞回来用。

### 1.2 口径（全部在 `TaskDispatchRules`，改这里一处即可）

| 事项 | 定死的规矩 |
|---|---|
| 走表窗口 | 只认 **02:00（含）到 05:00（不含）**。窗外被任何方式唤起（补触发、调度页点「执行一次」、cron 配错），只回「本轮未改动安排单」，单上一行不动；连按几回答话一字不差 |
| 到期边界 | 到期那一刻**走到本次这一刻就算到，恰好等于也算**（`<=`，不是 `<`）。挑单与回写同用这一条 |
| 提前日数 | 只看本行 `amount` 一格：空/0/负数=不提前；零头向零取整；周六周日（停办日）、法定节假日不数；至多往回 3660 日。节假日表是 2026 一册，新年通知下来只改 `statutoryHolidays2026()` 一处 |
| 只挑搁着的 | `status=0 且 del_flag=0`。已发出(1)、发不成(2) 永不重扫；更新语句再带「仍搁着」条件，并发两趟同一行只成一回（幂等） |
| 排序 | 最届期在前，同刻按主键；回回排得一样 |
| 定额 | 一趟 500 行，装满即止；剩下的什么也不标，下趟接着挑。窗口内可加第二趟（见挂点 SQL 注释） |
| 办成的记号 | **同一句 UPDATE 落两处**：status→1；content 尾部追加「［已发出 yyyy-MM-dd HH:mm:ss 发给 人名］」。只改一处会导致下趟重发，禁止拆成两句 |
| 发给谁 | 只认本行 `create_by`，空着/纯空白→这一行发不成 |
| 发不成 | 本行 status→2、remark 写「发不成：<原由>」（500 字封顶）；**其余行照常办，整趟不中断、不回滚已发出的行**；标记本身失败只 warn，行仍搁着等下趟 |
| 趟末回话 | `TaskDispatchResult.of(picked, sent, failed)` 三个数与逐行落记号同一遍累计，答话只由这三个数放出（`replyOf`），不许另起一轮逐行数来对冲 |
| 落账人名 | update_by 固定「程序夜发」，update_time=本次按下时刻 |

注意 **picked 与 sent/failed 不必相等**：并发被别的趟抢先落记号的行，计入 picked 但
既不算发出也不算没成——这是设计，不是数对不上。

### 1.3 存量老数据的取舍（老系统搬进来的行，定死这样走）

- 老条目与新条目**同一条路径、同一套挑法**，不按进库时间分道（测试 `legacyAndNewRowsWalkTheSamePath`）。
- `status` 为 NULL：新逻辑不认它是「搁着」，会永远捞不到 → 上线时跑挂点 SQL 里的
  兜底 `UPDATE ... SET status=0 WHERE status IS NULL` 补成搁着。
- `del_flag` 为 NULL：代码当正常，SQL 顺手补 0。
- `amount` 为 NULL：语义就是「不提前」，保留 NULL，不要批量填数。
- `due_at` 为 NULL：永不参与、也不报错；需要业务补日期。
- `create_by` 为空：挑得中但会标「发不成」；补人名后按 SQL 文件第三节把行放回 status=0，下趟重发。

---

## 2. 其它业务的「唯一口径」地图

| 业务 | 口径类（只在这里下结论） | 一句话 |
|---|---|---|
| 点位三态流转 | `PointFlowRules` | 待测→已测→压卡，压卡到头动不得；当下步只由 `t_nslp_point_step` 留痕**回放**得出，旧行无留痕=null（不是压卡）；名册分档只消费 `TNslpPointServiceImpl.currentStageMap` 这一处 |
| 投诉/超标办件 | `CaseFlowRules` | 受件→核查→责令→复查收尾；只许走紧邻一关；档数只由留痕回放得出；进复查收尾必须责令关记「可」 |
| 监测流水核收 | `FlowRowRules` | 点行数→验数据→定去处→出回执一遍走完；同批次码第二回只照面；抽行复核（`reconcile`）抬头数、流水数、收下/退回数同一次数，印不上的逐行点名 |
| 点位名册 | `LedgerRules` | 三种摆法共用 `baseRows` 一份底数，total 不一致直接抛错（出现即说明有人绕开了同源查询）；页大小只取区局配置 |
| 建册/改线 | `PointArchiveRules` | 五道筛；建册与改线两处都指不出人的行进 `listUnattributed` 单列，不进底数、不删除 |
| 签批单 / 限值线 / 文书 | `PermitBillRules` / `LimitLineRules` / `DocTplRules` | 同样的 Rules 在前、Service 落库形状，要改先读类头注释与同名测试 |

「两处口径合不上」类需求的标准修法：把判断挪进对应的 Rules，两处都调它；
不要在第二处把算法再抄一遍。

## 3. 怎么验证

- 全套单测：`mvn -o test`（158 个用例；构建目录在 9p 挂载上偶发「Operation not
  permitted」时，删掉 `target/` 重跑即可）。
- 走表相关边界集中在：
  `TaskDispatchRulesTest`（11 项）、`TNslpTaskItemDispatchServiceImplTest`（18 项，
  含空单、恰好到点、已处理不重发、并发抢行、单条失败不连累、02:00/05:00 边界、
  新老条目同路径、定额 500+1）。
- 改口径必须先加/改这些用例，用内存账 mock mapper，不依赖真库。

## 4. 常见运维动作

- 夜里没跑成：不要白天补点（只会答话不动单）。查清原因后等下一晚，或确认在
  02:00–05:00 窗口内再手工触发。
- 某行一直「发不成」：查 `remark` 的原由（多为 create_by 空），补数据后按
  `doc/schema/nslp_task_dispatch_seed.sql` 第三节放回 status=0。
- 定额装不下：窗口内加挂第二趟（同文件注释里有现成语句），不要把 RUN_CAP 改大
  让单趟跑出 05:00。
- 停作业：把 `t_sys_quartz_job.status` 改成 1 或在调度管理页暂停，不要删 Bean。
