package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TNslpPoint;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 名册一种摆法的回话。
 *
 * 三种摆法（按区层类别／按区册代号／按入册情形）的处数都出自同一份底数：分组处数
 * 合计必等于总数；前端照这一处给的数摆，不另数一遍。空着是合法结果，不拿邻层凑。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "LedgerView", description = "名册一种摆法（底数唯一）")
public class LedgerView implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 摆法 byKind/byZone/byIntake */
    @ApiModelProperty(value = "摆法")
    private String view;

    /** 年度 */
    @ApiModelProperty(value = "年度")
    private Integer regYear;

    /** 底数总数（三种摆法同一批行必须一样） */
    @ApiModelProperty(value = "底数总数")
    private int total;

    /** 分组处数：键为区层类别/区册代号/入册情形，值为处数；合计=total */
    @ApiModelProperty(value = "分组处数（合计=总数）")
    private Map<String, Integer> groupCounts = new LinkedHashMap<String, Integer>();

    /** 本页行（页大小只取区局配置那一处） */
    @ApiModelProperty(value = "本页行")
    private List<TNslpPoint> rows = new ArrayList<TNslpPoint>();

    /** 第几页（从 1 起） */
    @ApiModelProperty(value = "页码")
    private int pageNum;

    /** 每页几条（区局配置） */
    @ApiModelProperty(value = "每页几条")
    private int pageSize;

    /** 共几页 */
    @ApiModelProperty(value = "共几页")
    private int pages;

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Integer getRegYear() {
        return regYear;
    }

    public void setRegYear(Integer regYear) {
        this.regYear = regYear;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public Map<String, Integer> getGroupCounts() {
        return groupCounts;
    }

    public void setGroupCounts(Map<String, Integer> groupCounts) {
        this.groupCounts = groupCounts;
    }

    public List<TNslpPoint> getRows() {
        return rows;
    }

    public void setRows(List<TNslpPoint> rows) {
        this.rows = rows;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
