package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.LevelQuery;
import com.fc.v2.model.custom.LimitLineVerdict;
import com.fc.v2.model.custom.PointArchiveSubmit;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 声环境监测点位底册 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITNslpPointService {

    /** 按主键查询 */
    TNslpPoint selectTNslpPointById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TNslpPoint> selectTNslpPointList(Wrapper<TNslpPoint> queryWrapper);

    /** 新增 */
    int insertTNslpPoint(TNslpPoint record);

    /** 修改 */
    int updateTNslpPoint(TNslpPoint record);

    /** 批量删除 */
    int deleteTNslpPointByIds(String ids);

    /** 按主键删除 */
    int deleteTNslpPointById(Long id);

    /**
     * 点位归层（新方法承接，不动旧签名）：问一枚折算读数，走限值线那一份唯一算法。
     * 点位行上挂着线代号就按线代号取值，没挂走整表取优；
     * 归到 1..4 层就把开口线的线代号与起用那一一日钉进点位行两格；
     * 贴不上层或压根没有在位线，回话只有「不归层」，两格清成空白，不拿邻线数值凑答复。
     */
    LimitLineVerdict pinPointLevel(Long pointId, BigDecimal input, Date at, String objKind);

    /**
     * 一批点位一次回算、一次钉线：大屏昼段夜段折线、折线上的点值、后端逐条回算
     * 消费同一批发话（库只照一面，算法只走一遍）。问话里用 pointNo 认点位。
     */
    List<LimitLineVerdict> pinPointLevelBatch(List<LevelQuery> queries);

    /**
     * 看板上挂着线代号、回到 at 那一刻却寻不到在位版本的点位，一条条列出来
     * （撤下线照主键仍点得到，但不替它回话；挂码与在位版本对不上即列出）。
     */
    List<TNslpPoint> listPinnedOrphans(Date at);

    // ------------------------------------------------------------------
    // 建档：两条来路（建档岗逐条 / 复测核收带出）共用这一套，旧方法本体不动
    // ------------------------------------------------------------------

    /**
     建档：过五道筛（点位码在册唯一→区册代号在册且未撤出→数量→同点在走名额→落笔人），
     * 各报各的错；缺一回在写库前由口径处唯一轧法重轧并写回 lack_num。
     * 建档成功这一行起在「待测」，并压一笔起册留痕。存量空步旧行不报错、不当已压卡。
     *
     * @return 建起来的那一行（含重轧后的 lack_num、对齐后的 zone_name、系统数出的 flow_stage）
     * @throws IllegalArgumentException 过不了某道筛时，消息逐道逐栏点名
     */
    TNslpPoint archive(PointArchiveSubmit submit);

    /**
     * 已达标次数被更正：只改 got_num，lack_num 当场重轧盖写；建错的行同样过数量那道筛
     *（核定不能比已达标小、已达标不能反超核定）。不改点位码/区册/年度。
     */
    TNslpPoint correctGot(Long id, Integer got, String operator);

    /**
     * 这一行改挂到别的区册：lack_num 按第三趟重轧写回；新册代号仍须在册且未撤出；
     * 已压卡的行动不得（要改只能另起新行）；冗余 zone_name 以新册为准对齐。
     */
    TNslpPoint rezone(Long id, String newZoneNo, String operator);

    /**
     * 复测核收收下一条读数后带出建行：与 {@link #archive} 走同一套五道筛与数量算法，
     * 界面上不长第二种样子。need=核定应测次数（取自该点位底册或报文），got=本次新增达标 0/1。
     * 已存在的点位列不新建第二行，只把这一趟归并到那一行（见服务处说明）。
     */
    TNslpPoint archiveFromRetest(String pointNo, String zoneNo, Integer regYear,
                                 Integer need, Integer addGot, String operator);

    /**
     * 三态·推进一步：待测→已测。步子只由身后留痕串自行数出，不信行上 flow_stage
     *（先回放留痕再判）；已冻结（压卡/叫停）推不动。
     */
    TNslpPoint advanceFlow(Long id, String operator, String note);

    /**
     * 三态·退回一步重来：已测→待测。先前那一趟的留痕照旧留着不抹，只添一笔退回，
     * 不与后补的并排。已冻结退不了。
     */
    TNslpPoint rollbackFlow(Long id, String operator, String note);

    /**
     * 三态·压卡：只在已测发起，成功后这一行添不进、动不得、撤不掉，同点位名额腾出，
     * 要改只能另起新行。缺落笔人按没办完。
     */
    TNslpPoint sealFlow(Long id, String operator, String note);

    /**
     * 三态·就地叫停：没到压卡也能叫停，叫停后同点位第二行开得出（腾名额），但这一行
     * 仍在册、照常列得出来、照常计入在走处数之外的统计口径见名册处；叫停后不再往前。
     */
    TNslpPoint stopFlow(Long id, String operator, String note);

    /**
     * 当下走到哪一步：只由 t_nslp_point_step 那串留痕回放得出，不信 flow_stage 格、
     * 不信屏显/手记。旧行一笔留痕都没有回 null（不是压卡）。
     */
    Integer currentStage(Long id);

    /**
     * 一批点位的当下步，一次回放出来（名册分档只认这一处，不在名册处再算第二遍步子）。
     * 键为点位主键，值为当下步（旧行无留痕映射到 null）。
     */
    java.util.Map<Long, Integer> currentStageMap(java.util.Collection<Long> ids);
}
