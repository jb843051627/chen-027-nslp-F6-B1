package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fc.v2.service.ITNslpFlowRowService;
import com.fc.v2.service.support.FlowRowRules;

/**
 * 监测流水核收 Service业务层处理（batch-process 形状：整批提交）。
 *
 * 核收四个动作一口气走完：先把行数点出来（顶只认抬头那一格），再把数据看
 * 一遍（两数齐、点位码、报文切法），然后给每一行定个去处（收下／退回，退回
 * 写明哪一条），末了出一张回执（三个数同行数出）。同一批次码第二回到达只
 * 照面。口径全部收在 {@link FlowRowRules}，本类只负责落库与装配，不另起算法。
 *
 * @author fuce
 * @date 2026-10-04
 */
@Service
public class TNslpFlowRowServiceImpl implements ITNslpFlowRowService {

    @javax.annotation.Resource
    private TNslpFlowRowMapper nslpFlowRowMapper;

    @javax.annotation.Resource
    private TNslpFlowBatchMapper nslpFlowBatchMapper;

    @javax.annotation.Resource
    private TNslpPointMapper nslpPointMapper;

    @Override
    public TNslpFlowRow selectTNslpFlowRowById(Long id) {
        return this.nslpFlowRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowReceipt submitBatch(FlowBatchSubmit submit) {
        if (submit == null || submit.getBatchNo() == null || submit.getBatchNo().trim().isEmpty()) {
            throw new IllegalArgumentException("批次码没填，一拨落不了户");
        }
        String batchNo = submit.getBatchNo().trim();

        // 同一批次码第二回到达：只照面。不把第一遍已经落地的行翻回未勾，
        // 也不另起一拨、一行不插；回执照第一遍落地的流水原样给出。
        TNslpFlowBatch existing = findBatch(batchNo);
        if (existing != null) {
            return FlowReceipt.of(existing, selectAllRows(batchNo), true);
        }

        // 动作一·点行数：顶只认抬头那一格（空/负/离谱按 300 认），点出来的
        // 行数连同写定的顶先落本拨流水，事后翻得出来当初点了几行。
        int cap = FlowRowRules.recognizeCap(submit.getCapNum());
        List<FlowRowSubmit> raws = submit.getRows() == null
                ? Collections.<FlowRowSubmit>emptyList() : submit.getRows();
        int counted = raws.size();
        TNslpFlowBatch batch = newBatch(batchNo, cap, counted, submit.getParseKind());
        if (counted > cap) {
            // 一拨容多少行有个顶：超了顶整批未收，一行不落（拨不许拆着交）
            batch.setStatus(FlowRowRules.BATCH_OVER_CAP);
            this.nslpFlowBatchMapper.insert(batch);
            return FlowReceipt.of(batch, Collections.<TNslpFlowRow>emptyList(), false);
        }
        batch.setStatus(FlowRowRules.BATCH_ACCEPTED);
        this.nslpFlowBatchMapper.insert(batch);

        // 动作二·看数据 ＋ 动作三·定去处：逐行过，各定各的去处，谁也不抹谁的结论
        Map<String, TNslpPoint> register = registerByPointNo();
        List<TNslpFlowRow> decided = new ArrayList<TNslpFlowRow>(counted);
        int seq = 0;
        for (FlowRowSubmit raw : raws) {
            FlowRowSubmit r = raw == null ? new FlowRowSubmit() : raw;
            List<BigDecimal> parsed = FlowRowRules.parsePayload(
                    r.getPayload(), submit.getParseKind(), submit.getFixWidth());
            TNslpPoint point = matchPoint(register, r.getItemCode());
            String reason = FlowRowRules.decideReturn(r.getItemCode(), r.getQty(), r.getChkNum(),
                    point != null, point == null ? null : point.getNeedNum(), parsed);

            TNslpFlowRow row = new TNslpFlowRow();
            row.setBatchNo(batchNo);
            // 行次由系统在本拨内排，不留手填的格子；退回重报的行，行面上仍然
            // 显示当初那一个行次，不重新排队
            Integer refRowNo = refRowNoOf(r);
            row.setRowNo(refRowNo != null ? refRowNo : Integer.valueOf(++seq));
            row.setItemCode(r.getItemCode());
            row.setQty(r.getQty());
            row.setChkNum(r.getChkNum());
            row.setPutKind(r.getPutKind());
            row.setStatus(reason == null ? FlowRowRules.STATUS_ACCEPTED : FlowRowRules.STATUS_RETURNED);
            row.setRetReason(reason);
            row.setParseFlag(parsed == null ? Integer.valueOf(1) : Integer.valueOf(0));
            row.setRegDiffFlag(regDiff(point, r) ? Integer.valueOf(1) : Integer.valueOf(0));
            if (point != null) {
                // 行上的点位题名、区册代号以点位底册带出的为准
                row.setPointName(point.getContent());
                row.setZoneNo(point.getZoneNo());
            }
            row.setLayerFlag(FlowRowRules.LAYER_CURRENT);
            row.setDelFlag(0);
            this.nslpFlowRowMapper.insert(row);
            decided.add(row);
        }

        // 动作四·出回执：三个数从这一趟定完去处的行里一行一行数出来
        return FlowReceipt.of(batch, decided, false);
    }

    @Override
    public FlowReceipt getReceipt(String batchNo) {
        // 回执唯一出处：页面那份只是转述，对不住时以这一处为准
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return null;
        }
        TNslpFlowBatch batch = findBatch(batchNo.trim());
        if (batch == null) {
            return null;
        }
        return FlowReceipt.of(batch, selectAllRows(batchNo.trim()), false);
    }

    @Override
    public List<TNslpFlowRow> listRows(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TNslpFlowRow>();
        }
        // 现行名单：历史册层里的不再列
        return this.nslpFlowRowMapper.selectList(new QueryWrapper<TNslpFlowRow>()
                .eq("batch_no", batchNo.trim())
                .eq("layer_flag", FlowRowRules.LAYER_CURRENT)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TNslpFlowRow> listErrors(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TNslpFlowRow>();
        }
        // 退回行明细：哪一行吃了哪一条理由，ret_reason 里一句一句问得出
        return this.nslpFlowRowMapper.selectList(new QueryWrapper<TNslpFlowRow>()
                .eq("batch_no", batchNo.trim())
                .eq("status", FlowRowRules.STATUS_RETURNED)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TNslpFlowRow> listHistory(String batchNo) {
        QueryWrapper<TNslpFlowRow> wrapper = new QueryWrapper<TNslpFlowRow>()
                .eq("layer_flag", FlowRowRules.LAYER_HISTORY)
                .eq("del_flag", 0)
                .orderByAsc("batch_no", "row_no");
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            wrapper.eq("batch_no", batchNo.trim());
        }
        return this.nslpFlowRowMapper.selectList(wrapper);
    }

    @Override
    public FlowReconcile reconcile(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return null;
        }
        TNslpFlowBatch batch = findBatch(batchNo.trim());
        if (batch == null) {
            return null;
        }
        List<TNslpFlowRow> rows = selectAllRows(batchNo.trim());
        boolean overCap = batch.getStatus() != null
                && batch.getStatus() == FlowRowRules.BATCH_OVER_CAP;
        List<TNslpFlowRow> offenders = new ArrayList<TNslpFlowRow>();
        int accept = 0;
        int ret = 0;
        for (TNslpFlowRow row : rows) {
            if (!FlowRowRules.validStatus(row.getStatus())) {
                // 第四种写法不许落库；落了的在此点名
                offenders.add(row);
                continue;
            }
            if (row.getStatus() == FlowRowRules.STATUS_ACCEPTED) {
                accept++;
            } else if (row.getStatus() == FlowRowRules.STATUS_RETURNED) {
                ret++;
            } else if (!overCap && (row.getLayerFlag() == null
                    || row.getLayerFlag() == FlowRowRules.LAYER_CURRENT)) {
                // 已核收的拨里不该空悬未勾（历史册层里躺着的老行不算）
                offenders.add(row);
            }
        }
        boolean consistent = offenders.isEmpty()
                && (overCap
                        ? rows.isEmpty()
                        : batch.getRowNum() != null && batch.getRowNum() == rows.size()
                                && accept + ret == rows.size());
        return FlowReconcile.of(batchNo.trim(), batch.getRowNum(), rows.size(),
                accept, ret, consistent, offenders);
    }

    @Override
    public int transferToHistory(Date now) {
        if (now == null) {
            return 0;
        }
        // 老规矩只管「一直空悬未勾」满两个年度的行；新拨的行进门就定了去处，
        // 走不到它跟前。存量里入过账的一条不动，只挪册层标记。
        List<TNslpFlowRow> stale = this.nslpFlowRowMapper.selectList(new QueryWrapper<TNslpFlowRow>()
                .eq("status", FlowRowRules.STATUS_UNCHECKED)
                .eq("layer_flag", FlowRowRules.LAYER_CURRENT)
                .eq("del_flag", 0)
                .le("create_time", FlowRowRules.twoYearsBefore(now)));
        int moved = 0;
        for (TNslpFlowRow row : stale) {
            TNslpFlowRow upd = new TNslpFlowRow();
            upd.setId(row.getId());
            upd.setLayerFlag(FlowRowRules.LAYER_HISTORY);
            upd.setUpdateTime(now);
            moved += this.nslpFlowRowMapper.updateById(upd);
        }
        return moved;
    }

    @Override
    public int restoreFromHistory(Long id) {
        if (id == null) {
            return 0;
        }
        TNslpFlowRow row = this.nslpFlowRowMapper.selectById(id);
        if (row == null || row.getLayerFlag() == null
                || row.getLayerFlag() != FlowRowRules.LAYER_HISTORY) {
            return 0;
        }
        TNslpFlowRow upd = new TNslpFlowRow();
        upd.setId(id);
        upd.setLayerFlag(FlowRowRules.LAYER_CURRENT);
        upd.setUpdateTime(new Date());
        return this.nslpFlowRowMapper.updateById(upd);
    }

    // ------------------------------------------------------------------
    // 内部：一面流水 + 一处认定
    // ------------------------------------------------------------------

    /** 按批次码点拨抬头；没落地过为 null */
    private TNslpFlowBatch findBatch(String batchNo) {
        return this.nslpFlowBatchMapper.selectOne(new QueryWrapper<TNslpFlowBatch>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .last("limit 1"));
    }

    /** 本拨全部流水（含历史册层；回执与复核要数就数全拨） */
    private List<TNslpFlowRow> selectAllRows(String batchNo) {
        return this.nslpFlowRowMapper.selectList(new QueryWrapper<TNslpFlowRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    /** 点位底册一面：只认点位码，一字不差才算对上 */
    private Map<String, TNslpPoint> registerByPointNo() {
        Map<String, TNslpPoint> map = new HashMap<String, TNslpPoint>();
        for (TNslpPoint p : this.nslpPointMapper.selectList(
                new QueryWrapper<TNslpPoint>().eq("del_flag", 0))) {
            if (p.getPointNo() != null) {
                map.put(p.getPointNo().trim(), p);
            }
        }
        return map;
    }

    /** 点位是谁只认点位码；码长不够的一律算对不上，拿题名近似去凑的不认 */
    private TNslpPoint matchPoint(Map<String, TNslpPoint> register, String itemCode) {
        if (FlowRowRules.codeBlank(itemCode) || FlowRowRules.codeTooShort(itemCode)
                || FlowRowRules.codeTooLong(itemCode)) {
            return null;
        }
        return register.get(itemCode.trim());
    }

    /** 报文里那串字符只拿来比对：题名、区册代号有一处不合即标出，行面上仍以底册为准 */
    private boolean regDiff(TNslpPoint point, FlowRowSubmit r) {
        if (point == null) {
            return false;
        }
        if (notEmpty(r.getReportPointName()) && !r.getReportPointName().equals(point.getContent())) {
            return true;
        }
        if (notEmpty(r.getReportZoneNo()) && !r.getReportZoneNo().equals(point.getZoneNo())) {
            return true;
        }
        return false;
    }

    /**
     * 退回重报：指着当初那一行（同批次码＋行次、且确已退回）的，行面上仍然
     * 显示当初那一个行次；指不上的按新行排队。当初那行一字不动。
     */
    private Integer refRowNoOf(FlowRowSubmit r) {
        if (r.getRefBatchNo() == null || r.getRefBatchNo().trim().isEmpty()
                || r.getRefRowNo() == null) {
            return null;
        }
        TNslpFlowRow ref = this.nslpFlowRowMapper.selectOne(new QueryWrapper<TNslpFlowRow>()
                .eq("batch_no", r.getRefBatchNo().trim())
                .eq("row_no", r.getRefRowNo())
                .eq("status", FlowRowRules.STATUS_RETURNED)
                .eq("del_flag", 0)
                .last("limit 1"));
        return ref == null ? null : ref.getRowNo();
    }

    private TNslpFlowBatch newBatch(String batchNo, int cap, int counted, String parseKind) {
        TNslpFlowBatch batch = new TNslpFlowBatch();
        batch.setBatchNo(batchNo);
        batch.setCapNum(cap);
        batch.setRowNum(counted);
        batch.setParseKind(parseKind == null ? null : parseKind.trim());
        batch.setDelFlag(0);
        return batch;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
