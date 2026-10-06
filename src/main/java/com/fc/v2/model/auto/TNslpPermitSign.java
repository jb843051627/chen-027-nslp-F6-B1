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
 * 签批单逐笔留痕对象 t_nslp_permit_sign
 *
 * 一次落笔只有这一个入口，一笔一行，按落单时刻时序压在签批单身前。
 * 关次、轮次写的都是服务端认下的结果；压回抹去只置 void_flag，行不删，
 * 上一轮旧笔一句一句仍查得着。同刻两笔各记各的时刻，不许并成一笔。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_nslp_permit_sign")
@ApiModel(value = "TNslpPermitSign", description = "签批单逐笔留痕")
public class TNslpPermitSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所随签批单 t_nslp_permit_bill.id */
    @TableField("bill_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所随签批单")
    private Long billId;

    /** 动作 立单/落笔/推进/压回/点齐/止住（一次落笔只走落笔这一个入口） */
    @TableField("action")
    @ApiModelProperty(value = "动作 立单/落笔/推进/压回/点齐/止住")
    private String action;

    /** 落笔那一刻服务端认下的关 0收件席 1认定席 2存备席 */
    @TableField("node_no")
    @ApiModelProperty(value = "落笔那一刻服务端认下的关")
    private Integer nodeNo;

    /** 落笔那一刻本关第几轮（首次到达为1，压回重走加一） */
    @TableField("round_no")
    @ApiModelProperty(value = "落笔那一刻本关第几轮")
    private Integer roundNo;

    /** 落笔人（同一人同关同轮只许一笔） */
    @TableField("sign_by")
    @ApiModelProperty(value = "落笔人")
    private String signBy;

    /** 落笔原话（逐字为「不可」才按不认可记账） */
    @TableField("opinion")
    @ApiModelProperty(value = "落笔原话")
    private String opinion;

    /** 认可与否 1认可 0不认可（由原话放出来，报文勾选不采信） */
    @TableField("agree_flag")
    @ApiModelProperty(value = "认可与否 1认可 0不认可")
    private Integer agreeFlag;

    /** 抹去标记 0本轮活着 1被压回抹去（行留底，不进本轮计数） */
    @TableField("void_flag")
    @ApiModelProperty(value = "抹去标记 0活着 1压回抹去")
    private Integer voidFlag;

    /** 落单那一刻（同刻两笔各记各的，不许并成一笔） */
    @TableField("sign_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落单那一刻")
    private Date signTime;

    /** 同刻先后次序（sign_time 并列时以本列断先后） */
    @TableField("seq")
    @ApiModelProperty(value = "同刻先后次序")
    private Integer seq;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getSignBy() {
        return signBy;
    }

    public void setSignBy(String signBy) {
        this.signBy = signBy;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Integer getAgreeFlag() {
        return agreeFlag;
    }

    public void setAgreeFlag(Integer agreeFlag) {
        this.agreeFlag = agreeFlag;
    }

    public Integer getVoidFlag() {
        return voidFlag;
    }

    public void setVoidFlag(Integer voidFlag) {
        this.voidFlag = voidFlag;
    }

    public Date getSignTime() {
        return signTime;
    }

    public void setSignTime(Date signTime) {
        this.signTime = signTime;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
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
}
