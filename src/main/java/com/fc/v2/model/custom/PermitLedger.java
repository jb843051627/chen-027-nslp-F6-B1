package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpPermitSign;
import com.fc.v2.service.support.PermitBillRules;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 签批受理簿对账回话。
 *
 * 把一张单从存备席倒着数回收件席，逐段捋出来的笔数跟当天受理簿上一笔一笔合得上：
 * 落笔就是受理簿上的一笔，同刻两人各送一笔也各记各的；压回抹去的笔行还在，
 * 仍算受理簿上的一笔，只是不进本关本轮已到人数。单抽其中任意一段出来核，
 * 进出也要轧得平。笔数全部回到留痕里数，不许另起一套算法。
 *
 * @author fuce
 */
@ApiModel(value = "PermitLedger", description = "签批受理簿对账（笔数对得上受理簿，逐段自洽）")
public class PermitLedger implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 签批单主键 */
    @ApiModelProperty(value = "签批单主键")
    private Long billId;

    /** 对账那一一日（只数这一天落笔的）；空着数全串 */
    @ApiModelProperty(value = "对账那一一日")
    private Date day;

    /** 受理簿落笔总笔数（抹去的旧笔也在簿上，仍算一笔） */
    @ApiModelProperty(value = "受理簿落笔总笔数")
    private int totalCount;

    /** 其中活着（未被压回抹去）的笔数 */
    @ApiModelProperty(value = "活着的落笔笔数")
    private int liveCount;

    /** 其中被压回抹去、留底的笔数 */
    @ApiModelProperty(value = "抹去留底的笔数")
    private int voidedCount;

    /** 逐段落笔笔数（受理簿按关分） */
    @ApiModelProperty(value = "逐段落笔笔数")
    private List<NodeCount> nodes = new ArrayList<NodeCount>();

    /** 从头捋到的关 */
    @ApiModelProperty(value = "从头捋到的关")
    private int replayNode;

    /** 关名回话 */
    @ApiModelProperty(value = "关名回话")
    private String replayNodeText;

    /** 捋出来的情形 */
    @ApiModelProperty(value = "捋出来的情形")
    private int replayStatus;

    /** 逐段自洽与否 */
    @ApiModelProperty(value = "逐段自洽与否")
    private boolean nodeBalanced;

    /** 对不上的关 */
    @ApiModelProperty(value = "对不上的关")
    private List<Integer> unbalancedNodes = new ArrayList<Integer>();

    /** 一段的对账数 */
    @ApiModel(value = "PermitLedgerNodeCount", description = "一段的落笔/活笔账")
    public static class NodeCount implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        private int node;
        private String nodeText;
        private int signCount;
        private int liveCount;
        private int voidedCount;

        public NodeCount(int node, String nodeText) {
            this.node = node;
            this.nodeText = nodeText;
        }

        public int getNode() {
            return node;
        }

        public String getNodeText() {
            return nodeText;
        }

        public int getSignCount() {
            return signCount;
        }

        public int getLiveCount() {
            return liveCount;
        }

        public int getVoidedCount() {
            return voidedCount;
        }
    }

    /**
     * 唯一装配口：落笔笔数全部回到留痕里一笔一笔数；逐段轧平由整串放出来，
     * 不跟着单日截出的那一段另算一套去凑。
     */
    public static PermitLedger of(Long billId, Date day, List<TNslpPermitSign> allSigns) {
        PermitLedger l = new PermitLedger();
        l.billId = billId;
        l.day = day;

        List<PermitBillRules.Event> allEvents = new ArrayList<PermitBillRules.Event>();
        int[] enters = new int[PermitBillRules.NODE_COUNT];
        int[] exits = new int[PermitBillRules.NODE_COUNT];
        // 受理簿逐段笔数（全串）与当日截串
        int[] signAll = new int[PermitBillRules.NODE_COUNT];
        int[] liveAll = new int[PermitBillRules.NODE_COUNT];
        int[] voidAll = new int[PermitBillRules.NODE_COUNT];
        int[] signDay = new int[PermitBillRules.NODE_COUNT];
        int[] liveDay = new int[PermitBillRules.NODE_COUNT];
        int[] voidDay = new int[PermitBillRules.NODE_COUNT];

        if (allSigns != null) {
            for (TNslpPermitSign s : allSigns) {
                int n = s.getNodeNo() == null ? 0 : s.getNodeNo().intValue();
                boolean voided = s.getVoidFlag() != null && s.getVoidFlag().intValue() == 1;
                int round = s.getRoundNo() == null ? 0 : s.getRoundNo().intValue();
                allEvents.add(new PermitBillRules.Event(s.getAction(), s.getNodeNo(), round, voided));

                String a = s.getAction();
                if (PermitBillRules.ACT_OPEN.equals(a)) {
                    if (n >= 0 && n < PermitBillRules.NODE_COUNT) {
                        enters[n]++;
                    }
                } else if (PermitBillRules.ACT_ADVANCE.equals(a)) {
                    if (n >= 0 && n < PermitBillRules.NODE_COUNT) {
                        enters[n]++;
                    }
                    if (n - 1 >= 0) {
                        exits[n - 1]++;
                    }
                } else if (PermitBillRules.ACT_ROLLBACK.equals(a)) {
                    if (n >= 0 && n < PermitBillRules.NODE_COUNT) {
                        enters[n]++;
                    }
                    if (n + 1 < PermitBillRules.NODE_COUNT) {
                        exits[n + 1]++;
                    }
                } else if (PermitBillRules.ACT_SIGN.equals(a)
                        && n >= 0 && n < PermitBillRules.NODE_COUNT) {
                    signAll[n]++;
                    if (voided) {
                        voidAll[n]++;
                    } else {
                        liveAll[n]++;
                    }
                    if (day == null || sameDay(s.getSignTime(), day)) {
                        signDay[n]++;
                        if (voided) {
                            voidDay[n]++;
                        } else {
                            liveDay[n]++;
                        }
                    }
                }
            }
        }

        PermitBillRules.ReplayResult replay = PermitBillRules.replay(allEvents);
        int node = replay.getNode() == null ? PermitBillRules.NODE_INTAKE : replay.getNode().intValue();
        l.replayNode = node;
        l.replayNodeText = PermitBillRules.nodeName(Integer.valueOf(node));
        l.replayStatus = replay.getStatus();

        int[] picked = day == null ? signAll : signDay;
        int[] pickedLive = day == null ? liveAll : liveDay;
        int[] pickedVoid = day == null ? voidAll : voidDay;
        for (int n = 0; n < PermitBillRules.NODE_COUNT; n++) {
            NodeCount nc = new NodeCount(n, PermitBillRules.nodeName(Integer.valueOf(n)));
            nc.signCount = picked[n];
            nc.liveCount = pickedLive[n];
            nc.voidedCount = pickedVoid[n];
            l.nodes.add(nc);
            l.totalCount += picked[n];
            l.liveCount += pickedLive[n];
            l.voidedCount += pickedVoid[n];
        }

        // 逐段自洽：每段「进来＝出去」，唯独自捋到的那一段多压着一笔（单正停在那一段）；
        // 点齐封住、止住时单也压在停住的那一段。哪一段轧不平，点名。
        boolean balanced = true;
        for (int n = 0; n < PermitBillRules.NODE_COUNT; n++) {
            int expect = n == node ? 1 : 0;
            if (enters[n] - exits[n] != expect) {
                balanced = false;
                l.unbalancedNodes.add(Integer.valueOf(n));
            }
        }
        l.nodeBalanced = balanced;
        return l;
    }

    private static boolean sameDay(Date a, Date b) {
        if (a == null) {
            return false;
        }
        Calendar ca = Calendar.getInstance();
        ca.setTime(a);
        Calendar cb = Calendar.getInstance();
        cb.setTime(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    public Long getBillId() {
        return billId;
    }

    public Date getDay() {
        return day;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getLiveCount() {
        return liveCount;
    }

    public int getVoidedCount() {
        return voidedCount;
    }

    public List<NodeCount> getNodes() {
        return nodes;
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

    public boolean isNodeBalanced() {
        return nodeBalanced;
    }

    public List<Integer> getUnbalancedNodes() {
        return unbalancedNodes;
    }
}
