package com.fc.v2.service.support;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * 复测与巡查安排单夜间走表纯口径（不碰库、不沾 Spring）。
 *
 * 复测通知与现场巡查记在同一张安排单上，一行一事，夜里走一趟。下列各问只在本处作答，
 * 旁处算出的不作凭：
 * <ul>
 *   <li>那一截开不开：只认 02:00 到 05:00（含 02:00 那一刻，到 05:00 即收手）。</li>
 *   <li>哪些行算届期：这一行届期那一刻走到本次这一刻就算到，恰好等于这一刻的也算；
 *       挑单与回写同用这一条取法。</li>
 *   <li>可提前几日：只看本行自己那一格，零头不算、法定节假日不算、停办日不算；
 *       那一格空着就当不提前。</li>
 *   <li>一趟至多派几行：500，装满即止，够不着的行什么也不标，照旧搁着等下一趟。</li>
 *   <li>办成一行的记号、发不成一行的原由、趟末那三个数怎么放出答话——都只认这一处。</li>
 * </ul>
 *
 * @author fuce
 */
public final class TaskDispatchRules {

    /** 走表窗口起（含）：02:00 */
    public static final int WIN_FROM = 2;
    /** 走表窗口止（不含）：05:00，过了立刻收手 */
    public static final int WIN_TO = 5;

    /** 条目情形：搁着 */
    public static final int STATUS_WAIT = 0;
    /** 条目情形：已发出 */
    public static final int STATUS_DONE = 1;
    /** 条目情形：发不成 */
    public static final int STATUS_FAIL = 2;

    /** 一趟至多派 500 行，装满即止，剩下的下一趟接着挑 */
    public static final int RUN_CAP = 500;

    /** 走表落账人名：程序夜里走表，不是哪一位岗上的人 */
    public static final String ACTOR = "程序夜发";

    /** 窗外按下／一趟什么也没动时的答话，一字不差只此一句 */
    public static final String MSG_NO_CHANGE = "本轮未改动安排单";

    /** 失败原由写进备注格，备注格只放得下 500 字 */
    public static final int REMARK_CAP = 500;

    /** 提前日数再离谱也至多往回数十年（3660 日），防一格填成天文数字把一趟拖死 */
    public static final int MAX_EARLY_DAYS = 3660;

    /**
     * 法定节假日报表（2026 年一册，只此一处；新年度的通知下来只改这一处）。
     * 照《国务院办公厅关于2026年部分节假日安排的通知》落：元旦 1.1–1.3，春节 2.15–2.23，
     * 清明 4.4–4.6，劳动节 5.1–5.5，端午 6.19–6.21，中秋 9.25–9.27，国庆 10.1–10.7。
     */
    private static final Set<LocalDate> STATUTORY_HOLIDAYS = statutoryHolidays2026();

    private TaskDispatchRules() {
    }

    // ------------------------------------------------------------------
    // 那一截开不开
    // ------------------------------------------------------------------

    /**
     * 这一刻动不动手：只认 02:00 到 05:00 这一截——02:00 那一刻起算开，
     * 一到 05:00 即算收手（05:00:00 那一刻起不再动手）。
     */
    public static boolean isOpen(Date at) {
        if (at == null) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= WIN_FROM && h < WIN_TO;
    }

    // ------------------------------------------------------------------
    // 哪些行算届期、可提前几日
    // ------------------------------------------------------------------

    /**
     * 届没届期唯一取法：届期那一刻走到本次这一刻就算到（恰好等于这一刻的也算）。
     * 挑单与回写同用这一条，两头不许各算各的。
     */
    public static boolean isDue(Date effectiveDue, Date at) {
        return effectiveDue != null && at != null && !effectiveDue.after(at);
    }

    /**
     * 本行许提前几日：只看本行自己那一格。空着就当不提前；零头不算（向零取整）；
     * 填成负数、零都当不提前；填得离谱至多往回数 {@link #MAX_EARLY_DAYS} 日。
     */
    public static int earlyDaysOf(BigDecimal amount) {
        if (amount == null) {
            return 0;
        }
        long n = amount.longValue();
        if (n <= 0) {
            return 0;
        }
        return n > MAX_EARLY_DAYS ? MAX_EARLY_DAYS : (int) n;
    }

    /**
     * 本行最早可办那一刻：届期那一刻往回数「许提前几日」个可数日，时刻不动。
     * 可数的日子才算数——法定节假日不算、停办日（周六、周日）不算。
     * 届期空着的行没有可办那一刻（永远届不了期）。
     */
    public static Date effectiveDueAt(Date dueAt, BigDecimal amount) {
        if (dueAt == null) {
            return null;
        }
        int n = earlyDaysOf(amount);
        if (n <= 0) {
            return dueAt;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(dueAt);
        int moved = 0;
        while (moved < n) {
            c.add(Calendar.DAY_OF_MONTH, -1);
            LocalDate d = LocalDate.of(c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
            if (isCountableDay(d)) {
                moved++;
            }
        }
        return c.getTime();
    }

    /**
     * 这一行本趟挑不挑得中：只挑还搁着的（已发出的、发不成的都不再碰）；
     * 删除标记空着当正常；届不届期照 {@link #effectiveDueAt} 与 {@link #isDue} 这一条取法。
     */
    public static boolean pickable(Integer status, Integer delFlag, Date dueAt,
                                   BigDecimal amount, Date at) {
        if (status == null || status.intValue() != STATUS_WAIT) {
            return false;
        }
        if (delFlag != null && delFlag.intValue() == 1) {
            return false;
        }
        return isDue(effectiveDueAt(dueAt, amount), at);
    }

    /**
     * 先办哪一头的行：最届期的在前，届期同一刻的按主键小的在前；
     * 届期空着的排最后（本趟本就捞不进来，这里只图排得定、回回排得一样）。
     */
    public static int comparePickOrder(Date dueA, Long idA, Date dueB, Long idB) {
        if (dueA == null && dueB == null) {
            return compareId(idA, idB);
        }
        if (dueA == null) {
            return 1;
        }
        if (dueB == null) {
            return -1;
        }
        int c = dueA.compareTo(dueB);
        return c != 0 ? c : compareId(idA, idB);
    }

    // ------------------------------------------------------------------
    // 可数日：法定节假日不算、停办日不算
    // ------------------------------------------------------------------

    /** 停办日：每周六、周日（雷打不动的那两天） */
    public static boolean isSuspensionDay(LocalDate d) {
        if (d == null) {
            return false;
        }
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
    }

    /** 法定节假日：只认本类那一册（2026 年），旁处另算的不作凭 */
    public static boolean isStatutoryHoliday(LocalDate d) {
        return d != null && STATUTORY_HOLIDAYS.contains(d);
    }

    /** 可数日：不是法定节假日、也不是停办日的日子才数得进去 */
    public static boolean isCountableDay(LocalDate d) {
        return d != null && !isStatutoryHoliday(d) && !isSuspensionDay(d);
    }

    // ------------------------------------------------------------------
    // 办成／发不成一行的记号
    // ------------------------------------------------------------------

    /**
     * 这一回发给谁：行落到谁名下就发给谁——只认本行 create_by 落的那一位，
     * 别去邻行借。行上没落着人名的，回 null（这一行站不住，发不出去）。
     */
    public static String recipientOf(String createBy) {
        if (createBy == null) {
            return null;
        }
        String r = createBy.trim();
        return r.isEmpty() ? null : r;
    }

    /** 记要里补的那一句：发出那一刻与发给谁，格式只此一处 */
    public static String sendNote(Date at, String recipient) {
        return "［已发出 " + ts(at) + " 发给 " + recipient + "］";
    }

    /** 记要原句后头补上这一回的那一句（原句空着就只留新句） */
    public static String appendSendNote(String content, Date at, String recipient) {
        return (content == null ? "" : content) + sendNote(at, recipient);
    }

    /** 发不成的原由一句话：冠以「发不成：」，放不进备注格的截到恰好放下 */
    public static String failNote(String reason) {
        String r = reason == null ? "" : reason.trim();
        String note = "发不成：" + (r.isEmpty() ? "原由没留下" : r);
        return note.length() <= REMARK_CAP ? note : note.substring(0, REMARK_CAP);
    }

    // ------------------------------------------------------------------
    // 趟末三个数与答话
    // ------------------------------------------------------------------

    /**
     * 这一趟到底动没动单：只由趟末三个数放出来——发出或没成占一样就算动过，
     * 一行也没发出、一行也没标没成，就是没动。
     */
    public static boolean modified(int sent, int failed) {
        return sent > 0 || failed > 0;
    }

    /**
     * 趟末答话唯一出处：只引用这一趟给出的三个数，不拿内存里的临时数回话，
     * 也不另起一轮逐行数来对冲。没动单时只回那一句，连按几回一字不差。
     */
    public static String replyOf(int picked, int sent, int failed) {
        if (!modified(sent, failed)) {
            return MSG_NO_CHANGE;
        }
        return "本轮已改动安排单：挑中" + picked + "行、发出" + sent + "行、没成" + failed + "行";
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static int compareId(Long a, Long b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }

    /** 墙钟字符串：与库面 datetime 一个模样，比较与落账同用这一副格式 */
    private static String ts(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static Set<LocalDate> statutoryHolidays2026() {
        Set<LocalDate> s = new HashSet<LocalDate>();
        addRange(s, 2026, 1, 1, 1, 3);     // 元旦
        addRange(s, 2026, 2, 15, 2, 23);   // 春节
        addRange(s, 2026, 4, 4, 4, 6);     // 清明
        addRange(s, 2026, 5, 1, 5, 5);     // 劳动节
        addRange(s, 2026, 6, 19, 6, 21);   // 端午
        addRange(s, 2026, 9, 25, 9, 27);   // 中秋
        addRange(s, 2026, 10, 1, 10, 7);   // 国庆
        return Collections.unmodifiableSet(s);
    }

    private static void addRange(Set<LocalDate> s, int y, int m1, int d1, int m2, int d2) {
        LocalDate d = LocalDate.of(y, m1, d1);
        LocalDate end = LocalDate.of(y, m2, d2);
        while (!d.isAfter(end)) {
            s.add(d);
            d = d.plusDays(1);
        }
    }
}
