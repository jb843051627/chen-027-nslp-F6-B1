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
 * 执法文书出文留档对象 t_nslp_doc_issue
 *
 * 一纸一档。出文照哪一版取值，落笔时把那一版主键、版次、三个数钉在本档；
 * 往后新立一版不回改旧档。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_nslp_doc_issue")
@ApiModel(value = "TNslpDocIssue", description = "执法文书出文留档")
public class TNslpDocIssue implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 文种名（超标告知·责令限期改正·复查结论） */
    @TableField("tpl_name")
    @ApiModelProperty(value = "文种名")
    private String tplName;

    /** 出文那一刻开口的那一版 t_nslp_doc_tpl.id（钉住，不随新版改） */
    @TableField("tpl_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "出文当版主键（钉住）")
    private Long tplId;

    /** 当版版次（落笔钉住） */
    @TableField("ver_no")
    @ApiModelProperty(value = "当版版次（落笔钉住）")
    private Integer verNo;

    /** 所对点位 t_nslp_point.id */
    @TableField("point_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所对点位")
    private Long pointId;

    /** 所对点位码 */
    @TableField("point_no")
    @ApiModelProperty(value = "所对点位码")
    private String pointNo;

    /** 本版应填项数（落笔时的快照） */
    @TableField("var_num")
    @ApiModelProperty(value = "本版应填项数（落笔快照）")
    private Integer varNum;

    /** 已取到值的项数（落笔时的快照） */
    @TableField("fill_num")
    @ApiModelProperty(value = "已取到值的项数（落笔快照）")
    private Integer fillNum;

    /** 还差几项（落笔时由前两个轧出，钉住） */
    @TableField("miss_num")
    @ApiModelProperty(value = "还差几项（落笔轧出钉住）")
    private Integer missNum;

    /** 出文那一刻（选版只认这一刻） */
    @TableField("issue_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "出文那一刻")
    private Date issueTime;

    /** 出文情形 0已出 */
    @TableField("status")
    @ApiModelProperty(value = "出文情形 0已出")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者（谁出的文，留得下来） */
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

    public String getTplName() {
        return tplName;
    }

    public void setTplName(String tplName) {
        this.tplName = tplName;
    }

    public Long getTplId() {
        return tplId;
    }

    public void setTplId(Long tplId) {
        this.tplId = tplId;
    }

    public Integer getVerNo() {
        return verNo;
    }

    public void setVerNo(Integer verNo) {
        this.verNo = verNo;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public String getPointNo() {
        return pointNo;
    }

    public void setPointNo(String pointNo) {
        this.pointNo = pointNo;
    }

    public Integer getVarNum() {
        return varNum;
    }

    public void setVarNum(Integer varNum) {
        this.varNum = varNum;
    }

    public Integer getFillNum() {
        return fillNum;
    }

    public void setFillNum(Integer fillNum) {
        this.fillNum = fillNum;
    }

    public Integer getMissNum() {
        return missNum;
    }

    public void setMissNum(Integer missNum) {
        this.missNum = missNum;
    }

    public Date getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(Date issueTime) {
        this.issueTime = issueTime;
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
