package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TNslpZoneBook;

/**
 * 声环境功能区区册底册 Service接口。
 *
 * 挂靠、撤出不收新、冗余区册全称对齐，都只问这一处；建档与改挂共用。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITNslpZoneBookService {

    /** 照主键查（撤出的册也点得到，只是不收新行） */
    TNslpZoneBook selectById(Long id);

    /**
     * 按区册代号点拨在册底册（del_flag=0，含已撤出——是否收新行由口径处判）；
     * 查无此册回 null。挂靠认不认只看代号全等，不做前缀、不拿近似凑。
     */
    TNslpZoneBook getByZoneNo(String zoneNo);

    /**
     * 挂靠校验唯一口：代号在区册底里找不到、或该册已并走撤出，回那句点名的话；
     * 过得回 null。
     */
    String attachError(String zoneNo);

    /** 行上该写的区册全称：一律以区册底为准（对不齐就拿区册底的全称纠正这一行） */
    String resolveZoneName(String zoneNo);

    /** 区层类别（甲层/乙层/丙层/丁层）下在册的区册代号全集；本层值守岗只看得见这些 */
    List<TNslpZoneBook> listByKinds(List<String> zoneKinds);

    /** 全部在册区册（del_flag=0，含已撤出的旧册，旧行仍要挂着名） */
    List<TNslpZoneBook> listAll();
}
