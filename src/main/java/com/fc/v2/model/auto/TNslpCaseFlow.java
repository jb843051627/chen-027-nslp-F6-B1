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
 * 噪声投诉与超标处置办件流转单对象 t_nslp_case_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_nslp_case_flow")
@ApiModel(value = "TNslpCaseFlow", description = "噪声投诉与超标处置办件流转单")
public class TNslpCaseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 噪声投诉与超标处置办件的件次 */
    @TableField("biz_no")
    @ApiModelProperty(value = "噪声投诉与超标处置办件的件次")
    private String bizNo;

    /** 点位码（立件四样之一，一处点位一条） */
    @TableField("point_no")
    @ApiModelProperty(value = "点位码")
    private String pointNo;

    /** 所属区册代号（立件四样之一，以底册带出的为准） */
    @TableField("zone_no")
    @ApiModelProperty(value = "所属区册代号")
    private String zoneNo;

    /** 处数（立件四样之一，至少一处） */
    @TableField("occ_num")
    @ApiModelProperty(value = "处数")
    private Integer occNum;

    /** 准备折算的那一个核查读数（手机端敲进来回显，折算前先取一位小数逢半进一） */
    @TableField("check_reading")
    @ApiModelProperty(value = "准备折算的那一个核查读数")
    private BigDecimal checkReading;

    /** 当前关次 0..3（受件/核查/责令/复查收尾，关次一律由后端数出来） */
    @TableField("stage")
    @ApiModelProperty(value = "当前关次 0..3（受件/核查/责令/复查收尾）")
    private Integer stage;

    /** 单子走到哪一关 0未起 1在办 2已封住 */
    @TableField("status")
    @ApiModelProperty(value = "单子走到哪一关 0未起 1在办 2已封住")
    private Integer status;

    /** 本关一句（同一关第二句盖不上去，本行只留头一句） */
    @TableField("content")
    @ApiModelProperty(value = "本关一句")
    private String content;

    /** 最近一次挪关动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次挪关动作")
    private String lastAction;

    /** 责令关核收原话；记成「可」复查收尾才开得了工 */
    @TableField("order_accept")
    @ApiModelProperty(value = "责令关核收原话")
    private String orderAccept;

    /** 复查收尾见证岗甲落笔（两名各落一笔） */
    @TableField("witness_a")
    @ApiModelProperty(value = "见证岗甲落笔")
    private String witnessA;

    /** 复查收尾见证岗乙落笔（两名各落一笔） */
    @TableField("witness_b")
    @ApiModelProperty(value = "见证岗乙落笔")
    private String witnessB;

    /** 复测凭证那一串（封关时写回本行） */
    @TableField("proof_no")
    @ApiModelProperty(value = "复测凭证那一串")
    private String proofNo;

    /** 本趟各关头一句按语位图 bit0..bit3（收回的关腾位，重新挪到从头累计） */
    @TableField("note_mask")
    @ApiModelProperty(value = "本趟各关头一句按语位图")
    private Integer noteMask;

    /** 收口情由 空=走满末关封住 1反映人撤回 2区册并走（后两种视同收口） */
    @TableField("close_kind")
    @ApiModelProperty(value = "收口情由 空封住 1反映人撤回 2区册并走")
    private Integer closeKind;

    /** 末关作废 0正常 1已作废（作废的件名册照常露脸，只是不给动手的把手） */
    @TableField("void_flag")
    @ApiModelProperty(value = "末关作废 0正常 1已作废")
    private Integer voidFlag;

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

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getPointNo() {
        return pointNo;
    }

    public void setPointNo(String pointNo) {
        this.pointNo = pointNo;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public Integer getOccNum() {
        return occNum;
    }

    public void setOccNum(Integer occNum) {
        this.occNum = occNum;
    }

    public BigDecimal getCheckReading() {
        return checkReading;
    }

    public void setCheckReading(BigDecimal checkReading) {
        this.checkReading = checkReading;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public String getOrderAccept() {
        return orderAccept;
    }

    public void setOrderAccept(String orderAccept) {
        this.orderAccept = orderAccept;
    }

    public String getWitnessA() {
        return witnessA;
    }

    public void setWitnessA(String witnessA) {
        this.witnessA = witnessA;
    }

    public String getWitnessB() {
        return witnessB;
    }

    public void setWitnessB(String witnessB) {
        this.witnessB = witnessB;
    }

    public String getProofNo() {
        return proofNo;
    }

    public void setProofNo(String proofNo) {
        this.proofNo = proofNo;
    }

    public Integer getNoteMask() {
        return noteMask;
    }

    public void setNoteMask(Integer noteMask) {
        this.noteMask = noteMask;
    }

    public Integer getCloseKind() {
        return closeKind;
    }

    public void setCloseKind(Integer closeKind) {
        this.closeKind = closeKind;
    }

    public Integer getVoidFlag() {
        return voidFlag;
    }

    public void setVoidFlag(Integer voidFlag) {
        this.voidFlag = voidFlag;
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
