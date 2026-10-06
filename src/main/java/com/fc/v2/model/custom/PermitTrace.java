package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpPermitSign;
import com.fc.v2.service.support.PermitBillRules;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 签批追溯回话：一张单身前按时序压着的逐笔留痕。
 *
 * 每一笔连着它落单那一刻与服务端认下的关、轮，按先后排好；同刻两笔各记各的，
 * 不许并成一条。当前关、情形、各关已到几人不听行面缓存，由这一串当场放一遍。
 *
 * @author fuce
 */
@ApiModel(value = "PermitTrace", description = "签批追溯（逐笔连同落单时刻按时序）")
public class PermitTrace implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 签批单主键 */
    @ApiModelProperty(value = "签批单主键")
    private Long billId;

    /** 签批单单号 */
    @ApiModelProperty(value = "签批单单号")
    private String billNo;

    /** 点位码 */
    @ApiModelProperty(value = "点位码")
    private String pointNo;

    /** 从留痕放出来的当前关（不另算一套去凑行面） */
    @ApiModelProperty(value = "从留痕放出来的当前关")
    private int replayNode;

    /** 关名回话 */
    @ApiModelProperty(value = "关名回话")
    private String replayNodeText;

    /** 从留痕放出来的情形 0在签 1已点齐 2止住 */
    @ApiModelProperty(value = "从留痕放出来的情形")
    private int replayStatus;

    /** 当下关本轮轮次 */
    @ApiModelProperty(value = "当下关本轮轮次")
    private int replayRound;

    /** 各关本轮已到几人（逐笔累上来的那一遍，屏上只回显） */
    @ApiModelProperty(value = "各关本轮已到几人")
    private List<NodeCount> nodes = new ArrayList<NodeCount>();

    /** 按时序排好的一笔一笔 */
    @ApiModelProperty(value = "按时序的留痕明细")
    private List<Entry> entries = new ArrayList<Entry>();

    /**
     * 唯一装配口。行面那一个 node_no/sign_count 不喂进来——当前关、情形、
     * 各关已到几人由留痕这一串当场放一遍。
     */
    public static PermitTrace of(Long billId, String billNo, String pointNo,
                                 List<TNslpPermitSign> signs) {
        PermitTrace t = new PermitTrace();
        t.billId = billId;
        t.billNo = billNo;
        t.pointNo = pointNo;
        List<PermitBillRules.Event> events = new ArrayList<PermitBillRules.Event>();
        if (signs != null) {
            for (TNslpPermitSign s : signs) {
                Entry e = new Entry();
                int n = s.getNodeNo() == null ? 0 : s.getNodeNo().intValue();
                e.action = s.getAction();
                e.node = n;
                e.nodeText = PermitBillRules.nodeName(Integer.valueOf(n));
                e.roundNo = s.getRoundNo() == null ? 0 : s.getRoundNo().intValue();
                e.signBy = s.getSignBy();
                e.opinion = s.getOpinion();
                e.agree = s.getAgreeFlag() == null || s.getAgreeFlag().intValue() == 1;
                e.voided = s.getVoidFlag() != null && s.getVoidFlag().intValue() == 1;
                e.signTime = s.getSignTime();
                e.seq = s.getSeq() == null ? 0 : s.getSeq().intValue();
                t.entries.add(e);
                events.add(new PermitBillRules.Event(s.getAction(), s.getNodeNo(),
                        e.roundNo, e.voided));
            }
        }
        PermitBillRules.ReplayResult replay = PermitBillRules.replay(events);
        int node = replay.getNode() == null ? PermitBillRules.NODE_INTAKE : replay.getNode().intValue();
        t.replayNode = node;
        t.replayNodeText = PermitBillRules.nodeName(Integer.valueOf(node));
        t.replayStatus = replay.getStatus();
        t.replayRound = replay.getCurrentRound(node);
        int[] lives = replay.getLiveCounts();
        int[] rounds = replay.getRounds();
        for (int n = 0; n < PermitBillRules.NODE_COUNT; n++) {
            t.nodes.add(new NodeCount(n, PermitBillRules.nodeName(Integer.valueOf(n)),
                    PermitBillRules.needCount(n), lives[n],
                    rounds[n] == 0 ? 1 : rounds[n]));
        }
        return t;
    }

    /** 一段的账：关号、关名、应签几人、本轮已到几人、当前轮次 */
    @ApiModel(value = "PermitNodeCount", description = "一段的应签/已到账")
    public static class NodeCount implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        private int node;
        private String nodeText;
        private int needCount;
        private int signCount;
        private int roundNo;

        public NodeCount(int node, String nodeText, int needCount, int signCount, int roundNo) {
            this.node = node;
            this.nodeText = nodeText;
            this.needCount = needCount;
            this.signCount = signCount;
            this.roundNo = roundNo;
        }

        public int getNode() {
            return node;
        }

        public String getNodeText() {
            return nodeText;
        }

        public int getNeedCount() {
            return needCount;
        }

        public int getSignCount() {
            return signCount;
        }

        public int getRoundNo() {
            return roundNo;
        }
    }

    /** 留痕里的一笔 */
    @ApiModel(value = "PermitTraceEntry", description = "签批留痕一笔")
    public static class Entry implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        private String action;
        private int node;
        private String nodeText;
        private int roundNo;
        private String signBy;
        private String opinion;
        private boolean agree;
        private boolean voided;
        private Date signTime;
        private int seq;

        public String getAction() {
            return action;
        }

        public int getNode() {
            return node;
        }

        public String getNodeText() {
            return nodeText;
        }

        public int getRoundNo() {
            return roundNo;
        }

        public String getSignBy() {
            return signBy;
        }

        public String getOpinion() {
            return opinion;
        }

        public boolean isAgree() {
            return agree;
        }

        public boolean isVoided() {
            return voided;
        }

        public Date getSignTime() {
            return signTime;
        }

        public int getSeq() {
            return seq;
        }
    }

    public Long getBillId() {
        return billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public String getPointNo() {
        return pointNo;
    }

    public int getReplayNode() {
        return replayNode;
    }

    public String getReplayNodeText() {
        return replayNodeText;
    }

    public int getReplayStatus() {
        return replayStatus;
    }

    public int getReplayRound() {
        return replayRound;
    }

    public List<NodeCount> getNodes() {
        return nodes;
    }

    public List<Entry> getEntries() {
        return entries;
    }
}
