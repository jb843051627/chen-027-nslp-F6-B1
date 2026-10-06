package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpFlowBatchMapper;
import com.fc.v2.mapper.auto.TNslpFlowRowMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpFlowBatch;
import com.fc.v2.model.auto.TNslpFlowRow;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.FlowBatchSubmit;
import com.fc.v2.model.custom.FlowReceipt;
import com.fc.v2.model.custom.FlowReconcile;
import com.fc.v2.model.custom.FlowRowSubmit;
import com.fc.v2.service.support.FlowRowRules;

/**
 * 核收环节测试：四个动作一口气走完、同一批次码再到只照面、回执三个数
 * 同一次计算、退回理由一句一句问得出、未勾满两年转历史册层。
 *
 * 三个 mapper 用内存流水账顶替：selectList/selectOne 按 QueryWrapper 的
 * SQL 片段与参数名值对逐条过滤，insert/updateById 直接落账。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpFlowRowServiceImplTest {

    @Mock
    private TNslpFlowRowMapper flowRowMapper;
    @Mock
    private TNslpFlowBatchMapper flowBatchMapper;
    @Mock
    private TNslpPointMapper pointMapper;
    @InjectMocks
    private TNslpFlowRowServiceImpl service;

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<TNslpFlowRow> rowStore;
    private List<TNslpFlowBatch> batchStore;
    private List<TNslpPoint> pointStore;
    private long idSeq;

    // ------------------------------------------------------------------
    // 内存流水账
    // ------------------------------------------------------------------

    private static final Pattern CLAUSE = Pattern.compile(
            "(\\w+)\\s*(<=|>=|=|<|>)\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");

    private static boolean cmp(Object field, String op, Object val) {
        if ("=".equals(op)) {
            if (field == null || val == null) {
                return field == val;
            }
            return field.equals(val) || String.valueOf(field).equals(String.valueOf(val));
        }
        if (field instanceof Date && val instanceof Date) {
            int c = ((Date) field).compareTo((Date) val);
            if ("<=".equals(op)) {
                return c <= 0;
            }
            if (">=".equals(op)) {
                return c >= 0;
            }
            if ("<".equals(op)) {
                return c < 0;
            }
            if (">".equals(op)) {
                return c > 0;
            }
        }
        return false;
    }

    private static boolean rowMatches(TNslpFlowRow r, String seg, Map<String, Object> params) {
        Matcher m = CLAUSE.matcher(seg);
        while (m.find()) {
            String col = m.group(1);
            String op = m.group(2);
            Object val = params.get(m.group(3));
            boolean ok;
            if ("batch_no".equals(col)) {
                ok = cmp(r.getBatchNo(), op, val);
            } else if ("row_no".equals(col)) {
                ok = cmp(r.getRowNo(), op, val);
            } else if ("status".equals(col)) {
                ok = cmp(r.getStatus(), op, val);
            } else if ("layer_flag".equals(col)) {
                ok = cmp(r.getLayerFlag(), op, val);
            } else if ("del_flag".equals(col)) {
                ok = cmp(r.getDelFlag(), op, val);
            } else if ("create_time".equals(col)) {
                ok = cmp(r.getCreateTime(), op, val);
            } else if ("id".equals(col)) {
                ok = cmp(r.getId(), op, val);
            } else {
                ok = true;
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private List<TNslpFlowRow> filterRows(QueryWrapper<TNslpFlowRow> w) {
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Map<String, Object> params = w.getParamNameValuePairs();
        List<TNslpFlowRow> out = new ArrayList<TNslpFlowRow>();
        for (TNslpFlowRow r : rowStore) {
            if (rowMatches(r, seg, params)) {
                out.add(r);
            }
        }
        return out;
    }

    private TNslpFlowRow findRow(Long id) {
        for (TNslpFlowRow r : rowStore) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    @BeforeEach
    public void setUp() {
        rowStore = new ArrayList<TNslpFlowRow>();
        batchStore = new ArrayList<TNslpFlowBatch>();
        pointStore = new ArrayList<TNslpPoint>();
        idSeq = 0;

        pointStore.add(point(1L, "PT0001", 10, "临江片区1号点", "QN00"));
        pointStore.add(point(2L, "PT0002", 5, "临江片区2号点", "QN00"));

        when(pointMapper.selectList(any())).thenAnswer(inv -> new ArrayList<TNslpPoint>(pointStore));

        when(flowBatchMapper.insert(any(TNslpFlowBatch.class))).thenAnswer(inv -> {
            TNslpFlowBatch b = inv.getArgument(0);
            b.setId(Long.valueOf(++idSeq));
            if (b.getCreateTime() == null) {
                b.setCreateTime(new Date());
            }
            batchStore.add(b);
            return Integer.valueOf(1);
        });
        when(flowBatchMapper.selectOne(any())).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            // MP 的条件是惰性片段：先渲染 getSqlSegment，参数名值对才落袋
            w.getSqlSegment();
            String batchNo = null;
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof String) {
                    batchNo = (String) v;
                    break;
                }
            }
            for (TNslpFlowBatch b : batchStore) {
                if (b.getBatchNo().equals(batchNo)
                        && (b.getDelFlag() == null || b.getDelFlag() == 0)) {
                    return b;
                }
            }
            return null;
        });

        when(flowRowMapper.insert(any(TNslpFlowRow.class))).thenAnswer(inv -> {
            TNslpFlowRow r = inv.getArgument(0);
            r.setId(Long.valueOf(++idSeq));
            if (r.getCreateTime() == null) {
                r.setCreateTime(new Date());
            }
            rowStore.add(r);
            return Integer.valueOf(1);
        });
        when(flowRowMapper.selectList(any())).thenAnswer(inv -> filterRows(inv.getArgument(0)));
        when(flowRowMapper.selectOne(any())).thenAnswer(inv -> {
            List<TNslpFlowRow> hit = filterRows(inv.getArgument(0));
            return hit.isEmpty() ? null : hit.get(0);
        });
        when(flowRowMapper.selectById(any())).thenAnswer(inv -> findRow(inv.getArgument(0)));
        when(flowRowMapper.updateById(any(TNslpFlowRow.class))).thenAnswer(inv -> {
            TNslpFlowRow upd = inv.getArgument(0);
            TNslpFlowRow r = findRow(upd.getId());
            if (r == null) {
                return Integer.valueOf(0);
            }
            if (upd.getStatus() != null) {
                r.setStatus(upd.getStatus());
            }
            if (upd.getRetReason() != null) {
                r.setRetReason(upd.getRetReason());
            }
            if (upd.getLayerFlag() != null) {
                r.setLayerFlag(upd.getLayerFlag());
            }
            if (upd.getUpdateTime() != null) {
                r.setUpdateTime(upd.getUpdateTime());
            }
            return Integer.valueOf(1);
        });
    }

    private static TNslpPoint point(Long id, String pointNo, int needNum, String content, String zoneNo) {
        TNslpPoint p = new TNslpPoint();
        p.setId(id);
        p.setPointNo(pointNo);
        p.setNeedNum(Integer.valueOf(needNum));
        p.setContent(content);
        p.setZoneNo(zoneNo);
        p.setDelFlag(Integer.valueOf(0));
        return p;
    }

    private static FlowRowSubmit row(String code, String qty, String chk, String payload) {
        FlowRowSubmit r = new FlowRowSubmit();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setChkNum(chk == null ? null : new BigDecimal(chk));
        r.setPayload(payload);
        return r;
    }

    private static FlowBatchSubmit submit(String batchNo, Integer cap, String parseKind,
                                          FlowRowSubmit... rows) {
        FlowBatchSubmit s = new FlowBatchSubmit();
        s.setBatchNo(batchNo);
        s.setCapNum(cap);
        s.setParseKind(parseKind);
        s.setRows(new ArrayList<FlowRowSubmit>(Arrays.asList(rows)));
        return s;
    }

    private TNslpFlowRow storedRow(String batchNo, int rowNo) {
        for (TNslpFlowRow r : rowStore) {
            if (batchNo.equals(r.getBatchNo()) && r.getRowNo() != null && r.getRowNo() == rowNo) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 四个动作一口气
    // ------------------------------------------------------------------

    @Test
    public void firstArrivalRunsFourActions() {
        FlowReceipt receipt = service.submitBatch(submit("B1", null, "DELIMITER",
                row("PT0001", "8", "6", "55.0,56.1"),   // 收下（自动站直采）
                row("PT0001", "12", "9", "55.0"),       // 同一点位第二行（监测车补报）：高过上限10，退回
                row("PT0002", "0", "0", "55.0"),        // 读数次数为0
                row("PT0002", "-1", "0", "55.0"),       // 读数为负
                row("", "3", "1", "55.0"),              // 点位码没填
                row("PT0002", "3", null, "55.0"),       // 两数没一起到
                row("PT9999", "3", "1", "55.0"),        // 底册无此码
                row("PT0002", "3", "1", "abc")));       // 切不动

        // 回执三个数：8 行、收下 1、退回 7，后两个加起来等于第一个
        assertEquals(FlowReceipt.OUTCOME_ACCEPTED, receipt.getOutcome());
        assertEquals(8, receipt.getRowNum());
        assertEquals(1, receipt.getAcceptNum());
        assertEquals(7, receipt.getRetNum());
        assertEquals(receipt.getRowNum(), receipt.getAcceptNum() + receipt.getRetNum());
        assertFalse(receipt.isDuplicateArrival());
        // 抬头空格，顶按 300 认，连同点出来的 8 行落进本拨流水
        assertEquals(Integer.valueOf(300), receipt.getCapNum());
        assertEquals(1, batchStore.size());
        assertEquals(Integer.valueOf(300), batchStore.get(0).getCapNum());
        assertEquals(Integer.valueOf(8), batchStore.get(0).getRowNum());
        assertEquals(Integer.valueOf(FlowRowRules.BATCH_ACCEPTED), batchStore.get(0).getStatus());

        // 行次由系统在本拨内排：1..8
        assertEquals(8, rowStore.size());
        for (int i = 1; i <= 8; i++) {
            assertNotNull(storedRow("B1", i), "第 " + i + " 行该在流水里");
        }
        // 一处在册点位占两行是常事：各定各的，谁也不抹谁
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_ACCEPTED), storedRow("B1", 1).getStatus());
        assertNull(storedRow("B1", 1).getRetReason());
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_RETURNED), storedRow("B1", 2).getStatus());
        assertTrue(storedRow("B1", 2).getRetReason().startsWith(FlowRowRules.REASON_OVER_CAP));
        assertTrue(storedRow("B1", 2).getRetReason().contains("10"));
        // 哪一行吃了哪一条，流水里一句一句问得出
        assertEquals(FlowRowRules.REASON_ZERO, storedRow("B1", 3).getRetReason());
        assertEquals(FlowRowRules.REASON_NEGATIVE, storedRow("B1", 4).getRetReason());
        assertEquals(FlowRowRules.REASON_CODE_BLANK, storedRow("B1", 5).getRetReason());
        assertEquals(FlowRowRules.REASON_NUM_INCOMPLETE, storedRow("B1", 6).getRetReason());
        assertEquals(FlowRowRules.REASON_CODE_UNMATCHED, storedRow("B1", 7).getRetReason());
        assertEquals(FlowRowRules.REASON_PARSE_FAIL, storedRow("B1", 8).getRetReason());
        // 切不动的那一行单独标出，递给回核看的人
        assertEquals(Integer.valueOf(1), storedRow("B1", 8).getParseFlag());
        assertEquals(Arrays.asList(Integer.valueOf(8)), receipt.getParseFailedRowNos());
        // 行上的点位题名、区册代号以底册带出为准
        assertEquals("临江片区1号点", storedRow("B1", 1).getPointName());
        assertEquals("QN00", storedRow("B1", 1).getZoneNo());
        // 退回行明细问得出
        assertEquals(7, service.listErrors("B1").size());
    }

    @Test
    public void capComesOnlyFromBatchHeader() {
        // 填负、填得离谱都当没填，按 300 认
        assertEquals(Integer.valueOf(300),
                service.submitBatch(submit("C1", Integer.valueOf(-5), "DELIMITER")).getCapNum());
        assertEquals(Integer.valueOf(300),
                service.submitBatch(submit("C2", Integer.valueOf(99999999), "DELIMITER")).getCapNum());
        // 照填的认
        assertEquals(Integer.valueOf(2),
                service.submitBatch(submit("C3", Integer.valueOf(2), "DELIMITER")).getCapNum());

        // 超顶：整批未收，一行不落；点出来的数连同写定的顶照样落本拨流水
        FlowReceipt over = service.submitBatch(submit("C4", Integer.valueOf(2), "DELIMITER",
                row("PT0001", "1", "1", "55"),
                row("PT0001", "1", "1", "55"),
                row("PT0001", "1", "1", "55")));
        assertEquals(FlowReceipt.OUTCOME_OVER_CAP, over.getOutcome());
        assertEquals(3, over.getRowNum());
        assertEquals(0, over.getAcceptNum());
        assertEquals(0, over.getRetNum());
        assertEquals(Integer.valueOf(FlowRowRules.BATCH_OVER_CAP),
                batchStore.get(batchStore.size() - 1).getStatus());
        assertEquals(Integer.valueOf(3), batchStore.get(batchStore.size() - 1).getRowNum());
        for (TNslpFlowRow r : rowStore) {
            assertFalse("C4".equals(r.getBatchNo()), "超顶的拨一行都不该落");
        }
    }

    @Test
    public void duplicateArrivalOnlyShowsFace() {
        FlowReceipt first = service.submitBatch(submit("D1", null, "DELIMITER",
                row("PT0001", "2", "1", "55"),
                row("PT0002", "0", "0", "55")));
        assertEquals(2, first.getRowNum());
        assertEquals(1, first.getAcceptNum());
        assertEquals(1, first.getRetNum());

        // 区级上报走一回、市局复核又走一回：第二遍只许照面
        FlowReceipt second = service.submitBatch(submit("D1", null, "DELIMITER",
                row("PT0001", "9", "9", "55"),
                row("PT0001", "9", "9", "55"),
                row("PT0001", "9", "9", "55")));
        assertTrue(second.isDuplicateArrival());
        // 三个数照第一遍落地的流水原样给出
        assertEquals(2, second.getRowNum());
        assertEquals(1, second.getAcceptNum());
        assertEquals(1, second.getRetNum());
        // 不另起一拨、一行不插、已落地的行没翻回未勾
        assertEquals(1, batchStore.size());
        assertEquals(2, rowStore.size());
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_ACCEPTED), storedRow("D1", 1).getStatus());
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_RETURNED), storedRow("D1", 2).getStatus());

        // 超顶未收的拨再到，一样只照面
        service.submitBatch(submit("D2", Integer.valueOf(1), "DELIMITER",
                row("PT0001", "1", "1", "55"), row("PT0001", "1", "1", "55")));
        FlowReceipt again = service.submitBatch(submit("D2", null, "DELIMITER",
                row("PT0001", "1", "1", "55")));
        assertTrue(again.isDuplicateArrival());
        assertEquals(FlowReceipt.OUTCOME_OVER_CAP, again.getOutcome());
        assertEquals(2, rowStore.size());
    }

    @Test
    public void reReportKeepsOriginalRowNo() {
        service.submitBatch(submit("E1", null, "DELIMITER",
                row("PT0002", "0", "0", "55")));
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_RETURNED), storedRow("E1", 1).getStatus());

        // 被退回的那一行过几天重报：行面上仍然显示当初那一个行次，不重新排队
        FlowRowSubmit re = row("PT0002", "2", "1", "55");
        re.setRefBatchNo("E1");
        re.setRefRowNo(Integer.valueOf(1));
        FlowReceipt second = service.submitBatch(submit("E2", null, "DELIMITER", re));
        assertEquals(1, second.getAcceptNum());
        assertEquals(Integer.valueOf(1), storedRow("E2", 1).getRowNo());
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_ACCEPTED), storedRow("E2", 1).getStatus());
        // 当初那行的结论一字不动
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_RETURNED), storedRow("E1", 1).getStatus());
        assertEquals(FlowRowRules.REASON_ZERO, storedRow("E1", 1).getRetReason());

        // 指不到当初退回行的，按新行排队
        FlowRowSubmit bad = row("PT0002", "2", "1", "55");
        bad.setRefBatchNo("E1");
        bad.setRefRowNo(Integer.valueOf(99));
        service.submitBatch(submit("E3", null, "DELIMITER", bad, row("PT0001", "1", "1", "55")));
        assertEquals(Integer.valueOf(1), storedRow("E3", 1).getRowNo());
        assertEquals(Integer.valueOf(2), storedRow("E3", 2).getRowNo());
    }

    @Test
    public void missingOneNumberReturnedNotZeroFilled() {
        FlowReceipt receipt = service.submitBatch(submit("F1", null, "DELIMITER",
                row("PT0001", "5", null, "55"),
                row("PT0001", null, "3", "55")));
        assertEquals(0, receipt.getAcceptNum());
        assertEquals(2, receipt.getRetNum());
        // 系统不代填，也不许当成 0 收下：落库的仍是当初的空
        assertNull(storedRow("F1", 1).getChkNum());
        assertNull(storedRow("F1", 2).getQty());
        assertEquals(FlowRowRules.REASON_NUM_INCOMPLETE, storedRow("F1", 1).getRetReason());
        assertEquals(FlowRowRules.REASON_NUM_INCOMPLETE, storedRow("F1", 2).getRetReason());
    }

    @Test
    public void codeLengthAndUnmatched() {
        service.submitBatch(submit("G1", null, "DELIMITER",
                row("PT01", "1", "1", "55"),                        // 码长不够：一律算对不上
                row("PT000000000000000000001", "1", "1", "55"),     // 码长超出规范
                row("PT0001", "1", "1", "55")));                    // 一字不差，对上
        assertEquals(FlowRowRules.REASON_CODE_UNMATCHED, storedRow("G1", 1).getRetReason());
        assertEquals(FlowRowRules.REASON_CODE_TOO_LONG, storedRow("G1", 2).getRetReason());
        assertEquals(Integer.valueOf(FlowRowRules.STATUS_ACCEPTED), storedRow("G1", 3).getStatus());
    }

    @Test
    public void parseFailureMarksRowButNotBatch() {
        FlowBatchSubmit s = submit("H1", null, "FIXED",
                row("PT0001", "2", "1", "5560"),
                row("PT0001", "2", "1", "556"),
                row("PT0001", "2", "1", "5560"));
        s.setFixWidth(Integer.valueOf(2));
        FlowReceipt receipt = service.submitBatch(s);
        // 切不动的那一行单独标出，整拨不被它按住
        assertEquals(FlowReceipt.OUTCOME_ACCEPTED, receipt.getOutcome());
        assertEquals(2, receipt.getAcceptNum());
        assertEquals(1, receipt.getRetNum());
        assertEquals(Arrays.asList(Integer.valueOf(2)), receipt.getParseFailedRowNos());
        assertEquals(FlowRowRules.REASON_PARSE_FAIL, storedRow("H1", 2).getRetReason());

        // 报了第三种切法：整拨每行都切不动，但拨照样落地
        FlowReceipt weird = service.submitBatch(submit("H2", null, "ROT13",
                row("PT0001", "2", "1", "55")));
        assertEquals(1, weird.getRetNum());
        assertEquals(FlowRowRules.REASON_PARSE_FAIL, storedRow("H2", 1).getRetReason());
    }

    @Test
    public void registerDiffListedSeparately() {
        FlowRowSubmit diff = row("PT0001", "2", "1", "55");
        diff.setReportZoneNo("QN99");
        diff.setReportPointName("临江片区1号点");
        FlowRowSubmit same = row("PT0002", "2", "1", "55");
        same.setReportZoneNo("QN00");
        FlowReceipt receipt = service.submitBatch(submit("I1", null, "DELIMITER", diff, same));

        // 两处不合以底册为准，回执单列出来
        assertEquals(Arrays.asList(Integer.valueOf(1)), receipt.getRegDiffRowNos());
        assertEquals(Integer.valueOf(1), storedRow("I1", 1).getRegDiffFlag());
        assertEquals("QN00", storedRow("I1", 1).getZoneNo());
        assertEquals("临江片区1号点", storedRow("I1", 1).getPointName());
        assertEquals(Integer.valueOf(0), storedRow("I1", 2).getRegDiffFlag());
    }

    @Test
    public void receiptSameComputationEverywhere() {
        FlowReceipt atIntake = service.submitBatch(submit("J1", null, "DELIMITER",
                row("PT0001", "2", "1", "55"),
                row("PT0002", "0", "0", "55")));
        // 页面上那份只是转述：两处出自同一次计算
        FlowReceipt forPage = service.getReceipt("J1");
        assertEquals(atIntake.getRowNum(), forPage.getRowNum());
        assertEquals(atIntake.getAcceptNum(), forPage.getAcceptNum());
        assertEquals(atIntake.getRetNum(), forPage.getRetNum());
        assertFalse(forPage.isDuplicateArrival());
        assertNull(service.getReceipt("没落地过的码"));
        assertNull(service.getReceipt(" "));
    }

    @Test
    public void reconcileNamesOffenders() {
        service.submitBatch(submit("K1", null, "DELIMITER",
                row("PT0001", "2", "1", "55"),
                row("PT0002", "0", "0", "55")));
        FlowReconcile good = service.reconcile("K1");
        assertTrue(good.isConsistent());
        assertTrue(good.getOffenders().isEmpty());
        assertEquals(Integer.valueOf(2), good.getStoredRowNum());
        assertEquals(2, good.getLedgerRowNum());

        // 第四种写法落了库：一行一行点名
        TNslpFlowRow alien = new TNslpFlowRow();
        alien.setBatchNo("K1");
        alien.setRowNo(Integer.valueOf(9));
        alien.setStatus(Integer.valueOf(9));
        alien.setLayerFlag(Integer.valueOf(0));
        alien.setDelFlag(Integer.valueOf(0));
        flowRowMapper.insert(alien);
        FlowReconcile bad = service.reconcile("K1");
        assertFalse(bad.isConsistent());
        assertEquals(1, bad.getOffenders().size());
        assertEquals(Integer.valueOf(9), bad.getOffenders().get(0).getRowNo());

        // 南津那一出：拨上记着 25 行，流水里只数得出 6 行，对不住就是印不上
        TNslpFlowBatch legacy = new TNslpFlowBatch();
        legacy.setBatchNo("K2");
        legacy.setCapNum(Integer.valueOf(300));
        legacy.setRowNum(Integer.valueOf(25));
        legacy.setStatus(Integer.valueOf(FlowRowRules.BATCH_ACCEPTED));
        legacy.setDelFlag(Integer.valueOf(0));
        flowBatchMapper.insert(legacy);
        for (int i = 1; i <= 6; i++) {
            TNslpFlowRow r = new TNslpFlowRow();
            r.setBatchNo("K2");
            r.setRowNo(Integer.valueOf(i));
            r.setStatus(Integer.valueOf(FlowRowRules.STATUS_ACCEPTED));
            r.setLayerFlag(Integer.valueOf(0));
            r.setDelFlag(Integer.valueOf(0));
            flowRowMapper.insert(r);
        }
        FlowReconcile gap = service.reconcile("K2");
        assertFalse(gap.isConsistent());
        assertEquals(Integer.valueOf(25), gap.getStoredRowNum());
        assertEquals(6, gap.getLedgerRowNum());

        assertNull(service.reconcile("没落地过的码"));
    }

    @Test
    public void uncheckedRowInAcceptedBatchIsNamed() {
        service.submitBatch(submit("L1", null, "DELIMITER", row("PT0001", "2", "1", "55")));
        // 已核收的拨里混进一行空悬未勾（存量脏行）：印不上，点名
        TNslpFlowRow hanging = new TNslpFlowRow();
        hanging.setBatchNo("L1");
        hanging.setRowNo(Integer.valueOf(7));
        hanging.setStatus(Integer.valueOf(FlowRowRules.STATUS_UNCHECKED));
        hanging.setLayerFlag(Integer.valueOf(FlowRowRules.LAYER_CURRENT));
        hanging.setDelFlag(Integer.valueOf(0));
        flowRowMapper.insert(hanging);
        FlowReconcile r = service.reconcile("L1");
        assertFalse(r.isConsistent());
        assertEquals(1, r.getOffenders().size());
        assertEquals(Integer.valueOf(7), r.getOffenders().get(0).getRowNo());
    }

    @Test
    public void historyLayerTransferAndRestore() {
        // 老行：未勾空悬，正压满两个年度那条线
        craftRow("M0", 1, FlowRowRules.STATUS_UNCHECKED, FlowRowRules.LAYER_CURRENT,
                t("2024-10-04 00:00:00"), 0);
        // 未勾但还没满两年
        craftRow("M0", 2, FlowRowRules.STATUS_UNCHECKED, FlowRowRules.LAYER_CURRENT,
                t("2024-10-04 00:00:01"), 0);
        // 早已定下去处的老行：新拨走不到它跟前，老规矩管不着
        craftRow("M0", 3, FlowRowRules.STATUS_ACCEPTED, FlowRowRules.LAYER_CURRENT,
                t("2020-01-01 00:00:00"), 0);
        // 先前入过账（已在历史册层）的存量：一条不减
        craftRow("M0", 4, FlowRowRules.STATUS_UNCHECKED, FlowRowRules.LAYER_HISTORY,
                t("2020-01-01 00:00:00"), 0);
        // 已删的不算
        craftRow("M0", 5, FlowRowRules.STATUS_UNCHECKED, FlowRowRules.LAYER_CURRENT,
                t("2020-01-01 00:00:00"), 1);

        int moved = service.transferToHistory(t("2026-10-04 00:00:00"));
        assertEquals(1, moved);
        // 满两年的那行转入历史册层，现行名单不再列它，历史册层单独查得着
        assertEquals(Integer.valueOf(FlowRowRules.LAYER_HISTORY), rowStore.get(0).getLayerFlag());
        assertEquals(Integer.valueOf(FlowRowRules.LAYER_CURRENT), rowStore.get(1).getLayerFlag());
        assertEquals(Integer.valueOf(FlowRowRules.LAYER_CURRENT), rowStore.get(2).getLayerFlag());
        // 现行名单只剩未满两年的未勾行与已定去处的老行（已删的不算）
        assertEquals(2, service.listRows("M0").size());
        assertEquals(2, service.listHistory("M0").size());
        assertEquals(2, service.listHistory(null).size());
        // 再转一趟：没有够格的，一条不动
        assertEquals(0, service.transferToHistory(t("2026-10-04 00:00:00")));

        // 取出同样只由这一处作答
        Long movedId = rowStore.get(0).getId();
        assertEquals(1, service.restoreFromHistory(movedId));
        assertEquals(Integer.valueOf(FlowRowRules.LAYER_CURRENT), rowStore.get(0).getLayerFlag());
        assertEquals(3, service.listRows("M0").size());
        // 不在历史册层的行，取不出
        assertEquals(0, service.restoreFromHistory(rowStore.get(2).getId()));
        assertEquals(0, service.restoreFromHistory(null));
    }

    @Test
    public void emptyBatchAndBlankBatchNo() {
        // 空批次：点出 0 行，拨照样落档，回执 0/0/0
        FlowReceipt empty = service.submitBatch(submit("N1", null, "DELIMITER"));
        assertEquals(0, empty.getRowNum());
        assertEquals(0, empty.getAcceptNum());
        assertEquals(0, empty.getRetNum());
        assertEquals(Integer.valueOf(0), batchStore.get(0).getRowNum());

        FlowBatchSubmit noRows = submit("N2", null, "DELIMITER");
        noRows.setRows(null);
        assertEquals(0, service.submitBatch(noRows).getRowNum());

        // 批次码没填，一拨落不了户
        assertThrows(IllegalArgumentException.class,
                () -> service.submitBatch(submit(" ", null, "DELIMITER")));
        assertThrows(IllegalArgumentException.class, () -> service.submitBatch(null));
    }

    private TNslpFlowRow craftRow(String batchNo, int rowNo, int status, int layer,
                                  Date createTime, int delFlag) {
        TNslpFlowRow r = new TNslpFlowRow();
        r.setId(Long.valueOf(++idSeq));
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(Integer.valueOf(status));
        r.setLayerFlag(Integer.valueOf(layer));
        r.setDelFlag(Integer.valueOf(delFlag));
        r.setCreateTime(createTime);
        rowStore.add(r);
        return r;
    }
}
