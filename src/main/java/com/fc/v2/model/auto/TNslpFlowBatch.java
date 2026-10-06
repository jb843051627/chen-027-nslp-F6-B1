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
import java.util.Date;

/**
 * 监测流水核收拨抬头对象 t_nslp_flow_batch
 *
 * 一拨一档：写定的顶与进门点出来的行数都落在这一档上，别处不再设第二个来源；
 * 同一批次码第二回到达只照面，不另起一拨。
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_nslp_flow_batch")
@ApiModel(value = "TNslpFlowBatch", description = "监测流水核收拨抬头")
public class TNslpFlowBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 监测流水批次码（一拨一码；同一码第二回到达只照面，不另起一拨） */
    @TableField("batch_no")
    @ApiModelProperty(value = "监测流水批次码")
    private String batchNo;

    /** 写定的顶（抬头那一格空着/填负/填得离谱，一律按300认后落库，只此一处来源） */
    @TableField("cap_num")
    @ApiModelProperty(value = "写定的顶")
    private Integer capNum;

    /** 进门点出来的行数（事后翻得出来当初点了几行） */
    @TableField("row_num")
    @ApiModelProperty(value = "进门点出来的行数")
    private Integer rowNum;

    /** 本拨切法 DELIMITER按分隔符／FIXED按定宽（一拨之内只许一种） */
    @TableField("parse_kind")
    @ApiModelProperty(value = "本拨切法")
    private String parseKind;

    /** 拨落地情形 0已核收 1超顶整批未收 */
    @TableField("status")
    @ApiModelProperty(value = "拨落地情形 0已核收 1超顶整批未收")
    private Integer status;

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

    public Integer getCapNum() {
        return capNum;
    }

    public void setCapNum(Integer capNum) {
        this.capNum = capNum;
    }

    public Integer getRowNum() {
        return rowNum;
    }

    public void setRowNum(Integer rowNum) {
        this.rowNum = rowNum;
    }

    public String getParseKind() {
        return parseKind;
    }

    public void setParseKind(String parseKind) {
        this.parseKind = parseKind;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
