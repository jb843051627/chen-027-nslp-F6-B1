package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.service.ITNslpZoneBookService;
import com.fc.v2.service.support.PointArchiveRules;

/**
 * 声环境功能区区册底册 Service业务层处理。
 *
 * 挂靠校验、撤出不收新、冗余全称对齐只此一处；建档与改挂都走这里，别处不下结论、
 * 不自行改口。并走之前就存在的行不搞连带清除——那属于点位行的事，不在本服务里抹行。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TNslpZoneBookServiceImpl implements ITNslpZoneBookService {

    @javax.annotation.Resource
    private TNslpZoneBookMapper nslpZoneBookMapper;

    @Override
    public TNslpZoneBook selectById(Long id) {
        return this.nslpZoneBookMapper.selectById(id);
    }

    @Override
    public TNslpZoneBook getByZoneNo(String zoneNo) {
        if (zoneNo == null || zoneNo.trim().isEmpty()) {
            return null;
        }
        return this.nslpZoneBookMapper.selectOne(new QueryWrapper<TNslpZoneBook>()
                .eq("zone_no", zoneNo.trim())
                .eq("del_flag", 0)
                .last("limit 1"));
    }

    @Override
    public String attachError(String zoneNo) {
        return PointArchiveRules.screenZone(getByZoneNo(zoneNo));
    }

    @Override
    public String resolveZoneName(String zoneNo) {
        TNslpZoneBook zone = getByZoneNo(zoneNo);
        if (zone == null) {
            return null;
        }
        return PointArchiveRules.alignZoneName(zone, null);
    }

    @Override
    public List<TNslpZoneBook> listByKinds(List<String> zoneKinds) {
        QueryWrapper<TNslpZoneBook> w = new QueryWrapper<TNslpZoneBook>().eq("del_flag", 0);
        if (zoneKinds != null && !zoneKinds.isEmpty()) {
            w.in("zone_kind", zoneKinds);
        }
        return this.nslpZoneBookMapper.selectList(w);
    }

    @Override
    public List<TNslpZoneBook> listAll() {
        return this.nslpZoneBookMapper.selectList(
                new QueryWrapper<TNslpZoneBook>().eq("del_flag", 0));
    }
}
