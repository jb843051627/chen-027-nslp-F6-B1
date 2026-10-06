-- nslp 建筑施工与社会生活噪声污染防治监管 -- schema (chen-027)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_027

CREATE TABLE IF NOT EXISTS t_nslp_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '噪声投诉与超标处置办件的件次',
  point_no varchar(64) DEFAULT NULL COMMENT '点位码（立件四样之一，一处点位一条）',
  zone_no varchar(64) DEFAULT NULL COMMENT '所属区册代号（立件四样之一，以底册带出的为准）',
  occ_num int DEFAULT NULL COMMENT '处数（立件四样之一，至少一处）',
  check_reading decimal(12,1) DEFAULT NULL COMMENT '准备折算的那一个核查读数（立件四样之一，折算前先取一位小数逢半进一）',
  stage int DEFAULT NULL COMMENT '当前关次 0..3（受件/核查/责令/复查收尾，关次由后端数，报文自带的只作参考不入库）',
  status int DEFAULT NULL COMMENT '单子走到哪一关 0未起 1在办 2已封住',
  content varchar(255) DEFAULT NULL COMMENT '本关一句（同一关第二句盖不上去，本行只留头一句）',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪关动作',
  order_accept varchar(16) DEFAULT NULL COMMENT '责令关核收原话；记成「可」复查收尾才开得了工',
  witness_a varchar(64) DEFAULT NULL COMMENT '复查收尾见证岗甲落笔（两名各落一笔）',
  witness_b varchar(64) DEFAULT NULL COMMENT '复查收尾见证岗乙落笔（两名各落一笔）',
  proof_no varchar(128) DEFAULT NULL COMMENT '复测凭证那一串（封关时写回本行）',
  note_mask int DEFAULT 0 COMMENT '本趟各关头一句按语位图 bit0..bit3（收回的关腾位，重新挪到从头累计）',
  close_kind int DEFAULT NULL COMMENT '收口情由 空=走满末关封住 1反映人撤回 2区册并走（后两种视同收口）',
  void_flag int DEFAULT 0 COMMENT '末关作废 0正常 1已作废（作废的件名册照常露脸，只是不给动手的把手）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_point_open (point_no, status, void_flag, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='噪声投诉与超标处置办件流转单';

-- 一条办件身后的留痕：每一关那句按语连同提交那一刻，按时序压在本行身后；
-- 改档与留痕同一事务，日后追经过只取这一串（档数由这一串放出来，不许另算）。
CREATE TABLE IF NOT EXISTS t_nslp_case_trail (
  id bigint NOT NULL COMMENT '主键',
  case_id bigint NOT NULL COMMENT '所随办件 t_nslp_case_flow.id',
  stage int DEFAULT NULL COMMENT '落笔那一刻办件所处关次 0..3',
  action varchar(32) DEFAULT NULL COMMENT '动作 受件立条/挪前/收回/按语/封住/反映人撤回/区册并走/作废',
  note varchar(500) DEFAULT NULL COMMENT '这一笔留下的话（按语原句或动作按语）',
  trail_time datetime DEFAULT NULL COMMENT '提交那一刻（同刻两笔各记各的先后，按本列排先后不许并成一条）',
  seq int DEFAULT NULL COMMENT '同刻先后次序（trail_time 并列时以本列断先后）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_case_trail (case_id, trail_time, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='办件流转留痕（一关一句，按时序压在身后）';

CREATE TABLE IF NOT EXISTS t_nslp_doc_tpl (
  id bigint NOT NULL COMMENT '主键',
  tpl_no varchar(64) DEFAULT NULL COMMENT '文书模板代号',
  tpl_name varchar(128) DEFAULT NULL COMMENT '文种名（超标告知·责令限期改正·复查结论）',
  ver_no int DEFAULT NULL COMMENT '版次（数值大的先说话）',
  var_num int DEFAULT NULL COMMENT '本版应填项数',
  fill_num int DEFAULT NULL COMMENT '本行已取到值的项数',
  miss_num int DEFAULT NULL COMMENT '还差几项（轧出来，不手填）',
  eff_start datetime DEFAULT NULL COMMENT '起用那一一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一一日(不含)',
  status int DEFAULT NULL COMMENT '版次情形 0在位 1已撤版',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执法文书模板与版次';

CREATE TABLE IF NOT EXISTS t_nslp_flow_batch (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '监测流水批次码（一拨一码；同一码第二回到达只照面，不另起一拨）',
  cap_num int DEFAULT NULL COMMENT '写定的顶（抬头那一格空着/填负/填得离谱，一律按300认后落库，只此一处来源）',
  row_num int DEFAULT NULL COMMENT '进门点出来的行数（事后翻得出来当初点了几行）',
  parse_kind varchar(16) DEFAULT NULL COMMENT '本拨切法 DELIMITER按分隔符／FIXED按定宽（一拨之内只许一种）',
  status int DEFAULT NULL COMMENT '拨落地情形 0已核收 1超顶整批未收',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监测流水核收拨抬头';

CREATE TABLE IF NOT EXISTS t_nslp_flow_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '监测流水批次码',
  row_no int DEFAULT NULL COMMENT '原报行次（系统在本拨内排，不留手填格子；退回重报仍显示当初那一个行次）',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的点位码',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行读数次数',
  chk_num decimal(12,2) DEFAULT NULL COMMENT '本行达标次数',
  put_kind varchar(32) DEFAULT NULL COMMENT '来路（自动站直采／监测车补报两条来路）',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已退回（第四种写法不许落库）',
  ret_reason varchar(255) DEFAULT NULL COMMENT '退回理由（吃了哪一条，一句一句问得出）；未退空白',
  parse_flag int DEFAULT '0' COMMENT '解析情形 0切得动 1切不动（单独标出递给回核看的人，不按住整拨）',
  reg_diff_flag int DEFAULT '0' COMMENT '底册比对 0相符或未报 1不合（以底册为准，回执单列）',
  point_name varchar(128) DEFAULT NULL COMMENT '点位题名（以点位底册带出的为准，报文那串只拿来比对）',
  zone_no varchar(64) DEFAULT NULL COMMENT '区册代号（以点位底册带出的为准）',
  layer_flag int DEFAULT '0' COMMENT '册层 0现行 1历史（未勾连满两个年度转入历史册层，只此一处挪动）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监测流水核收行';

CREATE TABLE IF NOT EXISTS t_nslp_limit_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '功能区限值线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  obj_kind varchar(32) DEFAULT NULL COMMENT '这条线管昼段还是夜段',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '起算限值',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '够线限值',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶限值',
  eff_start datetime DEFAULT NULL COMMENT '起用那一一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一一日(不含)',
  priority int DEFAULT NULL COMMENT '交班次序(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在位 1已撤下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能区昼夜间限值线';

CREATE TABLE IF NOT EXISTS t_nslp_permit_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '夜间连续施工作业许可签批单',
  node_no int DEFAULT NULL COMMENT '当前所在关 0..2（窗口受理/区局核定/市局备案）',
  sign_mode int DEFAULT NULL COMMENT '本关算齐办法 0一名即过 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本关应签人数',
  sign_count int DEFAULT NULL COMMENT '本关已到人数',
  status int DEFAULT NULL COMMENT '签批情形 0在签 1已点齐 2已压回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='夜间连续施工作业许可签批单';

CREATE TABLE IF NOT EXISTS t_nslp_point (
  id bigint NOT NULL COMMENT '主键',
  point_no varchar(64) DEFAULT NULL COMMENT '点位码',
  zone_id int DEFAULT NULL COMMENT '挂在哪一个区册名下',
  zone_no varchar(64) DEFAULT NULL COMMENT '区册代号（冗余自区册）',
  src_kind varchar(128) DEFAULT NULL COMMENT '来路（自动站／移动监测车／人工核查三条来路）',
  need_num int DEFAULT NULL COMMENT '核定应测次数',
  got_num int DEFAULT NULL COMMENT '已核验达标的次数',
  lack_num int DEFAULT NULL COMMENT '还差几回（轧出来，不手填）',
  content varchar(255) DEFAULT NULL COMMENT '随件交来的点位题名与依附声源',
  status int DEFAULT NULL COMMENT '册面情形 0新入册 1已核齐 2缺项',
  pinned_rule_code varchar(64) DEFAULT NULL COMMENT '归层钉住的限值线代号（不归层清空白，追得出当年照哪条线）',
  pinned_eff_start datetime DEFAULT NULL COMMENT '归层钉住的起用那一一日（不归层清空白）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='声环境监测点位底册';

CREATE TABLE IF NOT EXISTS t_nslp_task_item (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '复测与巡查条目代号',
  due_at datetime DEFAULT NULL COMMENT '该发复测通知那一一日（落库为 datetime）',
  amount decimal(12,2) DEFAULT NULL COMMENT '最多许提前几日',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对点位的记要',
  status int DEFAULT NULL COMMENT '条目情形 0搁着 1已发出 2发不成',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='复测与巡查条目';

CREATE TABLE IF NOT EXISTS t_nslp_zone_book (
  id bigint NOT NULL COMMENT '主键',
  zone_no varchar(64) DEFAULT NULL COMMENT '声环境功能区区册代号',
  zone_name varchar(128) DEFAULT NULL COMMENT '区册全称',
  zone_kind varchar(32) DEFAULT NULL COMMENT '区层类别（甲层住宅·乙层混合·丙层工业·丁层交通干线）',
  road_name varchar(128) DEFAULT NULL COMMENT '属地详址（澜原省—江水市—区—片区）',
  align_kind varchar(32) DEFAULT NULL COMMENT '来路（市里区册下发／本局自录）',
  status int DEFAULT NULL COMMENT '区册情形 0在册 1已撤出',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='声环境功能区区册底册';

-- 初始档案数据（t_nslp_zone_book 预置两条区册种子）
-- zone_id=0 为在册区册（甲层住宅类），zone_id=1 为已并走撤出的旧区册（乙层），供挂靠校验与挡新入册校验取用。
-- 区册代号与区层类别按区层序给值。
INSERT IGNORE INTO t_nslp_zone_book (id, zone_no, zone_name, zone_kind, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'QN00', '澜原省江水市云桥区临江片区（甲层住宅类，在册）', '甲层', '澜原省—江水市—云桥区—临江片区', 0, 0, 'seed', NOW()),
       (1, 'QN01', '南津区北岸片区·并走撤出的旧区册（已撤出）', '乙层', '澜原省—江水市—南津区—北岸片区', 1, 0, 'seed', NOW());

-- ============================================================================
-- 建档 + 三态 + 版次出文：在旧表上加列、另立新表，旧列一条不动。
-- 列名与基线实体契约逐列对齐，改列必须同步实体。
-- ============================================================================

-- 点位底册加五列：
-- reg_year   年度。同一年度、同一区册底下，同点位码只许一条在走的行。
-- zone_name  区册全称（冗余自区册）。与区册底对不齐时以区册为准改这一行，两处各留一份说法。
-- flow_stage 三态当前步：0待测 1已测 2压卡；步数只由 t_nslp_point_step 那串放出来，
--            本行这一格只是回放后写回的结果。早年间纸质簿搬进来的旧行留空——空不当已压卡。
-- stop_flag  就地叫停：0在走 1已叫停。叫停与压卡一样腾出同点位名额，但不是压卡、
--            行仍在册、照常列得出来。
-- maintain_unit 递材料的维护单位（同一点位两家维护单位同日各报，仍只许一条在走）。
ALTER TABLE t_nslp_point
  ADD COLUMN reg_year int DEFAULT NULL COMMENT '年度（同一年度同一区册同码只许一条在走）' AFTER zone_no,
  ADD COLUMN zone_name varchar(128) DEFAULT NULL COMMENT '区册全称（冗余自区册，以区册为准对齐）' AFTER zone_no,
  ADD COLUMN flow_stage int DEFAULT NULL COMMENT '三态当前步 0待测 1已测 2压卡；旧行留空，空不当已压卡' AFTER status,
  ADD COLUMN stop_flag int DEFAULT 0 COMMENT '就地叫停 0在走 1已叫停（腾名额但非压卡，行仍在册）' AFTER flow_stage,
  ADD COLUMN maintain_unit varchar(128) DEFAULT NULL COMMENT '递材料的维护单位' AFTER src_kind,
  ADD KEY idx_point_live (reg_year, zone_no, point_no, flow_stage, stop_flag, del_flag);

-- 点位三态留痕：每推进一步／退回一步／压卡／叫停都压一笔，按时序排在行身后。
-- 当下走到哪一步只由这一串放出来（屏上写的档、经办人心里记的档都不作依据）。
-- 退回重来时先前那一趟的记录照旧留着，不与后补的并成一排。
CREATE TABLE IF NOT EXISTS t_nslp_point_step (
  id bigint NOT NULL COMMENT '主键',
  point_id bigint NOT NULL COMMENT '所随点位 t_nslp_point.id',
  step_no int DEFAULT NULL COMMENT '落笔那一刻走到的步 0待测 1已测 2压卡',
  action varchar(16) DEFAULT NULL COMMENT '动作 起册/推进/退回/压卡/叫停',
  note varchar(500) DEFAULT NULL COMMENT '这一笔留下的话',
  step_time datetime DEFAULT NULL COMMENT '提交那一刻（同刻两笔各记各的先后）',
  seq int DEFAULT NULL COMMENT '同刻先后次序（step_time 并列时以本列断先后）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_point_step (point_id, step_time, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点位三态留痕（一步一笔，档由这一串放出来）';

-- 文书出文留档：一纸文书落一次档。出文照哪一版取值，落笔时把那一版主键、版次、
-- 三个数钉在本档；往后新立一版不回改旧档（前年新版措辞回灌旧文的事今后一处也不许有）。
CREATE TABLE IF NOT EXISTS t_nslp_doc_issue (
  id bigint NOT NULL COMMENT '主键',
  tpl_name varchar(128) DEFAULT NULL COMMENT '文种名（超标告知·责令限期改正·复查结论）',
  tpl_id bigint DEFAULT NULL COMMENT '出文那一刻开口的那一版 t_nslp_doc_tpl.id（钉住，不随新版改）',
  ver_no int DEFAULT NULL COMMENT '当版版次（落笔钉住）',
  point_id bigint DEFAULT NULL COMMENT '所对点位 t_nslp_point.id',
  point_no varchar(64) DEFAULT NULL COMMENT '所对点位码',
  var_num int DEFAULT NULL COMMENT '本版应填项数（落笔时的快照）',
  fill_num int DEFAULT NULL COMMENT '已取到值的项数（落笔时的快照）',
  miss_num int DEFAULT NULL COMMENT '还差几项（落笔时由前两个轧出，钉住）',
  issue_time datetime DEFAULT NULL COMMENT '出文那一刻（选版只认这一刻）',
  status int DEFAULT 0 COMMENT '出文情形 0已出',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者（谁出的文，留得下来）',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_issue_tpl (tpl_name, issue_time),
  KEY idx_issue_point (point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执法文书出文留档（一纸一档，版次钉住不回改）';

-- ============================================================================
-- 夜间连续施工作业许可签批单：三关三种数法 + 逐笔留痕 + 册面联动。
-- 旧列一条不动：sign_mode/need_count/sign_count 仍是「当下这一关」的快照回显，
-- 关次与已到几人的凭据只在 t_nslp_permit_sign 那一串，行面只回放后写回。
-- 列名与基线实体契约逐列对齐，改列必须同步实体。
-- ============================================================================

-- 签批单主表挂点位三列：单只许从点位底册的记录里带出来，一处点位、一次夜间连续
-- 施工只摊上一张；前端没有自己新建的口子。cur_round 是当下关本轮轮次（同一关压回
-- 重走一轮加一），同样只作回放后的回显。
ALTER TABLE t_nslp_permit_bill
  ADD COLUMN point_id bigint DEFAULT NULL COMMENT '带出本单的点位底册行 t_nslp_point.id' AFTER bill_no,
  ADD COLUMN point_no varchar(64) DEFAULT NULL COMMENT '点位码（以点位底册带出的为准）' AFTER point_id,
  ADD COLUMN zone_no varchar(64) DEFAULT NULL COMMENT '区册代号（以点位底册带出的为准）' AFTER point_no,
  ADD COLUMN cur_round int DEFAULT 1 COMMENT '当下关本轮轮次（压回重走一轮加一，回放后写回）' AFTER sign_count,
  ADD KEY idx_bill_point_open (point_no, status, del_flag);
-- status 第 2 档正名：0在签 1已点齐 2止住（压回不再是终局情形，只退一关、仍记在签）。
ALTER TABLE t_nslp_permit_bill
  MODIFY COLUMN status int DEFAULT NULL COMMENT '签批情形 0在签 1已点齐 2止住（一方落不可即止住；压回退关仍是在签）';
ALTER TABLE t_nslp_permit_bill
  MODIFY COLUMN sign_mode int DEFAULT NULL COMMENT '本关算齐办法 0一名即过 1两条一并认可 2先落为准（三关各用各的）';

-- 签批逐笔留痕：一次落笔只有这一个入口，一笔一行，按落单时刻时序压在单身后。
-- 关次写的是服务端认下的关（报文写第几关不采信）；round_no 是落笔那一刻本关第几轮，
-- 压回抹去本轮笔只把 void_flag 置 1——行不删，旧轮原话一句一句仍查得着；同刻两人
-- 各按各的提交，sign_time 各记各的，并列时以 seq 断先后，不许并成一笔。
CREATE TABLE IF NOT EXISTS t_nslp_permit_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所随签批单 t_nslp_permit_bill.id',
  action varchar(16) DEFAULT NULL COMMENT '动作 立单/落笔/推进/压回/点齐/止住（一次落笔只走落笔这一个入口）',
  node_no int DEFAULT NULL COMMENT '落笔那一刻服务端认下的关 0收件席 1认定席 2存备席',
  round_no int DEFAULT NULL COMMENT '落笔那一刻本关第几轮（首次到达为1，压回重走加一）',
  sign_by varchar(64) DEFAULT NULL COMMENT '落笔人（同一人同关同轮只许一笔）',
  opinion varchar(255) DEFAULT NULL COMMENT '落笔原话（逐字为「不可」才按不认可记账）',
  agree_flag int DEFAULT 1 COMMENT '认可与否 1认可 0不认可（由原话放出来，报文勾选不采信）',
  void_flag int DEFAULT 0 COMMENT '抹去标记 0本轮活着 1被压回抹去（行留底，不进本轮计数）',
  sign_time datetime DEFAULT NULL COMMENT '落单那一刻（同刻两笔各记各的，不许并成一笔）',
  seq int DEFAULT NULL COMMENT '同刻先后次序（sign_time 并列时以本列断先后）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_bill_sign (bill_id, node_no, round_no, void_flag),
  KEY idx_bill_sign_time (bill_id, sign_time, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签批单逐笔留痕（一笔一行，档由这一串放出来）';

-- 点位底册加许可档：整张签批单封住时这一行同步改口；没封住不许动。
ALTER TABLE t_nslp_point
  ADD COLUMN permit_state int DEFAULT 0 COMMENT '夜间连续施工许可档 0未准许 1已据签批单准许（只随点齐同回改）' AFTER stop_flag;

-- 点位行变更履历：主档改口与履历追加出自同一回处理、基于同一次计算；
-- 这一笔由谁落、依据哪一张单、从哪一档改到哪一档、发生在哪一刻，一条不少。
CREATE TABLE IF NOT EXISTS t_nslp_point_change (
  id bigint NOT NULL COMMENT '主键',
  point_id bigint NOT NULL COMMENT '被改口的点位行 t_nslp_point.id',
  point_no varchar(64) DEFAULT NULL COMMENT '点位码（改口时钉住）',
  change_field varchar(64) DEFAULT NULL COMMENT '改的是哪一档（permit_state 许可档）',
  from_state int DEFAULT NULL COMMENT '改前档',
  to_state int DEFAULT NULL COMMENT '改后档',
  bill_id bigint DEFAULT NULL COMMENT '依据哪一张签批单 t_nslp_permit_bill.id',
  bill_no varchar(64) DEFAULT NULL COMMENT '依据的签批单单号（对不住时报得出对应单据）',
  change_by varchar(64) DEFAULT NULL COMMENT '这一笔由谁落',
  change_time datetime DEFAULT NULL COMMENT '改口那一刻（与主档改动同一刻）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_change_point (point_id, change_time),
  KEY idx_change_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点位行变更履历（改口与履历同回处理，同源同算）';

