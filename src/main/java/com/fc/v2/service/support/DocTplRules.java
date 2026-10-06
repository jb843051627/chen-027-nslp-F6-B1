package com.fc.v2.service.support;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpDocTpl;

/**
 * 执法文书版次与出文纯口径（不碰库、不沾 Spring）。
 *
 * 三个文种：超标告知、责令限期改正、复查结论。每文种存若干版，版次数值大的先开口。
 * 一纸该照哪一版取值，只看出文那一刻：起用那一一日不晚于出文（头含）、交班那一一日
 * 晚于出文（尾不含）。旧文照它当初那版留档，新版不回改旧文。
 *
 * 每版三个数：应填项数、已取到值的项数、还差几项；第三个数不留手填格子，由前两个
 * 相减得，且只由服务端这一处轧。应填项里但凡有一项取不到值，这一纸出不来——缺的项
 * 一项一项点名，不许让打印环节把一张空白纸当文书发出去。撤版（情形 1）不进现行清单，
 * 但照主键仍查得着。
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class DocTplRules {

    /** 文种一 */
    public static final String NAME_OVER_STANDARD = "超标告知";
    /** 文种二 */
    public static final String NAME_ORDER_CORRECT = "责令限期改正";
    /** 文种三 */
    public static final String NAME_REVIEW_CONCLUSION = "复查结论";

    /** 版次情形：在位（进现行可选清单） */
    public static final int STATUS_ACTIVE = 0;
    /** 版次情形：已撤版（不进现行清单，照主键仍查得着） */
    public static final int STATUS_WITHDRAWN = 1;

    /** 应填项第 1 项的名：点位题名（空着即缺这一项） */
    public static final String ITEM_POINT_TITLE = "点位题名";
    /** 应填项第 2 项的名：限值线（没挂上即缺这一项） */
    public static final String ITEM_LIMIT_LINE = "限值线";

    private static final String TS_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private DocTplRules() {
    }

    /** 三个文种只认这三个名；其余名字不是可出文的文种 */
    public static boolean isKnownName(String tplName) {
        return NAME_OVER_STANDARD.equals(tplName)
                || NAME_ORDER_CORRECT.equals(tplName)
                || NAME_REVIEW_CONCLUSION.equals(tplName);
    }

    /**
     * at 那一刻这一版在不在位：情形记 0、起用那一一日已走到（头含）、交班那一一日
     * 还没到（尾不含）。eff_end 留空按一直用下去认。统一走墙钟字符串比较，避开
     * JDBC serverTimezone 与本机不对称造成的切换当日错档。
     */
    public static boolean activeAt(TNslpDocTpl tpl, Date at) {
        if (tpl == null || at == null) {
            return false;
        }
        if (tpl.getStatus() == null || tpl.getStatus().intValue() != STATUS_ACTIVE) {
            return false;
        }
        if (tpl.getEffStart() == null) {
            return false;
        }
        String a = wallClock(at);
        if (a.compareTo(wallClock(tpl.getEffStart())) < 0) {
            return false;
        }
        if (tpl.getEffEnd() != null && a.compareTo(wallClock(tpl.getEffEnd())) >= 0) {
            return false;
        }
        return true;
    }

    /**
     * 同一刻同一文种多版在位时谁开口：版次数值大的先说话；并列再用主键新者兜底，
     * 保证一问一答、按版次取值与整表取优两答同一版。
     */
    public static Comparator<TNslpDocTpl> winnerOrder() {
        return new Comparator<TNslpDocTpl>() {
            @Override
            public int compare(TNslpDocTpl a, TNslpDocTpl b) {
                int va = a.getVerNo() == null ? 0 : a.getVerNo().intValue();
                int vb = b.getVerNo() == null ? 0 : b.getVerNo().intValue();
                if (va != vb) {
                    return vb - va;
                }
                Long ia = a.getId() == null ? Long.MIN_VALUE : a.getId();
                Long ib = b.getId() == null ? Long.MIN_VALUE : b.getId();
                return ib.compareTo(ia);
            }
        };
    }

    /**
     * 还差几项，只此一处轧法：应填项数 － 已取到值的项数。不留手填格子，每回出文前
     * 由服务端拿这一处重轧，盖掉版行里旧的 fill/miss。var 为空或为负按 0 认会在别处点名，
     * 这里只做算术。
     */
    public static int miss(int varNum, int fillNum) {
        return varNum - fillNum;
    }

    /**
     * 把一版的应填项按次序排出来：第 1 项点位题名、第 2 项限值线；版里声明的应填项数
     * 多于两项时，其余按「第N应填项」依次排（其取值由出文报文 extra 递进来，取不到即缺）。
     *
     * @param varNum 本版应填项数
     * @return 应填项名目（长度恰为 varNum；varNum 小于 2 时至少含前两项以外的合理处理见下）
     */
    public static List<String> requiredItems(Integer varNum) {
        int n = varNum == null || varNum.intValue() < 2 ? 2 : varNum.intValue();
        List<String> items = new ArrayList<String>(n);
        items.add(ITEM_POINT_TITLE);
        items.add(ITEM_LIMIT_LINE);
        for (int i = 3; i <= n; i++) {
            items.add("第" + i + "应填项");
        }
        return items;
    }

    /**
     * 逐项点出取不到值的应填项。点位题名、限值线由点位行带出（空着/没挂上即缺）；
     * 第 3 项起从出文报文 extra（序号→取值）里取，取不到或空白即缺。
     * 一项不缺回空单；缺几项就几项一项一行，不许并成「数据不对」。
     *
     * @param varNum       本版应填项数
     * @param pointTitle   点位题名（null/空白即缺第 1 项）
     * @param limitCode    挂上的限值线代号（null/空白即缺第 2 项）
     * @param extraValues  第 3 项起的取值（键为项序号，从 3 开始）；可空
     */
    public static List<String> missingItems(Integer varNum, String pointTitle, String limitCode,
                                            java.util.Map<Integer, String> extraValues) {
        List<String> items = requiredItems(varNum);
        List<String> missing = new ArrayList<String>();
        for (int i = 0; i < items.size(); i++) {
            int seq = i + 1;
            String name = items.get(i);
            String value;
            if (seq == 1) {
                value = pointTitle;
            } else if (seq == 2) {
                value = limitCode;
            } else {
                value = extraValues == null ? null : extraValues.get(Integer.valueOf(seq));
            }
            if (value == null || value.trim().isEmpty()) {
                missing.add(name);
            }
        }
        return missing;
    }

    /**
     * 版自身的配置体检：应填项数得是不小于 2 的非负整数（至少有点位题名、限值线两样），
     * 起用那一一日不能为空。不干净回逐句点名；干净回 null。
     */
    public static String checkTpl(TNslpDocTpl tpl) {
        if (tpl == null) {
            return "文书版本为空";
        }
        StringBuilder err = new StringBuilder();
        if (tpl.getVarNum() == null || tpl.getVarNum().intValue() < 2) {
            err.append("本版应填项数得是不小于 2 的整数（至少有点位题名、限值线两样）");
        }
        if (tpl.getEffStart() == null) {
            if (err.length() > 0) {
                err.append("；");
            }
            err.append("起用那一一日空着，没法判这版什么时候开口");
        }
        if (tpl.getEffEnd() != null && tpl.getEffStart() != null
                && tpl.getEffEnd().compareTo(tpl.getEffStart()) <= 0) {
            if (err.length() > 0) {
                err.append("；");
            }
            err.append("交班那一一日得晚于起用那一一日");
        }
        return err.length() == 0 ? null : err.toString();
    }

    private static String wallClock(Date d) {
        return new SimpleDateFormat(TS_PATTERN).format(d);
    }
}
