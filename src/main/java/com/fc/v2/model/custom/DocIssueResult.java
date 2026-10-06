package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fc.v2.model.auto.TNslpDocIssue;
import com.fc.v2.model.auto.TNslpDocTpl;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一纸文书出文的回话。
 *
 * 照哪一版取值、还差几项、缺的是哪几项，都在这一个回话里一处给出；打印环节只照它，
 * 不许再算第二遍。出不来时 issued=false 且 missing 一项一项列名，绝不发空白纸。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "DocIssueResult", description = "文书出文回话（取值唯一出处）")
public class DocIssueResult implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 出得来为 true；有缺项/无在位版为 false（这一纸张不发） */
    @ApiModelProperty(value = "出得来否")
    private boolean issued;

    /** 开口的那一版（撤版不选；出不来为 null） */
    @ApiModelProperty(value = "开口的那一版")
    private TNslpDocTpl tpl;

    /** 落档的那一条出文记录（成功才有） */
    @ApiModelProperty(value = "出文留档")
    private TNslpDocIssue issue;

    /** 应填项数（取当版） */
    @ApiModelProperty(value = "应填项数")
    private Integer varNum;

    /** 已取到值的项数（服务端数） */
    @ApiModelProperty(value = "已取到值的项数")
    private Integer fillNum;

    /** 还差几项（服务端唯一轧法） */
    @ApiModelProperty(value = "还差几项")
    private Integer missNum;

    /** 取不到值的应填项名目，一项一行（空着才出得来） */
    @ApiModelProperty(value = "缺项名目（一项一行）")
    private List<String> missing = new ArrayList<String>();

    /** 出不来/挡回的逐句原因 */
    @ApiModelProperty(value = "挡回原因")
    private String error;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "出文那一刻")
    private Date issueTime;

    public static DocIssueResult blocked(String error, Date at) {
        DocIssueResult r = new DocIssueResult();
        r.issued = false;
        r.error = error;
        r.issueTime = at;
        return r;
    }

    public static DocIssueResult missing(TNslpDocTpl tpl, Date at, int fillNum, List<String> missing) {
        DocIssueResult r = new DocIssueResult();
        r.issued = false;
        r.tpl = tpl;
        r.varNum = tpl.getVarNum();
        r.fillNum = Integer.valueOf(fillNum);
        r.missNum = Integer.valueOf(com.fc.v2.service.support.DocTplRules.miss(tpl.getVarNum(), fillNum));
        r.missing = missing;
        r.issueTime = at;
        return r;
    }

    public static DocIssueResult issued(TNslpDocTpl tpl, TNslpDocIssue issue, Date at) {
        DocIssueResult r = new DocIssueResult();
        r.issued = true;
        r.tpl = tpl;
        r.issue = issue;
        r.varNum = issue.getVarNum();
        r.fillNum = issue.getFillNum();
        r.missNum = issue.getMissNum();
        r.issueTime = at;
        return r;
    }

    public boolean isIssued() {
        return issued;
    }

    public void setIssued(boolean issued) {
        this.issued = issued;
    }

    public TNslpDocTpl getTpl() {
        return tpl;
    }

    public void setTpl(TNslpDocTpl tpl) {
        this.tpl = tpl;
    }

    public TNslpDocIssue getIssue() {
        return issue;
    }

    public void setIssue(TNslpDocIssue issue) {
        this.issue = issue;
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

    public List<String> getMissing() {
        return missing;
    }

    public void setMissing(List<String> missing) {
        this.missing = missing;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Date getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(Date issueTime) {
        this.issueTime = issueTime;
    }
}
