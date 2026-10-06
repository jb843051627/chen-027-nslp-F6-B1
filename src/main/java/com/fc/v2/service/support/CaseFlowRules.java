package com.fc.v2.service.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TNslpPoint;

/**
 * 噪声投诉与超标处置办件流转纯口径（不碰库、不沾 Spring）。
 *
 * 四档关次钉死在这一处：受件、核查、责令、复查收尾，先后不许添、不许改名、
 * 不许先定死档数再回头找路径来配。一条办件此刻停在哪一档、下一档准入满不满、
 * 封住的行还接不接按语、同一档第二句怎么放——都只问这一份口径，旁处不下结论。
 *
 * @author fuce
 */
public final class CaseFlowRules {

    /** 关次一：受件（从受件起） */
    public static final int STAGE_RECEIVED = 0;
    /** 关次二：核查 */
    public static final int STAGE_CHECK = 1;
    /** 关次三：责令 */
    public static final int STAGE_ORDER = 2;
    /** 关次四：复查收尾（到复查收尾止） */
    public static final int STAGE_REVIEW_CLOSE = 3;
    /** 关只有四档，先后钉死 */
    public static final int STAGE_COUNT = 4;

    /** 单子情形：未起（正式立起来之前那一小会儿，主档里不落行） */
    public static final int STATUS_DRAFT = 0;
    /** 单子情形：在办（没封住的件，一处点位只许有一条） */
    public static final int STATUS_ACTIVE = 1;
    /** 单子情形：已收口（走满末关封住，或撤回／并走视同收口） */
    public static final int STATUS_TERMINAL = 2;

    /** 责令关核收记成这一个字，复查收尾才开得了工 */
    public static final String ORDER_ACCEPT_OK = "可";

    /** 收口情由：走满末关、两样齐了正常封住（close_kind 留空） */
    public static final Integer CLOSE_KIND_SEALED = null;
    /** 收口情由：递件人在受件之后自己把反映撤了回去 */
    public static final int CLOSE_KIND_WITHDRAW = 1;
    /** 收口情由：这一处所属的区册从区册底里并走了 */
    public static final int CLOSE_KIND_MERGED = 2;

    /** 留痕动作：受件立条 */
    public static final String ACT_RECEIVE = "受件立条";
    /** 留痕动作：挪前一关 */
    public static final String ACT_FORWARD = "挪前";
    /** 留痕动作：收回一关 */
    public static final String ACT_BACK = "收回";
    /** 留痕动作：本关落一句按语 */
    public static final String ACT_NOTE = "按语";
    /** 留痕动作：走满末关封住 */
    public static final String ACT_SEAL = "封住";
    /** 留痕动作：反映人撤回（视同收口） */
    public static final String ACT_WITHDRAW = "反映人撤回";
    /** 留痕动作：区册并走（视同收口） */
    public static final String ACT_MERGE = "区册并走";
    /** 留痕动作：末关作废 */
    public static final String ACT_VOID = "作废";

    /** 四档的名字，档数与名字都只此一处出处 */
    private static final String[] STAGE_NAMES = {"受件", "核查", "责令", "复查收尾"};

    private CaseFlowRules() {
    }

    /** 档名回话：0..3 出本名，其余只有「档数不明」四个字，不许替它预设 */
    public static String stageName(Integer stage) {
        if (stage != null && stage.intValue() >= 0 && stage.intValue() < STAGE_COUNT) {
            return STAGE_NAMES[stage.intValue()];
        }
        return "档数不明";
    }

    // ------------------------------------------------------------------
    // 立：四样输入，一样一样点名；光甩一句「信息不全」不认
    // ------------------------------------------------------------------

    /**
     * 立件四样按固定次序点：点位码、所属区册代号、处数、准备折算的那一个核查读数。
     * 一样不缺回空单；缺了哪一样、哪一样不成样，回单上一句一句点出名来。
     *
     * @param pointNo      点位码
     * @param zoneNo       所属区册代号
     * @param occNum       处数
     * @param checkReading 准备折算的核查读数（要回显成数的那一格）
     */
    public static List<String> intakeMissing(String pointNo, String zoneNo,
                                             Integer occNum, String checkReading) {
        List<String> missing = new ArrayList<String>();
        if (blank(pointNo)) {
            missing.add("立件缺了这一样：点位码");
        }
        if (blank(zoneNo)) {
            missing.add("立件缺了这一样：所属区册代号");
        }
        if (occNum == null) {
            missing.add("立件缺了这一样：处数");
        } else if (occNum.intValue() < 1) {
            missing.add("处数得记成至少一处");
        }
        if (blank(checkReading)) {
            missing.add("立件缺了这一样：核查读数");
        } else if (!isNumber(checkReading)) {
            missing.add("核查读数得是个数，回显出来的得是数");
        }
        return missing;
    }

    /** 核查读数折算前先过这一道：读不进数的回 null，不替它折算 */
    public static BigDecimal toReading(String checkReading) {
        if (blank(checkReading) || !isNumber(checkReading)) {
            return null;
        }
        return new BigDecimal(checkReading.trim());
    }

    /** 进核查的门槛：这一处点位在底册上还挂着名没有，撤了名的进不去 */
    public static boolean pointOnRegister(TNslpPoint point) {
        return point != null
                && (point.getDelFlag() == null || point.getDelFlag().intValue() == 0);
    }

    // ------------------------------------------------------------------
    // 挪：紧邻下一关才生效；门槛摆在各关前头
    // ------------------------------------------------------------------

    /** 往前只许跨紧邻的下一关；越过中间某关直接送再下一关，不生效 */
    public static boolean adjacentForward(int from, int to) {
        return to == from + 1 && from >= STAGE_RECEIVED && from < STAGE_REVIEW_CLOSE;
    }

    /** 往后只收回一关；已经站在第一关的，没什么可再收 */
    public static boolean canRollback(int stage) {
        return stage > STAGE_RECEIVED && stage <= STAGE_REVIEW_CLOSE;
    }

    /**
     * 下一关准入（唯一入口）。满足回 null；不满足回那句点名的门槛话。
     * 进核查看底册挂名；进复查收尾看责令关核收记没记成「可」；
     * 受件→核查、核查→责令之间不再另设门槛。
     *
     * @param from             当前档（0..2）
     * @param pointOnRegister  这一处点位底册上还挂不挂名（进核查才看）
     * @param orderAccept      责令关核收原话（进复查收尾才看）
     */
    public static String guardForward(int from, boolean pointOnRegister, String orderAccept) {
        if (from == STAGE_RECEIVED && !pointOnRegister) {
            return "进不了核查：这一处点位在底册上已经撤名，撤了名的进不去";
        }
        if (from == STAGE_ORDER && !ORDER_ACCEPT_OK.equals(orderAccept)) {
            return "复查收尾开不了工：责令那一关的核收没记成「可」";
        }
        return null;
    }

    /**
     * 复查收尾封关两件：两名见证岗各落一笔（两岗、不得同一个抵两笔）、
     * 复测凭证那一串写回本行。两样毛病一次说全，用「；」连上；齐了回空单。
     */
    public static List<String> sealMissing(String witnessA, String witnessB, String proofNo) {
        List<String> missing = new ArrayList<String>();
        if (blank(witnessA)) {
            missing.add("封不上：第一名见证岗还没落笔");
        }
        if (blank(witnessB)) {
            missing.add("封不上：第二名见证岗还没落笔");
        }
        if (!blank(witnessA) && !blank(witnessB) && witnessA.trim().equals(witnessB.trim())) {
            missing.add("封不上：两名见证岗得两岗各落一笔，记成同一个不算两笔");
        }
        if (blank(proofNo)) {
            missing.add("封不上：复测凭证那一串还没写回本行");
        }
        return missing;
    }

    // ------------------------------------------------------------------
    // 按语：同一关第二次塞话，本行留头一句，第二句盖不上、不另存
    // ------------------------------------------------------------------

    /** 各关占一位：bit0..bit3 对应当前这一趟到该关时头一句落没落 */
    public static int noteBit(int stage) {
        return 1 << stage;
    }

    /** 这一关这一趟头一句已经落过没有 */
    public static boolean hasNote(int noteMask, int stage) {
        return (noteMask & noteBit(stage)) != 0;
    }

    /** 头一句落下：本关占一位 */
    public static int withNote(int noteMask, int stage) {
        return noteMask | noteBit(stage);
    }

    /**
     * 收回这一关时把它的位腾掉：先前那句已照原先后留在追溯记录里，
     * 重新挪到这一关时从头累计，新的头一句才落得进。
     */
    public static int withoutNote(int noteMask, int stage) {
        return noteMask & ~noteBit(stage);
    }

    // ------------------------------------------------------------------
    // 封：收口之后只能看不能动
    // ------------------------------------------------------------------

    /** 走满末关封住，或撤回／并走视同收口，或末关作废：都只许看不许动 */
    public static boolean isFrozen(Integer status, Integer voidFlag) {
        return (status != null && status.intValue() == STATUS_TERMINAL)
                || (voidFlag != null && voidFlag.intValue() == 1);
    }

    /** 走满末关封住（不含撤回、并走、作废） */
    public static boolean isSealed(Integer status, Integer closeKind, Integer voidFlag) {
        return status != null && status.intValue() == STATUS_TERMINAL
                && closeKind == null
                && (voidFlag == null || voidFlag.intValue() == 0);
    }

    // ------------------------------------------------------------------
    // 捋：照追溯记录从头放一遍，档数只许由这一趟放出来
    // ------------------------------------------------------------------

    /**
     * 把留痕按时序一笔一笔放回去：受件立条起在第 0 档，挪前只认紧邻的一下，
     * 收回只回一档，封住／撤回／并走到此收口，作废另记。
     * 不采信邻档旧结论，也不拿另一条办件的档数来套——档数全由递进来的
     * 这一串动作放出来。
     */
    public static ReplayResult replay(List<Event> events) {
        int stage = STAGE_RECEIVED;
        boolean terminal = false;
        Integer closeKind = null;
        boolean voided = false;
        int forwardCount = 0;
        int backCount = 0;
        if (events != null) {
            for (Event e : events) {
                if (e == null || e.action == null) {
                    continue;
                }
                String a = e.action;
                if (ACT_RECEIVE.equals(a)) {
                    stage = STAGE_RECEIVED;
                } else if (ACT_FORWARD.equals(a)) {
                    if (!terminal && adjacentForward(stage, stage + 1)) {
                        stage++;
                        forwardCount++;
                    }
                } else if (ACT_BACK.equals(a)) {
                    if (!terminal && canRollback(stage)) {
                        stage--;
                        backCount++;
                    }
                } else if (ACT_SEAL.equals(a)) {
                    terminal = true;
                } else if (ACT_WITHDRAW.equals(a)) {
                    terminal = true;
                    closeKind = Integer.valueOf(CLOSE_KIND_WITHDRAW);
                } else if (ACT_MERGE.equals(a)) {
                    terminal = true;
                    closeKind = Integer.valueOf(CLOSE_KIND_MERGED);
                } else if (ACT_VOID.equals(a)) {
                    voided = true;
                }
                // ACT_NOTE 不动档
            }
        }
        return new ReplayResult(stage, terminal, closeKind, voided, forwardCount, backCount);
    }

    /** 留痕放回去的结果：档数、收没收口、收口情由，以及放出来的挪前／收回笔数 */
    public static final class ReplayResult {
        private final int stage;
        private final boolean terminal;
        private final Integer closeKind;
        private final boolean voided;
        private final int forwardCount;
        private final int backCount;

        public ReplayResult(int stage, boolean terminal, Integer closeKind, boolean voided,
                            int forwardCount, int backCount) {
            this.stage = stage;
            this.terminal = terminal;
            this.closeKind = closeKind;
            this.voided = voided;
            this.forwardCount = forwardCount;
            this.backCount = backCount;
        }

        public int getStage() {
            return stage;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public Integer getCloseKind() {
            return closeKind;
        }

        public boolean isVoided() {
            return voided;
        }

        public int getForwardCount() {
            return forwardCount;
        }

        public int getBackCount() {
            return backCount;
        }
    }

    /** 留痕里的一笔（动作＋落笔时所处的档）；捋的时候只吃这一串 */
    public static final class Event {
        private final String action;
        private final int stage;

        public Event(String action, int stage) {
            this.action = action;
            this.stage = stage;
        }

        public String getAction() {
            return action;
        }

        public int getStage() {
            return stage;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isNumber(String s) {
        try {
            new BigDecimal(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
