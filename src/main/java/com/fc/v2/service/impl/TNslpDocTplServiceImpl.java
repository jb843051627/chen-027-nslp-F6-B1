package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpDocIssueMapper;
import com.fc.v2.mapper.auto.TNslpDocTplMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpDocIssue;
import com.fc.v2.model.auto.TNslpDocTpl;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.DocIssueResult;
import com.fc.v2.model.custom.DocIssueSubmit;
import com.fc.v2.service.ITNslpDocTplService;
import com.fc.v2.service.support.DocTplRules;

/**
 * 执法文书版次与出文 Service业务层处理。
 *
 * 口径全部收在 {@link DocTplRules}：选版只看出文那一刻的效期窗与版次大小；还差几项
 * 只在服务端由应填－已取轧出；缺项一项一项点名；出文落档把当版钉住，新版不回改旧档。
 * 页面不另算，按版次取值与整表取优同走 {@link #resolveVersion}，两答必同一版。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TNslpDocTplServiceImpl implements ITNslpDocTplService {

    @javax.annotation.Resource
    private TNslpDocTplMapper nslpDocTplMapper;

    @javax.annotation.Resource
    private TNslpDocIssueMapper nslpDocIssueMapper;

    @javax.annotation.Resource
    private TNslpPointMapper nslpPointMapper;

    @Override
    public TNslpDocTpl selectById(Long id) {
        // 撤版的版照主键仍查得着，只是不进现行清单、不开口
        return this.nslpDocTplMapper.selectById(id);
    }

    @Override
    public List<TNslpDocTpl> listCurrent(String tplName, Date at) {
        if (!DocTplRules.isKnownName(tplName) || at == null) {
            return new ArrayList<TNslpDocTpl>();
        }
        List<TNslpDocTpl> hit = new ArrayList<TNslpDocTpl>();
        for (TNslpDocTpl tpl : snapshotByName(tplName)) {
            if (DocTplRules.checkTpl(tpl) == null && DocTplRules.activeAt(tpl, at)) {
                hit.add(tpl);
            }
        }
        Collections.sort(hit, DocTplRules.winnerOrder());
        return hit;
    }

    @Override
    public TNslpDocTpl resolveVersion(String tplName, Date at) {
        List<TNslpDocTpl> current = listCurrent(tplName, at);
        return current.isEmpty() ? null : current.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpDocTpl saveVersion(TNslpDocTpl tpl, String operator) {
        if (tpl == null) {
            throw new IllegalArgumentException("文书版本没递上来，立不了版");
        }
        if (!DocTplRules.isKnownName(tpl.getTplName())) {
            throw new IllegalArgumentException("文种只认「" + DocTplRules.NAME_OVER_STANDARD + "·"
                    + DocTplRules.NAME_ORDER_CORRECT + "·" + DocTplRules.NAME_REVIEW_CONCLUSION
                    + "」，递进来的是「" + tpl.getTplName() + "」");
        }
        String err = DocTplRules.checkTpl(tpl);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("立版没留下落笔人，按没办完处理");
        }
        int fill = tpl.getFillNum() == null || tpl.getFillNum().intValue() < 0
                ? 0 : tpl.getFillNum().intValue();
        // 第三个数不留手填格子：由前两个相减得，服务端这一处写回
        tpl.setFillNum(fill);
        tpl.setMissNum(DocTplRules.miss(tpl.getVarNum(), fill));
        if (tpl.getStatus() == null) {
            tpl.setStatus(DocTplRules.STATUS_ACTIVE);
        }
        tpl.setDelFlag(0);
        Date now = new Date();
        if (tpl.getId() == null) {
            tpl.setCreateBy(operator.trim());
            tpl.setCreateTime(now);
            this.nslpDocTplMapper.insert(tpl);
        } else {
            tpl.setUpdateBy(operator.trim());
            tpl.setUpdateTime(now);
            this.nslpDocTplMapper.updateById(tpl);
        }
        return tpl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int withdrawVersion(Long id, String operator) {
        TNslpDocTpl tpl = this.nslpDocTplMapper.selectById(id);
        if (tpl == null || (tpl.getDelFlag() != null && tpl.getDelFlag() == 1)) {
            throw new IllegalArgumentException("文书版本里没有这一版：" + id);
        }
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("撤版没留下落笔人，按没办完处理");
        }
        tpl.setStatus(DocTplRules.STATUS_WITHDRAWN);
        tpl.setUpdateBy(operator.trim());
        tpl.setUpdateTime(new Date());
        // 只改版本情形；先前出文照当版钉在出文留档里，一笔都不回改
        return this.nslpDocTplMapper.updateById(tpl);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocIssueResult issue(DocIssueSubmit submit) {
        if (submit == null) {
            throw new IllegalArgumentException("出文报文没递上来，一纸也出不了");
        }
        Date at = submit.getIssueTime() == null ? new Date() : submit.getIssueTime();
        String name = submit.getTplName() == null ? null : submit.getTplName().trim();
        if (!DocTplRules.isKnownName(name)) {
            return DocIssueResult.blocked("文种只认三个：" + DocTplRules.NAME_OVER_STANDARD + "·"
                    + DocTplRules.NAME_ORDER_CORRECT + "·" + DocTplRules.NAME_REVIEW_CONCLUSION, at);
        }
        if (submit.getPointId() == null) {
            return DocIssueResult.blocked("出文没指到点位，取值取不到", at);
        }
        if (submit.getOperator() == null || submit.getOperator().trim().isEmpty()) {
            return DocIssueResult.blocked("出文没留下落笔人，按没办完处理", at);
        }
        TNslpPoint point = this.nslpPointMapper.selectById(submit.getPointId());
        if (point == null || (point.getDelFlag() != null && point.getDelFlag() == 1)) {
            return DocIssueResult.blocked("点位底册里没有这一行：" + submit.getPointId(), at);
        }

        // 选版唯一口：按出文那一刻在位且干净、版次最大者开口
        TNslpDocTpl tpl = resolveVersion(name, at);
        if (tpl == null) {
            return DocIssueResult.blocked("出文那一刻「" + name + "」没有在位的版，这一纸出不来", at);
        }

        // 应填项取值只由服务端一处取：前两项从点位行带（题名=content，限值线=归层钉住的线代号）
        String title = point.getContent();
        String limitCode = point.getPinnedRuleCode();
        List<String> missing = DocTplRules.missingItems(
                tpl.getVarNum(), title, limitCode, submit.getExtraValues());
        int varNum = tpl.getVarNum().intValue();
        int fillNum = varNum - missing.size();
        if (!missing.isEmpty()) {
            // 缺一项一项点名；这一纸张不发，更不许打印环节拿空白纸当文书
            return DocIssueResult.missing(tpl, at, fillNum, missing);
        }

        // 齐了才落出文留档：当版主键/版次/三个数钉住，日后新立版不回改这一条
        TNslpDocIssue rec = new TNslpDocIssue();
        rec.setTplName(name);
        rec.setTplId(tpl.getId());
        rec.setVerNo(tpl.getVerNo());
        rec.setPointId(point.getId());
        rec.setPointNo(point.getPointNo());
        rec.setVarNum(varNum);
        rec.setFillNum(fillNum);
        rec.setMissNum(DocTplRules.miss(varNum, fillNum));
        rec.setIssueTime(at);
        rec.setStatus(0);
        rec.setDelFlag(0);
        rec.setCreateBy(submit.getOperator().trim());
        rec.setCreateTime(new Date());
        this.nslpDocIssueMapper.insert(rec);
        return DocIssueResult.issued(tpl, rec, at);
    }

    private List<TNslpDocTpl> snapshotByName(String tplName) {
        return this.nslpDocTplMapper.selectList(new QueryWrapper<TNslpDocTpl>()
                .eq("del_flag", 0).eq("tpl_name", tplName));
    }
}
