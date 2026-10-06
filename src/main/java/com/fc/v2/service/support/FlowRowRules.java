package com.fc.v2.service.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 监测流水核收纯口径（不碰库、不沾 Spring）。
 *
 * 点行数、看数据、定去处、出回执四个动作共用这一份口径：拨抬头、核收环节、
 * 页面转述，哪儿都不许另算一遍。退回理由的判定次序固定，哪一行吃了哪一条，
 * 回话里一句一句点得清。
 *
 * @author fuce
 */
public final class FlowRowRules {

    /** 一拨行数顶的认定值：抬头那一格空着、填负、填得离谱，都按这个数认 */
    public static final int DEFAULT_CAP = 300;
    /** 抬头那格填过这个数就算离谱，当没填（按 {@link #DEFAULT_CAP} 认） */
    public static final int CAP_ABSURD = 10000;

    /** 点位码规范长度下限：不够的一律算对不上 */
    public static final int POINT_CODE_MIN_LEN = 6;
    /** 点位码规范长度上限：超出的按「码长超出规范」退回 */
    public static final int POINT_CODE_MAX_LEN = 20;

    /** 行落地情形：未勾 */
    public static final int STATUS_UNCHECKED = 0;
    /** 行落地情形：收下 */
    public static final int STATUS_ACCEPTED = 1;
    /** 行落地情形：退回 */
    public static final int STATUS_RETURNED = 2;

    /** 册层：现行名单 */
    public static final int LAYER_CURRENT = 0;
    /** 册层：历史册层（现行名单不再列，单独查得着） */
    public static final int LAYER_HISTORY = 1;

    /** 拨落地情形：已核收 */
    public static final int BATCH_ACCEPTED = 0;
    /** 拨落地情形：超顶整批未收（点行数那一步就超了写定的顶，一行不落） */
    public static final int BATCH_OVER_CAP = 1;

    /** 切法：按分隔符切 */
    public static final String PARSE_DELIMITER = "DELIMITER";
    /** 切法：按定宽切 */
    public static final String PARSE_FIXED = "FIXED";

    /** 退回理由：点位码没填 */
    public static final String REASON_CODE_BLANK = "点位码没填";
    /** 退回理由：码长超出规范 */
    public static final String REASON_CODE_TOO_LONG = "码长超出规范";
    /** 退回理由：读数次数与达标次数没一起到（只到一个，系统不代填，也不许当成 0 收下） */
    public static final String REASON_NUM_INCOMPLETE = "读数次数与达标次数没一起到";
    /** 退回理由：读数为负 */
    public static final String REASON_NEGATIVE = "读数为负";
    /** 退回理由：读数次数为 0 */
    public static final String REASON_ZERO = "读数次数为0";
    /** 退回理由：点位码对不上（底册无此码；拿题名近似去凑的、码长不够的一律算对不上） */
    public static final String REASON_CODE_UNMATCHED = "点位码对不上（底册无此码）";
    /** 退回理由：读数次数高过当地那条核定上限（后随上限值） */
    public static final String REASON_OVER_CAP = "读数次数高过核定上限";
    /** 退回理由：报文切不动（单独标出，递给回核看的人） */
    public static final String REASON_PARSE_FAIL = "报文切不动";

    /** 分隔符切法认这几样：中英文逗号、分号、顿号、竖线、空白 */
    private static final String SPLIT_REGEX = "[,，;；、|\\s]+";

    private FlowRowRules() {
    }

    /**
     * 顶的认定（唯一入口）：抬头那一格空着不填、填成负数、填得离谱（超过
     * {@link #CAP_ABSURD}），一律当没填按 {@link #DEFAULT_CAP} 认；其余照填的认。
     */
    public static int recognizeCap(Integer raw) {
        if (raw == null || raw.intValue() <= 0 || raw.intValue() > CAP_ABSURD) {
            return DEFAULT_CAP;
        }
        return raw.intValue();
    }

    /** 点位码没填（空串、全空白都算没填） */
    public static boolean codeBlank(String code) {
        return code == null || code.trim().isEmpty();
    }

    /** 码长不够规范下限：这种一律算对不上，不许拿题名近似去凑 */
    public static boolean codeTooShort(String code) {
        return code != null && code.trim().length() < POINT_CODE_MIN_LEN;
    }

    /** 码长超出规范上限：一律退回 */
    public static boolean codeTooLong(String code) {
        return code != null && code.trim().length() > POINT_CODE_MAX_LEN;
    }

    /**
     * 切法归一口径：一拨之内只许一种。认 DELIMITER／FIXED 与「分隔符／定宽」两种报法；
     * 没报切法或报了第三种切法，归成 null——这一拨每一行都切不动。
     */
    public static String normalizeParseKind(String raw) {
        if (raw == null) {
            return null;
        }
        String k = raw.trim();
        if (k.isEmpty()) {
            return null;
        }
        if (PARSE_DELIMITER.equalsIgnoreCase(k) || "分隔符".equals(k) || "按分隔符".equals(k)) {
            return PARSE_DELIMITER;
        }
        if (PARSE_FIXED.equalsIgnoreCase(k) || "定宽".equals(k) || "按定宽".equals(k)) {
            return PARSE_FIXED;
        }
        return null;
    }

    /**
     * 把报文里那串读数切出来：按本拨那一种切法切，切得动回一串读数，
     * 切不动回 null（这一行单独标出，不许因为它把整拨按住）。
     * 定宽切法要求宽度大于 0 且整串恰好切完，剩个尾巴也算切不动。
     */
    public static List<BigDecimal> parsePayload(String payload, String parseKind, Integer fixWidth) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        String kind = normalizeParseKind(parseKind);
        if (kind == null) {
            return null;
        }
        String body = payload.trim();
        String[] parts;
        if (PARSE_DELIMITER.equals(kind)) {
            parts = body.split(SPLIT_REGEX);
        } else {
            int w = fixWidth == null ? 0 : fixWidth.intValue();
            if (w <= 0 || body.length() % w != 0) {
                return null;
            }
            parts = new String[body.length() / w];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = body.substring(i * w, (i + 1) * w);
            }
        }
        if (parts.length == 0) {
            return null;
        }
        List<BigDecimal> out = new ArrayList<BigDecimal>(parts.length);
        for (String part : parts) {
            String s = part == null ? "" : part.trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                out.add(new BigDecimal(s));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    /**
     * 定去处（唯一入口，次序固定，先说先算）：
     * 点位码没填 → 码长超出规范 → 两数没一起到 → 读数为负 → 读数次数为 0
     * → 点位码对不上 → 读数次数高过核定上限 → 报文切不动。
     * 一条都不沾回 null（收下）；沾了回那一条理由（退回）。
     *
     * @param itemCode     报上来的点位码
     * @param qty          本行读数次数（可空，空即没一起到）
     * @param chkNum       本行达标次数（可空，空即没一起到）
     * @param pointMatched 点位码有没有在底册对上（码长不够的一律没对上）
     * @param needNum      对上的点位那条核定应测次数（没对上传 null）
     * @param parsed       报文切出来的读数串；切不动传 null
     */
    public static String decideReturn(String itemCode, BigDecimal qty, BigDecimal chkNum,
                                      boolean pointMatched, Integer needNum, List<BigDecimal> parsed) {
        if (codeBlank(itemCode)) {
            return REASON_CODE_BLANK;
        }
        if (codeTooLong(itemCode)) {
            return REASON_CODE_TOO_LONG;
        }
        // 两个数必须一起到；只到一个不代填、不当 0 收下，两个都没到一样不行
        if (qty == null || chkNum == null) {
            return REASON_NUM_INCOMPLETE;
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0 || anyNegative(parsed)) {
            return REASON_NEGATIVE;
        }
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return REASON_ZERO;
        }
        if (!pointMatched) {
            return REASON_CODE_UNMATCHED;
        }
        if (needNum != null && qty.compareTo(new BigDecimal(needNum.intValue())) > 0) {
            return REASON_OVER_CAP + "（核定上限" + needNum + "）";
        }
        if (parsed == null) {
            return REASON_PARSE_FAIL;
        }
        return null;
    }

    /** 行落地情形只许三种写法：0未勾 1收下 2退回；第四种写法不许落库 */
    public static boolean validStatus(Integer status) {
        return status != null
                && (status.intValue() == STATUS_UNCHECKED
                || status.intValue() == STATUS_ACCEPTED
                || status.intValue() == STATUS_RETURNED);
    }

    /** 行去处的回话：0未勾 1收下 2退回，其余只有「写法不明」四个字 */
    public static String statusText(Integer status) {
        if (status != null) {
            if (status.intValue() == STATUS_UNCHECKED) {
                return "未勾";
            }
            if (status.intValue() == STATUS_ACCEPTED) {
                return "收下";
            }
            if (status.intValue() == STATUS_RETURNED) {
                return "退回";
            }
        }
        return "写法不明";
    }

    /**
     * 「连着满两个年度」的那条线：create_time 早过或正压在 now 往前推两个整年，
     * 才算空悬满两个年度，够格转入历史册层。
     */
    public static Date twoYearsBefore(Date now) {
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.YEAR, -2);
        return c.getTime();
    }

    private static boolean anyNegative(List<BigDecimal> parsed) {
        if (parsed == null) {
            return false;
        }
        for (BigDecimal v : parsed) {
            if (v != null && v.compareTo(BigDecimal.ZERO) < 0) {
                return true;
            }
        }
        return false;
    }
}
