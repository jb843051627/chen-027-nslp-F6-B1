package com.fc.v2.service.support;

/**
 * 名册摆法纯口径（不碰库、不沾 Spring）。
 *
 * 三个查问位置都认前缀匹配与全等匹配；行分到哪一种入册情形只由回放后的步子与叫停记号
 * 定，不在名册处另算步子。视图只呈现库里真实存在、且归本视图底数的行。
 *
 * @author fuce
 * @date 2026-10-06
 */
public final class LedgerRules {

    /** 入册情形桶：在走（待测/已测，含空步旧行） */
    public static final String BUCKET_LIVE = "在走";
    /** 入册情形桶：已压卡（到头，名额已腾） */
    public static final String BUCKET_SEALED = "已压卡";
    /** 入册情形桶：已叫停（腾名额但行仍在册） */
    public static final String BUCKET_STOPPED = "已叫停";

    /** 视图：按区层类别 */
    public static final String VIEW_BY_KIND = "byKind";
    /** 视图：按区册代号 */
    public static final String VIEW_BY_ZONE = "byZone";
    /** 视图：按入册情形 */
    public static final String VIEW_BY_INTAKE = "byIntake";

    private LedgerRules() {
    }

    /**
     * 全等或前缀匹配都认：查问值空着表示这一位置不限；给了值，字段等于它或以它打头
     * 都收。字段空着、查问值不空，不收（不拿空去凑前缀）。
     */
    public static boolean matchesExactOrPrefix(String field, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        if (field == null) {
            return false;
        }
        String q = query.trim();
        return field.equals(q) || field.startsWith(q);
    }

    /**
     * 一行归到哪一种入册情形：压卡的归「已压卡」；叫停的归「已叫停」；
     * 其余（待测、已测、空步旧行）都归「在走」——旧行空步不当已压卡。
     */
    public static String intakeBucket(Integer stage, Integer stopFlag) {
        if (PointFlowRules.isSealed(stage)) {
            return BUCKET_SEALED;
        }
        if (PointFlowRules.isStopped(stopFlag)) {
            return BUCKET_STOPPED;
        }
        return BUCKET_LIVE;
    }

    /**
     * 本层值守岗看得见这一行否：行所挂靠区册的区层类别恰为本层。类别空着（挂靠已断）
     * 的不拿给本层凑数。
     */
    public static boolean visibleToLayer(String rowZoneKind, String viewerLayer) {
        return rowZoneKind != null && viewerLayer != null
                && !viewerLayer.trim().isEmpty()
                && rowZoneKind.equals(viewerLayer.trim());
    }

    /**
     * 区局岗看得见这一行否：四层都看得见，但范围仍限于划归它的那些区册——
     * 只认行上区册代号落在划归全集里；划归全集空着就一行也看不见，不拿邻层凑。
     */
    public static boolean visibleToBureau(String rowZoneNo, java.util.Collection<String> assignedZoneNos) {
        if (rowZoneNo == null || assignedZoneNos == null || assignedZoneNos.isEmpty()) {
            return false;
        }
        for (String z : assignedZoneNos) {
            if (z != null && z.equals(rowZoneNo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这一行落不落得了笔：谁建的册、谁改的行至少指得出一位；两处（create_by/update_by）
     * 都指不出人的，按没办完（不进在册底数）。
     */
    public static boolean attributed(String createBy, String updateBy) {
        return (createBy != null && !createBy.trim().isEmpty())
                || (updateBy != null && !updateBy.trim().isEmpty());
    }
}
