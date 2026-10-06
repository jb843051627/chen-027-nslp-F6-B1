package com.fc.v2.model.custom;

import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一纸文书出文的进门形状。
 *
 * 取值只由服务端一处给出：应填项的原值随报文递进来，缺不缺、照哪一版、还差几项，
 * 全在服务端口径处算，页面不许再算第二遍。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "DocIssueSubmit", description = "文书出文提交")
public class DocIssueSubmit implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 文种名（超标告知·责令限期改正·复查结论） */
    @ApiModelProperty(value = "文种名", required = true)
    private String tplName;

    /** 所对点位主键 */
    @ApiModelProperty(value = "所对点位主键", required = true)
    private Long pointId;

    /** 出文那一刻（空着由服务端取当下） */
    @ApiModelProperty(value = "出文那一刻（空取当下）")
    private java.util.Date issueTime;

    /** 第 3 项起的应填项取值（键为项序号，从 3 开始）；前两项由点位行带出 */
    @ApiModelProperty(value = "第3项起的应填项取值（键=项序号）")
    private Map<Integer, String> extraValues;

    /** 出文落笔人 */
    @ApiModelProperty(value = "出文落笔人", required = true)
    private String operator;

    public String getTplName() {
        return tplName;
    }

    public void setTplName(String tplName) {
        this.tplName = tplName;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public java.util.Date getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(java.util.Date issueTime) {
        this.issueTime = issueTime;
    }

    public Map<Integer, String> getExtraValues() {
        return extraValues;
    }

    public void setExtraValues(Map<Integer, String> extraValues) {
        this.extraValues = extraValues;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }
}
