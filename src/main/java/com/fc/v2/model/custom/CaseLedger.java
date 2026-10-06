package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpCaseTrail;
import com.fc.v2.service.support.CaseFlowRules;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 受理簿回话：把一条件从末关一关一关收回、再从头捋一遍，捋出来的笔数
 * 跟当天受理簿上一笔一笔合得上；只截其中任意一关单独核，也得自洽。
 *
 * 受理簿记的是「笔」：挪前、收回各一笔，同刻两条通道各送一笔也各记各的。
 * 数全部回到留痕里数，不许另起一套算法。
 *
 * @author fuce
 */
@ApiModel(value = "CaseLedger", description = "受理簿对账（捋出来的笔数对得上受理簿）")
public class CaseLedger implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 办件主键 */
    @ApiModelProperty(value = "办件主键")
    private Long caseId;

    /** 对账那一一日的线（只数这一天落笔的）；为空数全串 */
    @ApiModelProperty(value = "对账那一一日")
    private Date day;

    /** 留痕总笔数 */
    @ApiModelProperty(value = "留痕总笔数")
    private int totalCount;

    /** 挪前笔数 */
    @ApiModelProperty(value = "挪前笔数")
    private int forwardCount;

    /** 收回笔数 */
    @ApiModelProperty(value = "收回笔数")
    private int backCount;

    /** 从头捋到的关次（受件为 0，每笔挪前进一、每笔收回退一） */
    @ApiModelProperty(value = "从头捋到的关次")
    private int replayStage;

    /** 关名回话 */
    @ApiModelProperty(value = "关名回话")
    private String replayStageText;

    /** 是否收口 */
    @ApiModelProperty(value = "是否收口")
    private boolean replayTerminal;

    /** 逐关自洽：每一关自己的挪前进来笔数＝收回离开笔数（末关除外，封住落在末关） */
    @ApiModelProperty(value = "逐关自洽与否")
    private boolean stageBalanced;

    /** 对不上的关（哪一关进出轧不平，点名） */
    @ApiModelProperty(value = "对不上的关")
    private List<Integer> unbalancedStages = new ArrayList<Integer>();

    /**
     * 唯一装配口：笔数全部回到留痕里数，挪前／收回一笔一笔加，
     * 不许拿前端缓存里的数，也不许另起一套算法。
     *
     * day 不空时，三笔数只数那一一日落笔的（对当天受理簿）；档数与逐关轧平
     * 仍由整串留痕放出来——档数不跟着单日截出来的那一段另算一套去凑。
     */
    public static CaseLedger of(Long caseId, Date day, List<TNslpCaseTrail> allTrails) {
        CaseLedger l = new CaseLedger();
        l.caseId = caseId;
        l.day = day;

        List<CaseFlowRules.Event> allEvents = new ArrayList<CaseFlowRules.Event>();
        int[] enters = new int[CaseFlowRules.STAGE_COUNT];
        int[] exits = new int[CaseFlowRules.STAGE_COUNT];
        if (allTrails != null) {
            for (TNslpCaseTrail tr : allTrails) {
                int s = tr.getStage() == null ? 0 : tr.getStage().intValue();
                String a = tr.getAction();
                allEvents.add(new CaseFlowRules.Event(a, s));
                if (CaseFlowRules.ACT_RECEIVE.equals(a)) {
                    // 受件立条落在第 0 关：这一关压着一笔
                    if (inBounds(s)) {
                        enters[s]++;
                    }
                } else if (CaseFlowRules.ACT_FORWARD.equals(a)) {
                    // 落笔那一刻办件已经在新关：挪前记的是目的关
                    if (inBounds(s)) {
                        enters[s]++;
                    }
                    if (s - 1 >= 0) {
                        exits[s - 1]++;
                    }
                } else if (CaseFlowRules.ACT_BACK.equals(a)) {
                    // 收回同样记目的关：从后一关离开、落回前一关
                    if (inBounds(s)) {
                        enters[s]++;
                    }
                    if (s + 1 < CaseFlowRules.STAGE_COUNT) {
                        exits[s + 1]++;
                    }
                }
            }
        }

        // 对当天受理簿：笔数只数那一一日落笔的；day 空着就数全串
        List<CaseFlowRules.Event> dayEvents = new ArrayList<CaseFlowRules.Event>();
        if (day == null) {
            dayEvents.addAll(allEvents);
        } else {
            if (allTrails != null) {
                for (TNslpCaseTrail tr : allTrails) {
                    if (sameDay(tr.getTrailTime(), day)) {
                        dayEvents.add(new CaseFlowRules.Event(
                                tr.getAction(), tr.getStage() == null ? 0 : tr.getStage().intValue()));
                    }
                }
            }
        }

        CaseFlowRules.ReplayResult dayReplay = CaseFlowRules.replay(dayEvents);
        CaseFlowRules.ReplayResult fullReplay = CaseFlowRules.replay(allEvents);
        l.totalCount = dayEvents.size();
        l.forwardCount = dayReplay.getForwardCount();
        l.backCount = dayReplay.getBackCount();
        // 档数始终由整串放出来，不跟着单日截出的那一段另算一套去凑
        l.replayStage = fullReplay.getStage();
        l.replayStageText = CaseFlowRules.stageName(Integer.valueOf(l.replayStage));
        l.replayTerminal = fullReplay.isTerminal();

        // 逐关自洽：每一关「进来的笔＝出去的笔」，唯独自捋到的那一关多压着一笔（办件正停着）。
        // 从复查收尾收回再从头捋，回回进出都轧得平；哪一关轧不平，点名。
        boolean balanced = true;
        for (int s = 0; s < CaseFlowRules.STAGE_COUNT; s++) {
            int expect = s == l.replayStage ? 1 : 0;
            if (enters[s] - exits[s] != expect) {
                balanced = false;
                l.unbalancedStages.add(Integer.valueOf(s));
            }
        }
        l.stageBalanced = balanced;
        return l;
    }

    private static boolean inBounds(int stage) {
        return stage >= 0 && stage < CaseFlowRules.STAGE_COUNT;
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

    public Long getCaseId() {
        return caseId;
    }

    public Date getDay() {
        return day;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getForwardCount() {
        return forwardCount;
    }

    public int getBackCount() {
        return backCount;
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

    public boolean isStageBalanced() {
        return stageBalanced;
    }

    public List<Integer> getUnbalancedStages() {
        return unbalancedStages;
    }
}
