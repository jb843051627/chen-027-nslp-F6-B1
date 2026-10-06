package com.fc.v2.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

import com.fc.v2.model.auto.TNslpLimitLine;

/**
 * 功能区限值线判层纯口径（不碰库、不沾 Spring）。
 *
 * 大屏昼段/夜段折线、折线上点出来的数、后端逐条回算，三处都只许走这一份口径，
 * 不许各算一遍。判定次序固定为：精度对齐 → 脏配置剔除 → 量程 → 贴层。
 *
 * @author fuce
 */
public final class LimitLineRules {

    /** 折算读数量程下限（含） */
    public static final BigDecimal RANGE_MIN = new BigDecimal("30.0");
    /** 折算读数量程上限（含） */
    public static final BigDecimal RANGE_MAX = new BigDecimal("120.0");
    /** 贴不上层 / 无在位线 / 越界 / 脏配置，回话统一取 0（"不归层"） */
    public static final int NOT_LEVEL = 0;

    private static final String TS_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private LimitLineRules() {
    }

    /**
     * 精度对齐：定值与折算读数一律取到一位小数、逢半进一（HALF_UP）。
     * 前年错位事故就出在取数处进位、比数处没进位；两边必须都过这一道。
     */
    public static BigDecimal alignOne(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 三档定值体检。干净返回 null；不干净返回要递给前端的整句提示，
     * 不许只写「数值不对」——须点清缺的是哪一格、又是哪一格没大过它前面那一格。
     * 多档毛病一次说全，用「；」连上。
     */
    public static String checkThresholds(BigDecimal rawTh1, BigDecimal rawTh2, BigDecimal rawTh3) {
        // 体检同样站在"对齐之后"：定值先各取一位小数逢半进一，再谈缺不缺、大不大，
        // 不许取数处进了位、比数处没进位（前年 54.996 对 55.0 串档的根）。
        BigDecimal th1 = alignOne(rawTh1);
        BigDecimal th2 = alignOne(rawTh2);
        BigDecimal th3 = alignOne(rawTh3);
        StringBuilder err = new StringBuilder();
        if (th1 == null) {
            appendErr(err, "起算限值（第1档）缺档，三档上限定值不齐全，本轮不进来");
        }
        if (th2 == null) {
            appendErr(err, "够线限值（第2档）缺档，三档上限定值不齐全，本轮不进来");
        }
        if (th3 == null) {
            appendErr(err, "封顶限值（第3档）缺档，三档上限定值不齐全，本轮不进来");
        }
        // 三档缺档时不再接着比大小，免得拿空值再添一条糊涂提示
        if (th1 != null && th2 != null && th2.compareTo(th1) <= 0) {
            appendErr(err, "够线限值（第2档）" + plain(th2)
                    + " 没有大过它前面那一格起算限值（第1档）" + plain(th1)
                    + "，三档必须从小到大，本轮不进来");
        }
        if (th2 != null && th3 != null && th3.compareTo(th2) <= 0) {
            appendErr(err, "封顶限值（第3档）" + plain(th3)
                    + " 没有大过它前面那一格够线限值（第2档）" + plain(th2)
                    + "，三档必须从小到大，本轮不进来");
        }
        if (th3 != null && th3.compareTo(RANGE_MAX) > 0) {
            appendErr(err, "封顶限值（第3档）" + plain(th3)
                    + " 高过量程上限 120.0，属脏配置，一并剔掉（留着它，脏值会被算成紧的一层）");
        }
        return err.length() == 0 ? null : err.toString();
    }

    /** 整条线体检（委托三档体检），干净返回 null */
    public static String checkLine(TNslpLimitLine line) {
        if (line == null) {
            return "限值线为空";
        }
        return checkThresholds(line.getTh1Max(), line.getTh2Max(), line.getTh3Max());
    }

    /**
     * at 时刻在位三件事：起用那一一日已走到（头含，eff_start &lt;= at）、
     * 交班那一一日还没到（尾不含，at &lt; eff_end）、情形记 0。
     * eff_end 留空按「一直管下去」认——早年人工存量的口子，删不得。
     *
     * 统一走墙钟字符串比较：JDBC serverTimezone 与本机不对称时直接绑 Date 会整体偏移，
     * 实测在切换当日错档（见旧实现踩坑注记）。
     */
    public static boolean activeAt(TNslpLimitLine line, Date at) {
        if (line == null || at == null) {
            return false;
        }
        if (line.getStatus() == null || line.getStatus() != 0) {
            return false;
        }
        Date start = line.getEffStart();
        if (start == null) {
            return false;
        }
        String a = wallClock(at);
        if (a.compareTo(wallClock(start)) < 0) {
            return false;
        }
        if (line.getEffEnd() != null && a.compareTo(wallClock(line.getEffEnd())) >= 0) {
            return false;
        }
        return true;
    }

    /**
     * 贴层（四句，唯一入口）：
     * 不到起算限值落第 1 层；到了起算限值而不到够线限值落第 2 层；
     * 到了够线限值而不到封顶限值落第 3 层；到了封顶限值及以上落第 4 层。
     * 「到了」含等号：恰好压在某档上，收进以该档起算的那一层（55.0 对 45/55/70 出第 3 层）。
     * 脏线、越界（对齐后比 30 小、比 120 大）一律返回 {@link #NOT_LEVEL}。
     */
    public static int levelOf(TNslpLimitLine line, BigDecimal rawReading) {
        if (line == null || rawReading == null) {
            return NOT_LEVEL;
        }
        if (checkLine(line) != null) {
            return NOT_LEVEL;
        }
        BigDecimal x = alignOne(rawReading);
        if (x.compareTo(RANGE_MIN) < 0 || x.compareTo(RANGE_MAX) > 0) {
            return NOT_LEVEL;
        }
        BigDecimal t1 = alignOne(line.getTh1Max());
        BigDecimal t2 = alignOne(line.getTh2Max());
        BigDecimal t3 = alignOne(line.getTh3Max());
        if (x.compareTo(t1) < 0) {
            return 1;
        }
        if (x.compareTo(t2) < 0) {
            return 2;
        }
        if (x.compareTo(t3) < 0) {
            return 3;
        }
        return 4;
    }

    /**
     * 同一刻多条线在位时谁开口：先比交班次序（priority 大的先开口），
     * 并列再比线代号字面先后，靠后那条说了算；再并列用主键兜底，保证一问一答。
     */
    public static Comparator<TNslpLimitLine> winnerOrder() {
        return new Comparator<TNslpLimitLine>() {
            @Override
            public int compare(TNslpLimitLine a, TNslpLimitLine b) {
                int pa = a.getPriority() == null ? 0 : a.getPriority();
                int pb = b.getPriority() == null ? 0 : b.getPriority();
                if (pa != pb) {
                    return pb - pa;
                }
                String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
                String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
                int c = cb.compareTo(ca);
                if (c != 0) {
                    return c;
                }
                Long ia = a.getId() == null ? Long.MIN_VALUE : a.getId();
                Long ib = b.getId() == null ? Long.MIN_VALUE : b.getId();
                return ib.compareTo(ia);
            }
        };
    }

    /** 回给前端/大屏的那一句话：1..4 出「第N层」，其余只有「不归层」三个字 */
    public static String answerOf(int level) {
        if (level >= 1 && level <= 4) {
            return "第" + level + "层";
        }
        return "不归层";
    }

    private static void appendErr(StringBuilder err, String piece) {
        if (err.length() > 0) {
            err.append("；");
        }
        err.append(piece);
    }

    private static String plain(BigDecimal v) {
        return alignOne(v).toPlainString();
    }

    private static String wallClock(Date d) {
        return new SimpleDateFormat(TS_PATTERN).format(d);
    }
}
