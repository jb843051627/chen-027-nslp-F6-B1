package com.fc.v2.service.support;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TNslpZoneBook;

/**
 * 点位建档纯口径（不碰库、不沾 Spring）。
 *
 * 建档要过五道筛，次序钉死，前一道没过不进后一道；各道报各道的错，不许合并成一句
 * 「数据不对」：
 * <ol>
 *   <li>点位码：随报送材料进来，系统不另发第二串、也不核对构成规矩；只挡同年度同区册
 *       底下「另有一条同码且仍在册」的建立动作——操作对象正是那一行自己时按更新办，不挡。</li>
 *   <li>挂靠：区册代号在区册底里找不到，建行建不成，点名是代号这一栏对不上；已撤出的
 *       区册不收新行。行上冗余区册全称以区册为准对齐。</li>
 *   <li>数量：核定应测次数没填/不是整数/为负/比已核验达标次数小，逐栏点名；已达标反超
 *       核定同样不落库。还差几回＝核定－已达标，只此一处轧法。</li>
 *   <li>名额：同一处点位同时只容一行在走；前一行没压卡也没叫停，第二行开不出来。</li>
 *   <li>落笔人：谁建的册、谁改的行要留在那一行上；两处都指不出人的，按没办完处理。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class PointArchiveRules {

    /** 册面/在走状态只此一处用：在走（含旧行空步）、压卡、叫停见 {@link PointFlowRules} */

    private PointArchiveRules() {
    }

    // ------------------------------------------------------------------
    // 第一道：点位码
    // ------------------------------------------------------------------

    /** 点位码随材料进来；空串全空白算没给（系统不另发第二串，空着建不成） */
    public static boolean codeBlank(String pointNo) {
        return pointNo == null || pointNo.trim().isEmpty();
    }

    /**
     * 第一道筛：建立动作遇同年度同区册底下已有一条同码且仍在册（占名额）的行即拒。
     * 唯一放过的情形：这次操作对象正是那一行自己（更新）。
     *
     * @param hasOtherLiveSameCode 库里是否另有一条同年度、同区册、同码且仍占名额的行
     * @return 不合回这句点名的话；过得回 null
     */
    public static String screenCode(boolean codeBlank, boolean hasOtherLiveSameCode) {
        if (codeBlank) {
            return "点位码这一栏空着：点位码随报送材料进来，系统不另发第二串，建不成";
        }
        if (hasOtherLiveSameCode) {
            return "同一年度、同一区册底下已有一条同点位码且仍在册的行，这一条建立动作拒掉"
                    + "（要改原行走更新，不当成多出的一条）";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 第二道：挂靠
    // ------------------------------------------------------------------

    /**
     * 第二道筛：代号拿到区册底里找不到，建行建不成，点名是代号这一栏对不上；
     * 找得到但已并走撤出（整段划走／改挂别册），不再收新行。
     * 过得回 null。
     */
    public static String screenZone(TNslpZoneBook zone) {
        if (zone == null) {
            return "区册代号这一栏对不上：区册底里查无此册，建行建不成";
        }
        if (zone.getStatus() != null && zone.getStatus().intValue() == 1) {
            return "该区册已并走撤出，这一段不再收新行（并走之前就在的旧行照旧列、照旧计数）";
        }
        return null;
    }

    /**
     * 行上冗余存的区册全称跟区册自己对不齐时，以区册为准：该用区册底的全称回 null
     * 之外的「对齐后的全称」。两处各留一份说法算缺陷——这里只纠正，不拿它挡住建行。
     *
     * @return 行上该写的全称（一律以区册底为准）；zone 为空回 null
     */
    public static String alignZoneName(TNslpZoneBook zone, String rowZoneName) {
        if (zone == null) {
            return rowZoneName;
        }
        return zone.getZoneName();
    }

    /** 行上冗余全称与区册底是否对不齐（对不齐要以区册为准改过来，记一处缺陷） */
    public static boolean zoneNameMismatch(TNslpZoneBook zone, String rowZoneName) {
        return zone != null && !eq(zone.getZoneName(), rowZoneName);
    }

    // ------------------------------------------------------------------
    // 第三道：数量
    // ------------------------------------------------------------------

    /**
     * 第三道筛：核定应测次数、已核验达标次数逐栏点名。毛病一次说全，用「；」连上；
     * 干净回空单。核定：没填、不是整数、负数、比已达标小，一一指名；已达标反超核定同挡。
     *
     * @param needRaw 核定应测次数（报上来的原字串，可能不成整数）
     * @param gotRaw  已核验达标的次数（空着按一次还没核验、即 0 认）
     */
    public static List<String> quantityErrors(String needRaw, String gotRaw) {
        List<String> errs = new ArrayList<String>();
        Integer need = parseCount(needRaw, "核定应测次数", errs);
        Integer got = parseOptionalCount(gotRaw, "已核验达标的次数", errs);
        if (need != null && need.intValue() < 0) {
            errs.add("核定应测次数这一栏不能是负数：" + needRaw);
        }
        if (got != null && got.intValue() < 0) {
            errs.add("已核验达标的次数这一栏不能是负数：" + gotRaw);
        }
        if (need != null && got != null && need.intValue() < got.intValue()) {
            errs.add("核定应测次数（" + need + "）比已核验达标的次数（" + got
                    + "）还小，这一行进不了库；已达标次数反超核定同样不让落库");
        }
        return errs;
    }

    /**
     * 还差几回，只此一处轧法：核定应测次数 － 已核验达标的次数。
     * 30 配 12 得 18；20 配 7 得 13；两数相等得 0。调用前须先过 {@link #quantityErrors}。
     * 每回写库之前都由这里重轧，重算结果盖掉上一回落库的那一个，不认手填。
     */
    public static int lack(int need, int got) {
        return need - got;
    }

    // ------------------------------------------------------------------
    // 第四道：名额
    // ------------------------------------------------------------------

    /**
     * 第四道筛：同一处点位同时只容一行在走。前一行没压卡、也没就地叫停，第二行开不出来
     *（两家维护单位同日各报一遍、或同一处点位换人重报，都不许一点位叠两行）。
     *
     * @param hasOtherLivePoint 该点位码下是否另有一条仍占名额的行（压卡/叫停/删除的不占）
     */
    public static String screenSlot(boolean hasOtherLivePoint) {
        if (hasOtherLivePoint) {
            return "同一处点位已有一行在走（前一行没压卡、也没叫停），第二行开不出来"
                    + "——两家维护单位同日各报、或换人重报都不许一点位叠两行";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 第五道：落笔人
    // ------------------------------------------------------------------

    /**
     * 第五道筛（建档）：谁建的册要在那一行上留得下来，复核时点得出落笔的是哪一位。
     * 指不出人回这句；建行这笔按没办完处理（不落库）。
     */
    public static String screenCreator(String createBy) {
        if (isBlank(createBy)) {
            return "建册没留下落笔人（create_by 空着），复核点不出是哪一位，按没办完处理";
        }
        return null;
    }

    /**
     * 第五道筛（改线）：谁改的行同样要留名。改线这笔 update_by 指不出人，按没办完处理。
     */
    public static String screenUpdater(String updateBy) {
        if (isBlank(updateBy)) {
            return "改行没留下落笔人（update_by 空着），复核点不出是哪一位，按没办完处理";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static Integer parseCount(String raw, String field, List<String> errs) {
        if (isBlank(raw)) {
            errs.add(field + "这一栏没填，这一行进不了库");
            return null;
        }
        String s = raw.trim();
        try {
            // 「30.0」「30 」这类不算整数：只认整数字面
            if (!s.matches("[+-]?\\d+")) {
                errs.add(field + "这一栏得是整数，录进来的是「" + s + "」");
                return null;
            }
            return Integer.valueOf(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            errs.add(field + "这一栏得是整数，录进来的是「" + s + "」");
            return null;
        }
    }

    private static Integer parseOptionalCount(String raw, String field, List<String> errs) {
        if (isBlank(raw)) {
            // 建档时可能一回都还没核验达标：空着按 0 认，不当缺项、不代填成别的
            return Integer.valueOf(0);
        }
        String s = raw.trim();
        if (!s.matches("[+-]?\\d+")) {
            errs.add(field + "这一栏得是整数，录进来的是「" + s + "」");
            return null;
        }
        return Integer.valueOf(Integer.parseInt(s));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
