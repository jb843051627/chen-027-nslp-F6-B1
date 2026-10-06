package com.fc.v2.service.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 夜间连续施工作业许可签批单纯口径（不碰库、不沾 Spring）。
 *
 * 三关钉死在这一处：区里收件席、区局认定席、市局存备席，先后不许添、不许改名；
 * 各关应签几人、照什么算法算齐、落了「不可」之后整张单还动不动得、压回退到哪一关、
 * 轮次怎么另起——一概只问这一份口径，旁处不下结论。报文里写了第几关、几人已签，
 * 落库不采信；屏上那个已签人数只是回显，源头只认逐笔累加的那一遍。
 *
 * 三关各数各的笔，谁也不套用谁：
 * <ul>
 *   <li>收件席（一名即过）：只等 1 人，这一位落「可」即齐；落「不可」整张止住。</li>
 *   <li>认定席（先落为准）：等 2 人，头一个落下的说法就是本关结论——头一个「不可」
 *       整张止住；头一个「可」，第二个说什么都照常累笔、两方原话都留底，本关按头一句
 *       算齐往前推。拿收件席的数法量本关、拿本关的数法量存备席，都算走样。</li>
 *   <li>存备席（两条一并认可）：等 2 人，两条都「可」才算齐、整张点齐封住；头一个
 *       落「不可」整张当即止住；先到一条「可」、后到一条「不可」，整单停在本关等，
 *       既不往前推、也不就此止住——解开只走压回那一条道。</li>
 * </ul>
 *
 * @author fuce
 */
public final class PermitBillRules {

    /** 关一：区里收件席（1 人，一名即过） */
    public static final int NODE_INTAKE = 0;
    /** 关二：区局认定席（2 人，先落为准） */
    public static final int NODE_DISTRICT = 1;
    /** 关三：市局存备席（2 人，两条一并认可） */
    public static final int NODE_CITY = 2;
    /** 关只有三档，先后钉死 */
    public static final int NODE_COUNT = 3;

    /** 算齐办法：一名即过（收件席） */
    public static final int MODE_SOLE = 0;
    /** 算齐办法：两名须同判、两条一并认可（存备席，旧档原有这一种，模样不动） */
    public static final int MODE_ALL_APPROVE = 1;
    /** 算齐办法：先落为准（认定席）——新增的一种数法另立一号承接，不回头改旧号的模样 */
    public static final int MODE_FIRST_WINS = 2;

    /** 签批情形：在签（压回退关之后仍是在签，不单另立一种情形） */
    public static final int STATUS_RUNNING = 0;
    /** 签批情形：已点齐封住（走满三关、存备席两条一并认可） */
    public static final int STATUS_SEALED = 1;
    /** 签批情形：止住（驱动本关算法的那一句落了「不可」） */
    public static final int STATUS_STOPPED = 2;

    /** 认可原话 */
    public static final String OPINION_APPROVE = "可";
    /** 不认可原话；逐字等于这两个字才按「不可」记账，其余原话一律按认可留底 */
    public static final String OPINION_VETO = "不可";

    /** 点位册面许可档：未据签批单准许夜间连续施工 */
    public static final int POINT_PERMIT_IDLE = 0;
    /** 点位册面许可档：已据点齐的签批单准许 */
    public static final int POINT_PERMIT_GRANTED = 1;

    /** 留痕动作：立单（只从点位底册带出，一处点位在签的只许一张） */
    public static final String ACT_OPEN = "立单";
    /** 留痕动作：落笔（一次落笔只有这一个入口，不另起单、不改旧笔、不追补签署） */
    public static final String ACT_SIGN = "落笔";
    /** 留痕动作：推进（上一关记为齐，整单挪到紧邻的下一关） */
    public static final String ACT_ADVANCE = "推进";
    /** 留痕动作：压回（只退一关，抹的也只是离开那一关本轮的笔） */
    public static final String ACT_ROLLBACK = "压回";
    /** 留痕动作：点齐（走满三关，整张封住，册面那一行同一回改口） */
    public static final String ACT_SEAL = "点齐";
    /** 留痕动作：止住（有一句作数的「不可」落下，此后再递的笔一概不采信） */
    public static final String ACT_STOP = "止住";

    /** 三关的名字，关数与名字都只此一处出处 */
    private static final String[] NODE_NAMES = {"区里收件席", "区局认定席", "市局存备席"};

    private PermitBillRules() {
    }

    /** 关名回话：0..2 出本名，其余只有「关数不明」四个字，不许替它预设 */
    public static String nodeName(Integer node) {
        if (node != null && node.intValue() >= 0 && node.intValue() < NODE_COUNT) {
            return NODE_NAMES[node.intValue()];
        }
        return "关数不明";
    }

    /** 这一关应签几人：1／2／2，钉死，报文说了不算 */
    public static int needCount(int node) {
        switch (node) {
            case NODE_INTAKE:
                return 1;
            case NODE_DISTRICT:
            case NODE_CITY:
                return 2;
            default:
                throw new IllegalArgumentException("没有这一关，应签人数无处可问：" + node);
        }
    }

    /** 这一关照什么算法算齐：一名即过／先落为准／两条一并认可 */
    public static int signMode(int node) {
        switch (node) {
            case NODE_INTAKE:
                return MODE_SOLE;
            case NODE_DISTRICT:
                return MODE_FIRST_WINS;
            case NODE_CITY:
                return MODE_ALL_APPROVE;
            default:
                throw new IllegalArgumentException("没有这一关，算齐办法无处可问：" + node);
        }
    }

    /**
     * 落笔原话怎么记账：逐字等于「不可」记为不认可；其余原话（含「可」与带原文的
     * 认可意见）一律记为认可。认可与否只由这一处按原话放出来，报文自带的可／不可
     * 勾选不采信。
     */
    public static boolean isVetoOpinion(String opinion) {
        return opinion != null && OPINION_VETO.equals(opinion.trim());
    }

    /** 走满三关封住，或一句作数的「不可」止住：都只许看不许动，再递进来的笔一概不接 */
    public static boolean isFrozen(Integer status) {
        return status != null
                && (status.intValue() == STATUS_SEALED || status.intValue() == STATUS_STOPPED);
    }

    /** 段次只许一级一级往上走：这一关没齐，下一关不收——能往前挪的只此一下 */
    public static boolean adjacentAdvance(int from, int to) {
        return to == from + 1 && from >= NODE_INTAKE && from < NODE_CITY;
    }

    /**
     * 压回退到哪一关，由这一处定死：只退紧邻的上一关。报文写退到哪一关不采信；
     * 已经站在收件席头一关的，没什么可再收。
     */
    public static boolean canRollback(Integer status, int node) {
        return !isFrozen(status) && node > NODE_INTAKE && node <= NODE_CITY;
    }

    /**
     * 本关本轮还收不收这一笔、收下文怎么走，唯一裁定口。
     *
     * 判的时候只吃四样：本关算法（由关号查）、本关本轮已经收下的笔数、头一笔的
     * 说法（先落为准那关用）、来人这一笔认可不认可；同一人本关本轮再来第二笔，
     * 段上保留头一次那一句，不覆盖也不另存一份。
     *
     * @param node          服务端点出来的当下关（报文写的关不喂进来）
     * @param liveCount     本关本轮已收下、未被压回抹去的笔数
     * @param firstAgree    头一笔认可与否（一笔还没有时给 null）
     * @param sameApprover  来人本关本轮是不是已经落过一笔
     * @param incomingAgree 来人这一笔认可与否（由原话放出来）
     */
    public static Decision decide(int node, int liveCount, Boolean firstAgree,
                                  boolean sameApprover, boolean incomingAgree) {
        if (sameApprover) {
            // 同一人同关同轮第二句：头一句留着，这句不覆盖、不另存、不累笔
            return Decision.REJECT_DUP;
        }
        if (liveCount >= needCount(node)) {
            return Decision.REJECT_FULL;
        }
        switch (signMode(node)) {
            case MODE_SOLE:
                // 一名即过：这一位的说法一落单，关就齐／单就止，没有第二笔的位置
                return incomingAgree ? Decision.ACCEPT_COMPLETE : Decision.ACCEPT_STOP;
            case MODE_FIRST_WINS:
                // 先落为准：头一句是「不可」在头一笔处就已止住，走到这里头一句必是「可」
                if (liveCount == 0) {
                    return incomingAgree ? Decision.ACCEPT_WAIT : Decision.ACCEPT_STOP;
                }
                // 第二句照常收下、照常累笔、两方原话都留底，结论跟头一句走 → 算齐
                return Decision.ACCEPT_COMPLETE;
            case MODE_ALL_APPROVE:
                if (liveCount == 0) {
                    return incomingAgree ? Decision.ACCEPT_WAIT : Decision.ACCEPT_STOP;
                }
                // 头一句必是「可」（不是早止住了）：两条都可才算齐；一可一不可停在本关等
                return incomingAgree ? Decision.ACCEPT_COMPLETE : Decision.ACCEPT_WAIT_SPLIT;
            default:
                throw new IllegalStateException("算齐办法没有这一种：" + signMode(node));
        }
    }

    /**
     * 把签批留痕按时序一笔一笔放回去：立单起在收件席，推进只认紧邻的一下，
     * 压回只退一关（被抹去的笔不再进本轮计数），点齐到此封住，止住到此冻住。
     * 关次、情形、各关已到几人全由这一串放出来，不拿行面缓存凑。
     */
    public static ReplayResult replay(List<Event> events) {
        int node = NODE_INTAKE;
        boolean touched = false;
        boolean frozen = false;
        int status = STATUS_RUNNING;
        // 各关「到达」次数：立单算到达收件席一回，此后每推进／压回落到哪一关，哪一关新一轮；
        // 这个次数就是该关当下轮次
        int[] arrivals = new int[NODE_COUNT];
        // 各关本轮（轮次＝该关到达次数）还活着的笔数；压回抹去的、上一轮旧笔都不进
        int[] live = new int[NODE_COUNT];
        int signCount = 0;
        int rollbackCount = 0;
        if (events != null) {
            for (Event e : events) {
                if (e == null || e.action == null) {
                    continue;
                }
                String a = e.action;
                if (ACT_OPEN.equals(a)) {
                    touched = true;
                    node = NODE_INTAKE;
                    // 每到达一轮，这一关从头累计：旧轮的笔行留底，但不进新一轮活笔
                    live[NODE_INTAKE] = 0;
                    arrivals[NODE_INTAKE]++;
                } else if (ACT_SIGN.equals(a)) {
                    if (frozen || e.node == null || e.node.intValue() < 0
                            || e.node.intValue() >= NODE_COUNT) {
                        continue;
                    }
                    int n = e.node.intValue();
                    // 双保险：只数当下这一轮、未抹去的笔（到达时已先清零，轮次不符也丢）
                    if (!e.voided && e.roundNo == arrivals[n]) {
                        live[n]++;
                    }
                    signCount++;
                } else if (ACT_ADVANCE.equals(a)) {
                    if (!frozen && e.node != null && adjacentAdvance(node, e.node.intValue())) {
                        node = e.node.intValue();
                        live[node] = 0;
                        arrivals[node]++;
                    }
                } else if (ACT_ROLLBACK.equals(a)) {
                    if (!frozen && e.node != null && canRollback(STATUS_RUNNING, node)
                            && e.node.intValue() == node - 1) {
                        // 离开那一关本轮笔已在写库时标成抹去；回到的那一关也另起新一轮，
                        // 它上一轮旧笔行留底、查得着，但一笔都不进本轮计数
                        int to = e.node.intValue();
                        live[node] = 0;
                        live[to] = 0;
                        node = to;
                        arrivals[node]++;
                        rollbackCount++;
                    }
                } else if (ACT_SEAL.equals(a)) {
                    touched = true;
                    frozen = true;
                    status = STATUS_SEALED;
                } else if (ACT_STOP.equals(a)) {
                    touched = true;
                    frozen = true;
                    status = STATUS_STOPPED;
                }
            }
        }
        return new ReplayResult(touched ? Integer.valueOf(node) : null, status,
                java.util.Arrays.copyOf(live, NODE_COUNT),
                java.util.Arrays.copyOf(arrivals, NODE_COUNT), signCount, rollbackCount);
    }

    /** 裁定回话：收不收、收下文往哪走 */
    public enum Decision {
        /** 收下，关还没齐，整单留在本关等下一位 */
        ACCEPT_WAIT,
        /** 收下，本关算齐：收件席／认定席往前推一关，存备席则整张点齐 */
        ACCEPT_COMPLETE,
        /** 收下，一句作数的「不可」，整张单当即止住 */
        ACCEPT_STOP,
        /** 收下，存备席一可一不可：停在本关等，不前不止，解开只走压回 */
        ACCEPT_WAIT_SPLIT,
        /** 不收：同一人同关同轮第二句，头一句留着，不覆盖不另存 */
        REJECT_DUP,
        /** 不收：本关本轮笔已收满（分歧停等时再递也走这里） */
        REJECT_FULL
    }

    /** 留痕放回去的结果：当下关（无立单回 null）、情形、各关本轮活笔数、各关当前轮次、落笔／压回总笔数 */
    public static final class ReplayResult {
        private final Integer node;
        private final int status;
        private final int[] liveCounts;
        private final int[] rounds;
        private final int signCount;
        private final int rollbackCount;

        public ReplayResult(Integer node, int status, int[] liveCounts, int[] rounds,
                            int signCount, int rollbackCount) {
            this.node = node;
            this.status = status;
            this.liveCounts = liveCounts;
            this.rounds = rounds;
            this.signCount = signCount;
            this.rollbackCount = rollbackCount;
        }

        public Integer getNode() {
            return node;
        }

        public int getStatus() {
            return status;
        }

        /** 各关本轮活笔数（拷贝，防外面改） */
        public int[] getLiveCounts() {
            return java.util.Arrays.copyOf(liveCounts, liveCounts.length);
        }

        public int getLiveCount(int node) {
            return node >= 0 && node < liveCounts.length ? liveCounts[node] : 0;
        }

        /** 各关到达轮次（拷贝）；一次没到过的关为 0 */
        public int[] getRounds() {
            return java.util.Arrays.copyOf(rounds, rounds.length);
        }

        /** 当下关这一轮是第几轮（没到过给 1，与立单初始轮次对齐） */
        public int getCurrentRound(int node) {
            int r = node >= 0 && node < rounds.length ? rounds[node] : 0;
            return r == 0 ? 1 : r;
        }

        public int getSignCount() {
            return signCount;
        }

        public int getRollbackCount() {
            return rollbackCount;
        }
    }

    /** 留痕里的一笔；捋的时候只吃这一串 */
    public static final class Event {
        private final String action;
        private final Integer node;
        private final int roundNo;
        private final boolean voided;

        public Event(String action, Integer node, int roundNo, boolean voided) {
            this.action = action;
            this.node = node;
            this.roundNo = roundNo;
            this.voided = voided;
        }

        public String getAction() {
            return action;
        }

        public Integer getNode() {
            return node;
        }

        public int getRoundNo() {
            return roundNo;
        }

        public boolean isVoided() {
            return voided;
        }
    }

    /** 把一串留痕捋成 Event 串的小工具，免得各处自己装 */
    public static List<Event> events(List<String> actions, List<Integer> nodes,
                                     List<Integer> rounds, List<Boolean> voids) {
        List<Event> list = new ArrayList<Event>();
        int n = actions == null ? 0 : actions.size();
        for (int i = 0; i < n; i++) {
            list.add(new Event(actions.get(i), nodes.get(i),
                    rounds == null || rounds.get(i) == null ? 0 : rounds.get(i).intValue(),
                    voids != null && voids.get(i) != null && voids.get(i).booleanValue()));
        }
        return list;
    }
}
