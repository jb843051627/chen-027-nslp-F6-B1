package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 复查收尾封关报文：两名见证岗各落一笔，复测凭证那一串写回本行。
 * 两样齐了才算封上；缺哪一样由口径处一句一句点名。
 *
 * @author fuce
 */
@ApiModel(value = "CaseSeal", description = "复查收尾封关报文（见证两笔＋凭证一串）")
public class CaseSeal implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 办件主键（与件次给一样即可） */
    @ApiModelProperty(value = "办件主键")
    private Long id;

    /** 办件件次 */
    @ApiModelProperty(value = "办件件次")
    private String bizNo;

    /** 第一名见证岗落笔 */
    @ApiModelProperty(value = "第一名见证岗落笔")
    private String witnessA;

    /** 第二名见证岗落笔 */
    @ApiModelProperty(value = "第二名见证岗落笔")
    private String witnessB;

    /** 复测凭证那一串 */
    @ApiModelProperty(value = "复测凭证那一串")
    private String proofNo;

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
}
