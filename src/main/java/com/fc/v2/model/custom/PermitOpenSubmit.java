package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 签批单立单报文。
 *
 * 单只许从点位底册的记录里带出来：报文只点一处点位底册行，单号没填由后端配；
 * 前端不留自己新建的口子，也不许手填关次、应签人数。作业单位打个电话口头
 * 说一声不算立单——没有这一条报文、主档里就不落行。
 *
 * @author fuce
 */
@ApiModel(value = "PermitOpenSubmit", description = "签批单立单报文（只从点位底册带出）")
public class PermitOpenSubmit implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 带出本单的点位底册行主键（一处点位、一次夜间连续施工只摊上一张） */
    @ApiModelProperty(value = "点位底册行主键", required = true)
    private Long pointId;

    /** 签批单单号；没填由后端按点位码配 */
    @ApiModelProperty(value = "签批单单号（没填由后端配）")
    private String billNo;

    /** 立单人（登录岗，没带由服务端按当前登录岗补） */
    @ApiModelProperty(value = "立单人")
    private String operator;

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
