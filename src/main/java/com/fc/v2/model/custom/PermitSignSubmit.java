package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 签批落笔报文（一次落笔只有这一个入口）。
 *
 * 报文只递两样：哪张单、这一位的原话。当前停在哪一关、这是第几轮、已到几人，
 * 一律由服务端从留痕那串点出来——reportNode 即便写了关次，也只作参考，不写库、
 * 不定关；不另起单、不改旧笔、不开追补签署的旁路。认可与否由原话放出来，
 * 报文里没有可勾选的可／不可。
 *
 * @author fuce
 */
@ApiModel(value = "PermitSignSubmit", description = "签批落笔报文（只带单号与原话，关次服务端认）")
public class PermitSignSubmit implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 签批单主键 */
    @ApiModelProperty(value = "签批单主键", required = true)
    private Long id;

    /** 这一位的落笔原话（逐字为「不可」才按不认可记账） */
    @ApiModelProperty(value = "落笔原话", required = true)
    private String opinion;

    /** 报文自带的关次：只作参考，关次一律以服务端点出来的为准 */
    @ApiModelProperty(value = "报文自带关次（只作参考，不采信）")
    private Integer reportNode;

    /** 落笔人（登录岗，没带由服务端按当前登录岗补） */
    @ApiModelProperty(value = "落笔人")
    private String signBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Integer getReportNode() {
        return reportNode;
    }

    public void setReportNode(Integer reportNode) {
        this.reportNode = reportNode;
    }

    public String getSignBy() {
        return signBy;
    }

    public void setSignBy(String signBy) {
        this.signBy = signBy;
    }
}
