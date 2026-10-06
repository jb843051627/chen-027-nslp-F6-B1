package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 监测流水核收行对象 t_nslp_flow_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_nslp_flow_row")
@ApiModel(value = "TNslpFlowRow", description = "监测流水核收行")
public class TNslpFlowRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 监测流水批次码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "监测流水批次码")
    private String batchNo;

    /** 原报行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原报行次")
    private Integer rowNo;

    /** 被对上的点位码 */
    @TableField("item_code")
    @ApiModelProperty(value = "被对上的点位码")
    private String itemCode;

    /** 本行读数次数 */
    @TableField("qty")
    @ApiModelProperty(value = "本行读数次数")
    private BigDecimal qty;

    /** 本行达标次数 */
    @TableField("chk_num")
    @ApiModelProperty(value = "本行达标次数")
    private BigDecimal chkNum;

    /** 来路（自动站直采／监测车补报两条来路） */
    @TableField("put_kind")
    @ApiModelProperty(value = "来路（自动站直采／监测车补报两条来路）")
    private String putKind;

    /** 行落地情形 0未勾 1已收下 2已退回（第四种写法不许落库） */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0未勾 1已收下 2已退回")
    private Integer status;

    /** 退回理由（吃了哪一条，一句一句问得出）；未退空白 */
    @TableField("ret_reason")
    @ApiModelProperty(value = "退回理由，未退空白")
    private String retReason;

    /** 解析情形 0切得动 1切不动（单独标出递给回核看的人，不按住整拨） */
    @TableField("parse_flag")
    @ApiModelProperty(value = "解析情形 0切得动 1切不动")
    private Integer parseFlag;

    /** 底册比对 0相符或未报 1不合（以底册为准，回执单列） */
    @TableField("reg_diff_flag")
    @ApiModelProperty(value = "底册比对 0相符或未报 1不合")
    private Integer regDiffFlag;

    /** 点位题名（以点位底册带出的为准，报文那串只拿来比对） */
    @TableField("point_name")
    @ApiModelProperty(value = "点位题名（以底册带出为准）")
    private String pointName;

    /** 区册代号（以点位底册带出的为准） */
    @TableField("zone_no")
    @ApiModelProperty(value = "区册代号（以底册带出为准）")
    private String zoneNo;

    /** 册层 0现行 1历史（未勾连满两个年度转入历史册层，只此一处挪动） */
    @TableField("layer_flag")
    @ApiModelProperty(value = "册层 0现行 1历史")
    private Integer layerFlag;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getChkNum() {
        return chkNum;
    }

    public void setChkNum(BigDecimal chkNum) {
        this.chkNum = chkNum;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRetReason() {
        return retReason;
    }

    public void setRetReason(String retReason) {
        this.retReason = retReason;
    }

    public Integer getParseFlag() {
        return parseFlag;
    }

    public void setParseFlag(Integer parseFlag) {
        this.parseFlag = parseFlag;
    }

    public Integer getRegDiffFlag() {
        return regDiffFlag;
    }

    public void setRegDiffFlag(Integer regDiffFlag) {
        this.regDiffFlag = regDiffFlag;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public Integer getLayerFlag() {
        return layerFlag;
    }

    public void setLayerFlag(Integer layerFlag) {
        this.layerFlag = layerFlag;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
