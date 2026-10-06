package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
import com.fc.v2.mapper.auto.TNslpDocIssueMapper;
import com.fc.v2.mapper.auto.TNslpDocTplMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpDocIssue;
import com.fc.v2.model.auto.TNslpDocTpl;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.DocIssueResult;
import com.fc.v2.model.custom.DocIssueSubmit;
import com.fc.v2.service.support.DocTplRules;

/**
 * 版次窗口选版、新旧版不串、撤版仍可主键查、缺项逐项点名、出文钉版留档、
 * 按版次取值与整表取优同答。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpDocTplServiceImplTest {

    @Mock
    private TNslpDocTplMapper nslpDocTplMapper;
    @Mock
    private TNslpDocIssueMapper nslpDocIssueMapper;
    @Mock
    private TNslpPointMapper nslpPointMapper;
    @InjectMocks
    private TNslpDocTplServiceImpl service;

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private List<TNslpDocTpl> tplStore;
    private List<TNslpDocIssue> issueStore;
    private List<TNslpPoint> pointStore;
    private long idSeq;
    private long issueSeq;

    static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Pattern EQ = Pattern.compile(
            "(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");

    @BeforeEach
    public void setUp() {
        tplStore = new ArrayList<TNslpDocTpl>();
        issueStore = new ArrayList<TNslpDocIssue>();
        pointStore = new ArrayList<TNslpPoint>();
        idSeq = 0;
        issueSeq = 0;

        when(nslpDocTplMapper.insert(any(TNslpDocTpl.class))).thenAnswer(inv -> {
            TNslpDocTpl d = inv.getArgument(0);
            d.setId(Long.valueOf(++idSeq));
            tplStore.add(d);
            return 1;
        });
        when(nslpDocTplMapper.updateById(any(TNslpDocTpl.class))).thenAnswer(inv -> {
            TNslpDocTpl u = inv.getArgument(0);
            for (int i = 0; i < tplStore.size(); i++) {
                if (tplStore.get(i).getId().equals(u.getId())) {
                    tplStore.set(i, u);
                    return 1;
                }
            }
            return 0;
        });
        when(nslpDocTplMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TNslpDocTpl d : tplStore) {
                if (d.getId().equals(id)) {
                    return d;
                }
            }
            return null;
        });
        when(nslpDocTplMapper.selectList(any())).thenAnswer(inv -> filterTpl(inv.getArgument(0)));

        when(nslpDocIssueMapper.insert(any(TNslpDocIssue.class))).thenAnswer(inv -> {
            TNslpDocIssue d = inv.getArgument(0);
            d.setId(Long.valueOf(++issueSeq));
            issueStore.add(d);
            return 1;
        });
        when(nslpPointMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TNslpPoint p : pointStore) {
                if (p.getId().equals(id)) {
                    return p;
                }
            }
            return null;
        });
    }

    private TNslpDocTpl addTpl(long id, String name, int ver, int status, int varNum,
                               String start, String end) {
        TNslpDocTpl d = new TNslpDocTpl();
        d.setId(Long.valueOf(id));
        d.setTplName(name);
        d.setVerNo(Integer.valueOf(ver));
        d.setVarNum(Integer.valueOf(varNum));
        d.setFillNum(Integer.valueOf(0));
        d.setStatus(Integer.valueOf(status));
        d.setEffStart(t(start));
        d.setEffEnd(end == null ? null : t(end));
        d.setDelFlag(0);
        tplStore.add(d);
        return d;
    }

    private TNslpPoint addPoint(long id, String title, String limitCode) {
        TNslpPoint p = new TNslpPoint();
        p.setId(Long.valueOf(id));
        p.setPointNo("PT" + id);
        p.setContent(title);
        p.setPinnedRuleCode(limitCode);
        p.setDelFlag(0);
        pointStore.add(p);
        return p;
    }

    private List<TNslpDocTpl> filterTpl(QueryWrapper<?> w) {
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        java.util.Map<String, Object> pm = w.getParamNameValuePairs();
        List<TNslpDocTpl> out = new ArrayList<TNslpDocTpl>();
        for (TNslpDocTpl d : tplStore) {
            boolean ok = true;
            Matcher m = EQ.matcher(seg);
            while (m.find()) {
                String col = m.group(1);
                Object val = pm.get(m.group(2));
                Object f = "del_flag".equals(col) ? d.getDelFlag()
                        : "tpl_name".equals(col) ? d.getTplName() : null;
                if (f == null || !f.equals(val) && !String.valueOf(f).equals(String.valueOf(val))) {
                    ok = false;
                }
            }
            if (ok) {
                out.add(d);
            }
        }
        return out;
    }

    @Test
    public void picksHighestActiveVersionByIssueMoment() {
        addTpl(1, "超标告知", 1, 0, 2, "2024-01-01 00:00:00", "2025-01-01 00:00:00");
        addTpl(2, "超标告知", 2, 0, 2, "2025-01-01 00:00:00", "2026-01-01 00:00:00");
        addTpl(3, "超标告知", 3, 0, 2, "2026-01-01 00:00:00", null);

        // 2025 年中：第 2 版开口
        assertEquals(Integer.valueOf(2), service.resolveVersion("超标告知", t("2025-06-01 00:00:00")).getVerNo());
        // 2026 年中：第 3 版开口
        assertEquals(Integer.valueOf(3), service.resolveVersion("超标告知", t("2026-06-01 00:00:00")).getVerNo());
        // 整表取优（resolveVersion）与现行清单首版同答
        List<TNslpDocTpl> cur = service.listCurrent("超标告知", t("2026-06-01 00:00:00"));
        assertEquals(Integer.valueOf(3), cur.get(0).getVerNo());
        assertEquals(service.resolveVersion("超标告知", t("2026-06-01 00:00:00")).getId(), cur.get(0).getId());
    }

    @Test
    public void withdrawnVersionNotCurrentButGettableById() {
        TNslpDocTpl wv = addTpl(7, "复查结论", 9, 1, 2, "2025-01-01 00:00:00", null);
        assertNull(service.resolveVersion("复查结论", t("2026-01-01 00:00:00")));
        assertTrue(service.listCurrent("复查结论", t("2026-01-01 00:00:00")).isEmpty());
        // 照主键仍查得着
        assertNotNull(service.selectById(wv.getId()));
    }

    @Test
    public void saveRecomputesMissAndRejectsBad() {
        TNslpDocTpl d = new TNslpDocTpl();
        d.setTplName("责令限期改正");
        d.setVerNo(1);
        d.setVarNum(5);
        d.setFillNum(3);
        d.setEffStart(t("2026-01-01 00:00:00"));
        TNslpDocTpl saved = service.saveVersion(d, "版管岗");
        // miss 由服务端轧：5-3=2，盖过手填
        assertEquals(Integer.valueOf(2), saved.getMissNum());

        TNslpDocTpl bad = new TNslpDocTpl();
        bad.setTplName("别的文种");
        bad.setVarNum(2);
        bad.setEffStart(t("2026-01-01 00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> service.saveVersion(bad, "u"));
        assertThrows(IllegalArgumentException.class, () -> service.saveVersion(d, " "));
    }

    @Test
    public void issueMissingItemsNamedAndNoBlankPaper() {
        addTpl(1, "超标告知", 1, 0, 2, "2025-01-01 00:00:00", null);
        TNslpPoint p = addPoint(10, "  ", null); // 题名空、限值线没挂

        DocIssueSubmit s = new DocIssueSubmit();
        s.setTplName("超标告知");
        s.setPointId(p.getId());
        s.setIssueTime(t("2026-01-01 00:00:00"));
        s.setOperator("出文岗");
        DocIssueResult r = service.issue(s);
        assertFalse(r.isIssued());
        assertEquals(2, r.getMissing().size());
        assertTrue(r.getMissing().contains(DocTplRules.ITEM_POINT_TITLE));
        assertTrue(r.getMissing().contains(DocTplRules.ITEM_LIMIT_LINE));
        assertEquals(Integer.valueOf(0), r.getFillNum());
        assertEquals(Integer.valueOf(2), r.getMissNum());
        // 没出文不留档
        assertEquals(0, issueStore.size());
    }

    @Test
    public void issuePinsVersionAndNewVersionDoesNotRewriteOld() {
        TNslpDocTpl v1 = addTpl(1, "超标告知", 1, 0, 2, "2025-01-01 00:00:00", null);
        TNslpDocTpl v2 = addTpl(2, "超标告知", 2, 0, 2, "2026-06-01 00:00:00", null);
        TNslpPoint p = addPoint(10, "临江1号", "RL-DAY");

        DocIssueSubmit s = new DocIssueSubmit();
        s.setTplName("超标告知");
        s.setPointId(p.getId());
        s.setIssueTime(t("2026-01-01 00:00:00")); // v1 开口
        s.setOperator("出文岗");
        DocIssueResult r = service.issue(s);
        assertTrue(r.isIssued());
        assertEquals(v1.getId(), r.getIssue().getTplId());
        assertEquals(Integer.valueOf(1), r.getIssue().getVerNo());

        // 后来立的 v2 不回改旧文：旧档仍钉 v1
        assertEquals(Integer.valueOf(1), issueStore.get(0).getVerNo());
        assertEquals(v1.getId(), issueStore.get(0).getTplId());

        // 撤掉 v1 也不影响已出旧档
        service.withdrawVersion(v1.getId(), "版管岗");
        assertEquals(Integer.valueOf(1), issueStore.get(0).getVerNo());
    }

    @Test
    public void issueWithoutActiveVersionBlocked() {
        addTpl(1, "超标告知", 1, 0, 2, "2030-01-01 00:00:00", null);
        TNslpPoint p = addPoint(10, "临江1号", "RL-DAY");
        DocIssueSubmit s = new DocIssueSubmit();
        s.setTplName("超标告知");
        s.setPointId(p.getId());
        s.setIssueTime(t("2026-01-01 00:00:00"));
        s.setOperator("出文岗");
        DocIssueResult r = service.issue(s);
        assertFalse(r.isIssued());
        assertTrue(r.getError().contains("没有在位的版"));
    }
}
