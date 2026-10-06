package com.fc.v2.service.support;

import java.util.List;

/**
 * 点位入册后三态流转纯口径（不碰库、不沾 Spring）。
 *
 * 三态钉死在这一处：待测、已测、压卡，先后不许添、不许改名。这一行眼下走到哪一步，
 * 只由身后那串留痕（{@code t_nslp_point_step}）一笔一笔放出来——屏上写的档、经办人
 * 心里记的档都不作依据。未到压卡，已测可退回一步重来（先前那一趟的记录照旧留着，
 * 不与后补的并排）；推进到压卡便到了头，添不进、动不得、撤不掉，要改只能另起新行。
 *
 * 早年间纸质簿搬进来的旧行，步数、点位码、核验串全空：本口径也容得下——空步不当作
 * 已压卡，在走的旧行照样占着同点位名额，但一看、一碰都不许报错。
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class PointFlowRules {

    /** 步一：待测（起册落在这一步） */
    public static final int STEP_PENDING = 0;
    /** 步二：已测 */
    public static final int STEP_MEASURED = 1;
    /** 步三：压卡（走到这一步便到了头） */
    public static final int STEP_SEALED = 2;
    /** 步子只有三步，先后钉死 */
    public static final int STEP_COUNT = 3;

    /** 留痕动作：起册 */
    public static final String ACT_START = "起册";
    /** 留痕动作：往前推进一步 */
    public static final String ACT_FORWARD = "推进";
    /** 留痕动作：退回一步重来 */
    public static final String ACT_BACK = "退回";
    /** 留痕动作：压卡（到头） */
    public static final String ACT_SEAL = "压卡";
    /** 留痕动作：就地叫停（腾名额但不是压卡，行仍在册） */
    public static final String ACT_STOP = "叫停";

    /** 三态的名字，步数与名字都只此一处出处 */
    private static final String[] STEP_NAMES = {"待测", "已测", "压卡"};

    private PointFlowRules() {
    }

    /** 步名回话：0..2 出本名；null（旧行没数出步来）与其余只有「步数不明」四个字 */
    public static String stepName(Integer step) {
        if (step != null && step >= 0 && step < STEP_COUNT) {
            return STEP_NAMES[step];
        }
        return "步数不明";
    }

    /** 到没到压卡那一步。旧行空步（null）不算压卡 */
    public static boolean isSealed(Integer step) {
        return step != null && step.intValue() == STEP_SEALED;
    }

    /** 就没就地叫停 */
    public static boolean isStopped(Integer stopFlag) {
        return stopFlag != null && stopFlag.intValue() == 1;
    }

    /**
     * 这一行眼下占不占「同点位只容一条在走」的名额。
     * 压卡的、就地叫停的都腾出名额；删了的不算；旧行空步当作在走，照样占名额
     *（不能把在走的旧行当已压卡、凭空把名额让出去）。
     */
    public static boolean occupiesSlot(Integer step, Integer stopFlag, Integer delFlag) {
        if (delFlag != null && delFlag.intValue() == 1) {
            return false;
        }
        if (isSealed(step) || isStopped(stopFlag)) {
            return false;
        }
        return true;
    }

    /** 压卡之后添不进、动不得、撤不掉；叫停的行在册但也不再往前走 */
    public static boolean frozen(Integer step, Integer stopFlag) {
        return isSealed(step) || isStopped(stopFlag);
    }

    /**
     * 推进一步的目标步：待测(0)→已测(1)；旧行空步也容得下，当头一回推进按待测→已测办。
     * 已测要到压卡走 {@link #sealTarget} 那专门的一下；其余（已压卡/已叫停）回 null 推不动。
     */
    public static Integer forwardTarget(Integer step) {
        if (step == null || step.intValue() == STEP_PENDING) {
            return Integer.valueOf(STEP_MEASURED);
        }
        return null;
    }

    /** 退回只认已测(1)→待测(0) 这一下；还在待测、已压卡、已叫停都退不了 */
    public static boolean canRollback(Integer step, Integer stopFlag) {
        return !isStopped(stopFlag) && step != null && step.intValue() == STEP_MEASURED;
    }

    /** 压卡只在已测(1) 这一步发起；旧行空步、待测、已压卡、已叫停都压不了 */
    public static boolean canSeal(Integer step, Integer stopFlag) {
        return !isStopped(stopFlag) && step != null && step.intValue() == STEP_MEASURED;
    }

    /** 压卡落成的目标步 */
    public static int sealTarget() {
        return STEP_SEALED;
    }

    /**
     * 把留痕按时序一笔一笔放回去，当下停在哪一步只由这一串放出来：
     * 起册落待测；推进只认紧邻的一下；退回只从已测收回到待测；压卡到此到头；
     * 叫停另记（步子不变，腾名额）。旧行一笔留痕都没有时 step 回 null——不是压卡。
     */
    public static ReplayResult replay(List<Event> events) {
        Integer stage = null;
        boolean touched = false;
        boolean sealed = false;
        boolean stopped = false;
        int forwardCount = 0;
        int backCount = 0;
        if (events != null) {
            for (Event e : events) {
                if (e == null || e.action == null) {
                    continue;
                }
                String a = e.action;
                if (ACT_START.equals(a)) {
                    touched = true;
                    stage = Integer.valueOf(STEP_PENDING);
                } else if (ACT_FORWARD.equals(a)) {
                    Integer to = (sealed || stopped) ? null : forwardTarget(stage);
                    if (to != null) {
                        touched = true;
                        stage = to;
                        forwardCount++;
                    }
                } else if (ACT_BACK.equals(a)) {
                    if (!sealed && !stopped && canRollback(stage, 0)) {
                        touched = true;
                        stage = Integer.valueOf(STEP_PENDING);
                        backCount++;
                    }
                } else if (ACT_SEAL.equals(a)) {
                    if (!sealed && !stopped && canSeal(stage, 0)) {
                        touched = true;
                        stage = Integer.valueOf(STEP_SEALED);
                        sealed = true;
                    }
                } else if (ACT_STOP.equals(a)) {
                    if (!sealed) {
                        touched = true;
                        stopped = true;
                    }
                }
            }
        }
        // 一笔留痕都没有的旧行回 null——不是压卡；有过起册/推进/退回/压卡的则数出当下步
        return new ReplayResult(touched ? stage : null, sealed, stopped, forwardCount, backCount);
    }

    /** 留痕放回去的结果：当下步（旧行无起册为 null）、压没压卡、叫没叫停、推进/退回笔数 */
    public static final class ReplayResult {
        private final Integer step;
        private final boolean sealed;
        private final boolean stopped;
        private final int forwardCount;
        private final int backCount;

        public ReplayResult(Integer step, boolean sealed, boolean stopped, int forwardCount, int backCount) {
            this.step = step;
            this.sealed = sealed;
            this.stopped = stopped;
            this.forwardCount = forwardCount;
            this.backCount = backCount;
        }

        public Integer getStep() {
            return step;
        }

        public boolean isSealed() {
            return sealed;
        }

        public boolean isStopped() {
            return stopped;
        }

        public int getForwardCount() {
            return forwardCount;
        }

        public int getBackCount() {
            return backCount;
        }
    }

    /** 留痕里的一笔（动作＋落笔时所处步）；捋的时候只吃这一串 */
    public static final class Event {
        private final String action;
        private final int step;

        public Event(String action, int step) {
            this.action = action;
            this.step = step;
        }

        public String getAction() {
            return action;
        }

        public int getStep() {
            return step;
        }
    }
}
