package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TNslpTaskItemMapper;
import com.fc.v2.model.auto.TNslpTaskItem;
import com.fc.v2.model.custom.TaskDispatchResult;
import com.fc.v2.service.support.TaskDispatchRules;

/**
 * 安排单夜间走表服务测试：窗口、届期、提前、定额、记号、没成、幂等照文走一遍。
 *
 * mapper 用内存账顶替：QueryWrapper 的等值片段按列名逐个过滤，UpdateWrapper 的
 * set 片段落到命中的行上并返回咬着的行数（与库面条件更新一个模样）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpTaskItemDispatchServiceImplTest {

    @Mock
    private TNslpTaskItemMapper itemMapper;
    @InjectMocks
    private TNslpTaskItemDispatchServiceImpl service;

    private List<TNslpTaskItem> store;
    /** 模拟并着的另一趟先落记号：这些 id 的「改已发出」更新咬不到（返回 0） */
    private Set<Long> stealIds;
    /** 模拟库面打颤：这些 id 的「改已发出」更新直接抛错 */
    private Set<Long> throwOnSendIds;

    private static final Pattern CLAUSE = Pattern.compile(
            "(\\w+)\\s*(<=|>=|=|<|>)\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}");
    private static final Pattern IS_NULL = Pattern.compile("(\\w+)\\s+IS\\s+NULL");
    private static final Pattern SET_CLAUSE = Pattern.compile(
            "(\\w+)=#\\{ew\\.paramNameValuePairs\\.(\\w+)}");

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TNslpTaskItem row(long id, String dueAt, String amount,
                                     String createBy, int status) {
        TNslpTaskItem r = new TNslpTaskItem();
        r.setId(Long.valueOf(id));
        r.setItemNo("IT" + id);
        r.setDueAt(dueAt == null ? null : d(dueAt));
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setCreateBy(createBy);
        r.setStatus(Integer.valueOf(status));
        r.setDelFlag(Integer.valueOf(0));
        r.setCreateTime(d("2026-10-01 09:00:00"));
        return r;
    }

    private static boolean eq(Object field, Object val) {
        if (field == null || val == null) {
            return field == val;
        }
        return field.equals(val) || String.valueOf(field).equals(String.valueOf(val));
    }

    private static List<String[]> clauses(AbstractWrapper<?, ?, ?> w) {
        Map<String, Object> params = w.getParamNameValuePairs();
        List<String[]> out = new ArrayList<String[]>();
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Matcher m = CLAUSE.matcher(seg);
        while (m.find()) {
            out.add(new String[]{m.group(1), m.group(2), String.valueOf(params.get(m.group(3)))});
        }
        return out;
    }

    /** 出现在「列 IS NULL」里的列：这些列与同组的等值条件按 OR 相就（空值也算匹配） */
    private static Set<String> nullColumns(AbstractWrapper<?, ?, ?> w) {
        Set<String> out = new HashSet<String>();
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Matcher m = IS_NULL.matcher(seg);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Map<String, Object> setValues(UpdateWrapper<?> uw) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Map<String, Object> params = uw.getParamNameValuePairs();
        String sql = uw.getSqlSet() == null ? "" : uw.getSqlSet();
        Matcher m = SET_CLAUSE.matcher(sql);
        while (m.find()) {
            out.put(m.group(1), params.get(m.group(2)));
        }
        return out;
    }

    private boolean itemMatches(TNslpTaskItem r, List<String[]> cs, Set<String> nullCols) {
        for (String[] c : cs) {
            Object v;
            switch (c[0]) {
                case "id": v = r.getId(); break;
                case "status": v = r.getStatus(); break;
                case "del_flag": v = r.getDelFlag(); break;
                default: v = null;
            }
            // 同组「列=值 OR 列 IS NULL」：情形格空着的存量行也算命中（仍搁着）
            if (nullCols.contains(c[0]) && v == null) {
                continue;
            }
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private static void applySets(TNslpTaskItem r, Map<String, Object> sets) {
        for (Map.Entry<String, Object> e : sets.entrySet()) {
            switch (e.getKey()) {
                case "status": r.setStatus((Integer) e.getValue()); break;
                case "content": r.setContent((String) e.getValue()); break;
                case "remark": r.setRemark((String) e.getValue()); break;
                case "update_by": r.setUpdateBy((String) e.getValue()); break;
                case "update_time": r.setUpdateTime((Date) e.getValue()); break;
                default: break;
            }
        }
    }

    private TNslpTaskItem find(long id) {
        for (TNslpTaskItem r : store) {
            if (r.getId().longValue() == id) {
                return r;
            }
        }
        return null;
    }

    private int countStatus(int status) {
        int n = 0;
        for (TNslpTaskItem r : store) {
            if (r.getStatus() != null && r.getStatus().intValue() == status) {
                n++;
            }
        }
        return n;
    }

    /** 拨表：走表途中每办一行之前看到的时刻，依次给出 */
    private void wallClockTicks(String... ticks) {
        final List<Date> seq = new ArrayList<Date>();
        for (String t : ticks) {
            seq.add(d(t));
        }
        final int[] idx = {0};
        service.setWallClock(new Supplier<Date>() {
            @Override
            public Date get() {
                return seq.get(Math.min(idx[0]++, seq.size() - 1));
            }
        });
    }

    @BeforeEach
    public void setUp() {
        store = new ArrayList<TNslpTaskItem>();
        stealIds = new HashSet<Long>();
        throwOnSendIds = new HashSet<Long>();
        wallClockTicks("2026-10-06 03:00:00");

        when(itemMapper.selectList(any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
            List<String[]> cs = clauses(w);
            Set<String> nullCols = nullColumns(w);
            List<TNslpTaskItem> out = new ArrayList<TNslpTaskItem>();
            for (TNslpTaskItem r : store) {
                if (itemMatches(r, cs, nullCols)) {
                    out.add(r);
                }
            }
            return out;
        });
        when(itemMapper.update(any(), any())).thenAnswer(inv -> {
            UpdateWrapper<?> uw = inv.getArgument(1);
            List<String[]> cs = clauses(uw);
            Set<String> nullCols = nullColumns(uw);
            Map<String, Object> sets = setValues(uw);
            boolean sendMark = Integer.valueOf(TaskDispatchRules.STATUS_DONE).equals(sets.get("status"));
            int n = 0;
            for (TNslpTaskItem r : store) {
                if (!itemMatches(r, cs, nullCols)) {
                    continue;
                }
                if (sendMark && stealIds.contains(r.getId())) {
                    continue;   // 并着的另一趟先落了记号：本趟咬不到
                }
                if (sendMark && throwOnSendIds.contains(r.getId())) {
                    throw new RuntimeException("库面打了个颤");
                }
                applySets(r, sets);
                n++;
            }
            return n;
        });
    }

    // ------------------------------------------------------------------
    // 那一截开不开：窗外按下只答话、不动单，连按两回一字不差
    // ------------------------------------------------------------------

    @Test
    public void outsideWindowOnlyReplies_andReplyIsStable() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        TaskDispatchResult r1 = service.runOnce(d("2026-10-06 01:30:00"));
        TaskDispatchResult r2 = service.runOnce(d("2026-10-06 06:00:00"));
        assertEquals(0, r1.getPicked());
        assertEquals(0, r1.getSent());
        assertEquals(0, r1.getFailed());
        assertEquals("本轮未改动安排单", r1.getReply());
        assertFalse(r1.isModified());
        // 连按两回，两回的答话一字不差
        assertEquals(r1.getReply(), r2.getReply());
        // 单上一行没动
        assertEquals(0, find(1L).getStatus().intValue());
        assertNull(find(1L).getContent());
    }

    @Test
    public void windowBoundary_0200Open_0500Closed() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        // 02:00:00 那一刻起算开
        TaskDispatchResult open = service.runOnce(d("2026-10-06 02:00:00"));
        assertEquals(1, open.getSent());
        // 一到 05:00:00 即算收手
        store.add(row(2L, "2026-10-01 00:00:00", null, "岗乙", 0));
        TaskDispatchResult closed = service.runOnce(d("2026-10-06 05:00:00"));
        assertEquals(0, closed.getPicked());
        assertEquals("本轮未改动安排单", closed.getReply());
        assertEquals(0, find(2L).getStatus().intValue());
    }

    @Test
    public void pastFiveOClockStopsImmediately_midRun() {
        // 走表途中抬头看表：过了 05:00 立刻收手，剩下的行照旧搁着
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        store.add(row(2L, "2026-10-01 01:00:00", null, "岗乙", 0));
        store.add(row(3L, "2026-10-01 02:00:00", null, "岗丙", 0));
        wallClockTicks("2026-10-06 04:30:00", "2026-10-06 04:50:00", "2026-10-06 05:00:00");
        TaskDispatchResult r = service.runOnce(d("2026-10-06 04:20:00"));
        assertEquals(2, r.getPicked());
        assertEquals(2, r.getSent());
        assertEquals(0, r.getFailed());
        assertEquals(0, find(3L).getStatus().intValue());
    }

    // ------------------------------------------------------------------
    // 届期取法：走到这一刻就算到，恰好等于这一刻的也算；只挑还搁着的
    // ------------------------------------------------------------------

    @Test
    public void dueExactlyAtThisMomentCounts() {
        store.add(row(1L, "2026-10-06 03:00:00", null, "岗甲", 0));
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, r.getPicked());
        assertEquals(1, r.getSent());
        assertEquals(1, find(1L).getStatus().intValue());
    }

    @Test
    public void onlyWaitingRowsArePicked() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 1));   // 已发出
        store.add(row(2L, "2026-10-01 00:00:00", null, "岗乙", 2));   // 发不成
        store.add(row(3L, "2026-10-01 00:00:00", null, "岗丙", 0));   // 搁着
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, r.getPicked());
        assertEquals(1, r.getSent());
        assertEquals(1, find(1L).getStatus().intValue());
        assertEquals(2, find(2L).getStatus().intValue());
        assertNull(find(1L).getContent());
        assertNull(find(2L).getContent());
    }

    @Test
    public void emptySheetIsLegal_returnsZeroSafely() {
        // 空单是合法输入：安全回 0，不许越界去取第一条
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(0, r.getPicked());
        assertEquals(0, r.getSent());
        assertEquals(0, r.getFailed());
        assertEquals("本轮未改动安排单", r.getReply());
    }

    // ------------------------------------------------------------------
    // 可提前几日：只看本行那一格；零头不算、法定节假日不算、停办日不算
    // ------------------------------------------------------------------

    @Test
    public void earlyDays_nullAmountMeansNoEarly() {
        // 那一格空着就当不提前：邻行许提前也借不过来
        store.add(row(1L, "2026-10-07 09:00:00", null, "岗甲", 0));
        store.add(row(2L, "2026-10-07 09:00:00", "5", "岗乙", 0));
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, r.getPicked());
        assertEquals(0, find(1L).getStatus().intValue());
        assertEquals(1, find(2L).getStatus().intValue());
    }

    @Test
    public void earlyDays_fractionDropped() {
        // 3.75 日 → 零头不算，按 3 个可数日提前
        store.add(row(1L, "2026-10-12 09:00:00", "3.75", "岗甲", 0));
        // 2026-10-12 是周一；往回数 3 个可数日：10.9(五)、10.8(四)，再往前
        // 10.1–10.7 是法定节假日、10.10/10.11 是停办日都不算 → 落在 9.30(三)
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, r.getPicked());
        assertEquals(1, r.getSent());
    }

    @Test
    public void earlyDays_holidaysAndSuspensionDaysDoNotCount() {
        // 国庆那一周（10.1–10.7 法定节假日）一天也数不进去
        store.add(row(1L, "2026-10-08 10:00:00", "1", "岗甲", 0));
        // 许提前 1 个可数日：10.1–10.7 都不算 → 最早可办 9.30 10:00
        TaskDispatchResult inHolidayWeek = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, inHolidayWeek.getSent());
        // 同一行若不许提前，假期里就届不了期
        store.add(row(2L, "2026-10-08 10:00:00", null, "岗乙", 0));
        TaskDispatchResult noEarly = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(0, noEarly.getPicked());
        assertEquals(0, find(2L).getStatus().intValue());
    }

    @Test
    public void earlyDays_weekendDoesNotCount() {
        // 周一届期、许提前 1 日：周日、周六是停办日 → 最早可办在上周五
        store.add(row(1L, "2026-10-12 09:00:00", "1", "岗甲", 0));
        TaskDispatchResult saturday = service.runOnce(d("2026-10-10 03:00:00"));
        assertEquals(1, saturday.getSent());
    }

    // ------------------------------------------------------------------
    // 办成一行的记号：两处同一回落下
    // ------------------------------------------------------------------

    @Test
    public void sentRowGetsBothMarks_inOnePass() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        find(1L).setContent("临江片区水泵房复测");
        TaskDispatchResult r = service.runOnce(d("2026-10-06 02:30:00"));
        assertEquals(1, r.getSent());
        TNslpTaskItem row = find(1L);
        // 前一处：情形从搁着改成已发出
        assertEquals(1, row.getStatus().intValue());
        // 后一处：记要里补上这一回的发出那一刻与发给谁
        assertEquals("临江片区水泵房复测［已发出 2026-10-06 02:30:00 发给 岗甲］", row.getContent());
        assertEquals(TaskDispatchRules.ACTOR, row.getUpdateBy());
        assertEquals(d("2026-10-06 02:30:00"), row.getUpdateTime());
    }

    @Test
    public void secondRunDoesNotSendAgain_idempotent() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        TaskDispatchResult first = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, first.getSent());
        String contentAfterFirst = find(1L).getContent();
        // 同一趟并着跑两回／下一趟再来：已发出的不再碰，答话仍是那一句
        TaskDispatchResult second = service.runOnce(d("2026-10-06 03:20:00"));
        assertEquals(0, second.getPicked());
        assertEquals(0, second.getSent());
        assertEquals("本轮未改动安排单", second.getReply());
        assertEquals(contentAfterFirst, find(1L).getContent());
    }

    @Test
    public void concurrentRunTookTheRow_firstCountsNeither() {
        // 并着的另一趟先落了记号：本趟条件更新咬不到，不算发出也不算没成
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        stealIds.add(1L);
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, r.getPicked());
        assertEquals(0, r.getSent());
        assertEquals(0, r.getFailed());
        assertEquals("本轮未改动安排单", r.getReply());
        assertEquals(0, find(1L).getStatus().intValue());
    }

    // ------------------------------------------------------------------
    // 一行站不住：标本行，其余照常，整趟不中断
    // ------------------------------------------------------------------

    @Test
    public void rowWithoutRecipientIsMarkedFailed_othersGoOn() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "  ", 0));      // 行上没落着人名
        store.add(row(2L, "2026-10-01 01:00:00", null, "岗乙", 0));
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(2, r.getPicked());
        assertEquals(1, r.getSent());
        assertEquals(1, r.getFailed());
        assertEquals(2, find(1L).getStatus().intValue());
        assertEquals("发不成：行上没落着人名，不知发给谁", find(1L).getRemark());
        assertEquals(1, find(2L).getStatus().intValue());
        assertNotNull(find(2L).getContent());
    }

    @Test
    public void dbErrorOnOneRow_marksItFailed_othersGoOn() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        store.add(row(2L, "2026-10-01 01:00:00", null, "岗乙", 0));
        throwOnSendIds.add(1L);
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(2, r.getPicked());
        assertEquals(1, r.getSent());
        assertEquals(1, r.getFailed());
        // 本行标一句失败原由；已经发出的行不翻回原样
        assertEquals(2, find(1L).getStatus().intValue());
        assertEquals("发不成：库面打了个颤", find(1L).getRemark());
        assertEquals(1, find(2L).getStatus().intValue());
    }

    // ------------------------------------------------------------------
    // 定额：一趟至多 500 行，装满即止，剩下的下一趟接着挑
    // ------------------------------------------------------------------

    @Test
    public void quotaCapsAtFiveHundred_restStayWaiting() {
        for (int i = 1; i <= 501; i++) {
            // 届期一个比一个晚一刻：最届期的先办，够不着定额的那行是最晚的
            TNslpTaskItem r = row(i, "2026-10-01 00:00:00", null, "岗甲", 0);
            r.setDueAt(new Date(d("2026-10-01 00:00:00").getTime() + i * 60000L));
            store.add(r);
        }
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(500, r.getPicked());
        assertEquals(500, r.getSent());
        assertEquals(0, r.getFailed());
        assertEquals("本轮已改动安排单：挑中500行、发出500行、没成0行", r.getReply());
        assertEquals(500, countStatus(1));
        // 剩下的那一行照旧搁着：届期最晚的那一行（第 501 行）
        assertEquals(0, find(501L).getStatus().intValue());
        assertNull(find(501L).getContent());
        // 下一趟接着挑：再按一回，剩下那一行办掉
        TaskDispatchResult next = service.runOnce(d("2026-10-06 03:30:00"));
        assertEquals(1, next.getPicked());
        assertEquals(1, next.getSent());
        assertEquals(1, find(501L).getStatus().intValue());
    }

    // ------------------------------------------------------------------
    // 老条目与新条目同一条路径；答话只由三个数放出来
    // ------------------------------------------------------------------

    @Test
    public void legacyAndNewRowsWalkTheSamePath() {
        TNslpTaskItem legacy = row(1L, "2026-10-01 00:00:00", null, "岗甲", 0);
        legacy.setCreateTime(d("2024-03-01 08:00:00"));   // 早先进库的老条目
        TNslpTaskItem fresh = row(2L, "2026-10-01 00:00:00", null, "岗乙", 0);
        fresh.setCreateTime(d("2026-10-05 08:00:00"));
        store.add(legacy);
        store.add(fresh);
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(2, r.getPicked());
        assertEquals(2, r.getSent());
        assertEquals(1, find(1L).getStatus().intValue());
        assertEquals(1, find(2L).getStatus().intValue());
    }

    @Test
    public void blankStatusLegacyRow_isPickedAndSent() {
        // 老系统搬来、情形格空着（列默认 NULL）、恰好今天届期的复测条目：照发，不能漏
        TNslpTaskItem legacy = row(1L, "2026-10-06 03:00:00", null, "岗甲", 0);
        legacy.setStatus(null);
        store.add(legacy);
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, r.getPicked());
        assertEquals(1, r.getSent());
        // 发成后情形落成已发出（不再是空），两处记号同回落下
        assertEquals(1, find(1L).getStatus().intValue());
        assertNotNull(find(1L).getContent());
    }

    @Test
    public void blankStatusLegacyRow_sentOnlyOnce_isIdempotent() {
        // 空情形老行发成一回后情形变已发出，第二趟（夜间再触发／别的把手照同一处）不再碰
        TNslpTaskItem legacy = row(1L, "2026-10-01 00:00:00", null, "岗甲", 0);
        legacy.setStatus(null);
        store.add(legacy);
        TaskDispatchResult first = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(1, first.getSent());
        String contentAfterFirst = find(1L).getContent();
        TaskDispatchResult second = service.runOnce(d("2026-10-06 03:20:00"));
        assertEquals(0, second.getPicked());
        assertEquals(0, second.getSent());
        assertEquals(contentAfterFirst, find(1L).getContent());
        assertEquals(1, find(1L).getStatus().intValue());
    }

    @Test
    public void blankStatusLegacyRow_withoutRecipient_markedFailed() {
        // 空情形老行没落着人名：标本行发不成（情形从空落成发不成），不越界、不连累旁行
        TNslpTaskItem legacy = row(1L, "2026-10-01 00:00:00", null, "  ", 0);
        legacy.setStatus(null);
        TNslpTaskItem fresh = row(2L, "2026-10-01 01:00:00", null, "岗乙", 0);
        store.add(legacy);
        store.add(fresh);
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(2, r.getPicked());
        assertEquals(1, r.getSent());
        assertEquals(1, r.getFailed());
        assertEquals(2, find(1L).getStatus().intValue());
        assertEquals("发不成：行上没落着人名，不知发给谁", find(1L).getRemark());
        assertEquals(1, find(2L).getStatus().intValue());
    }

    @Test
    public void replyIsDerivedFromTheThreeNumbersOnly() {
        store.add(row(1L, "2026-10-01 00:00:00", null, "岗甲", 0));
        store.add(row(2L, "2026-10-01 01:00:00", null, null, 0));
        TaskDispatchResult r = service.runOnce(d("2026-10-06 03:00:00"));
        assertEquals(2, r.getPicked());
        assertEquals(1, r.getSent());
        assertEquals(1, r.getFailed());
        assertTrue(r.isModified());
        assertEquals("本轮已改动安排单：挑中2行、发出1行、没成1行", r.getReply());
    }
}
