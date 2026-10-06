package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpCaseTrail;
import com.fc.v2.service.support.CaseFlowRules;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 追溯回话：一条办件身后按时序压着的那一串。
 *
 * 每一关那句按语，连着它提交的那一刻，按先后排好；同刻两笔各记各的，
 * 不许并成一条，也不许吞掉一条。档数不听行面上的存量，由这一串当场放一遍。
 *
 * @author fuce
 */
@ApiModel(value = "CaseTrace", description = "办件追溯（按语连同落笔时刻按时序）")
public class CaseTrace implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 办件主键 */
    @ApiModelProperty(value = "办件主键")
    private Long caseId;

    /** 办件件次 */
    @ApiModelProperty(value = "办件件次")
    private String bizNo;

    /** 从这一串放出来的当前关次（不另算一套去凑行面） */
    @ApiModelProperty(value = "从留痕放出来的当前关次")
    private int replayStage;

    /** 关名回话 */
    @ApiModelProperty(value = "关名回话")
    private String replayStageText;

    /** 从这一串放出来是否已收口 */
    @ApiModelProperty(value = "从留痕放出来是否已收口")
    private boolean replayTerminal;

    /** 按时序排好的一笔一笔 */
    @ApiModelProperty(value = "按时序的留痕明细")
    private List<Entry> entries = new ArrayList<Entry>();

    /**
     * 唯一装配口：追溯与受理簿捋档都走这里。行面那一个 stage 不喂进来——
     * 当前档由留痕这一串当场放一遍，档数与时序只认这一趟点出来的结果。
     */
    public static CaseTrace of(Long caseId, String bizNo, List<TNslpCaseTrail> trails) {
        CaseTrace t = new CaseTrace();
        t.caseId = caseId;
        t.bizNo = bizNo;
        List<CaseFlowRules.Event> events = new ArrayList<CaseFlowRules.Event>();
        if (trails != null) {
            for (TNslpCaseTrail tr : trails) {
                Entry e = new Entry();
                int st = tr.getStage() == null ? 0 : tr.getStage().intValue();
                e.stage = st;
                e.stageText = CaseFlowRules.stageName(Integer.valueOf(st));
                e.action = tr.getAction();
                e.note = tr.getNote();
                e.trailTime = tr.getTrailTime();
                e.seq = tr.getSeq() == null ? 0 : tr.getSeq().intValue();
                t.entries.add(e);
                events.add(new CaseFlowRules.Event(tr.getAction(), st));
            }
        }
        CaseFlowRules.ReplayResult replay = CaseFlowRules.replay(events);
        t.replayStage = replay.getStage();
        t.replayStageText = CaseFlowRules.stageName(Integer.valueOf(t.replayStage));
        t.replayTerminal = replay.isTerminal();
        return t;
    }

    /** 留痕里的一笔：当时在第几关、什么动作、原话、那一刻 */
    @ApiModel(value = "CaseTraceEntry", description = "留痕一笔")
    public static class Entry implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        private int stage;
        private String stageText;
        private String action;
        private String note;
        private Date trailTime;
        private int seq;

        public int getStage() {
            return stage;
        }

        public String getStageText() {
            return stageText;
        }

        public String getAction() {
            return action;
        }

        public String getNote() {
            return note;
        }

        public Date getTrailTime() {
            return trailTime;
        }

        public int getSeq() {
            return seq;
        }
    }

    public Long getCaseId() {
        return caseId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public int getReplayStage() {
        return replayStage;
    }

    public String getReplayStageText() {
        return replayStageText;
    }

    public boolean isReplayTerminal() {
        return replayTerminal;
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
