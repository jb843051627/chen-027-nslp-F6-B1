package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TNslpFlowBatch;
import com.fc.v2.model.auto.TNslpFlowRow;
import com.fc.v2.service.support.FlowRowRules;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一张回执。
 *
 * 回执上只写三个数：本拨行数、收下的行数、退回的行数；已核收的拨，
 * 后两个加起来等于第一个。三个数一律回到流水里一行一行数出来——核收那一趟、
 * 事后页面转述，都拿本对象说话，两处出自同一次计算，才谈得上对得住。
 * 切不动的行、报文与底册不合的行，各列一份清单随回执递出。
 *
 * @author fuce
 */
@ApiModel(value = "FlowReceipt", description = "核收回执（三个数出自同一次计算）")
public class FlowReceipt implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 拨落地情形：已核收 */
    public static final int OUTCOME_ACCEPTED = 0;
    /** 拨落地情形：超顶整批未收（点行数那一步就超了写定的顶，一行不落） */
    public static final int OUTCOME_OVER_CAP = 1;

    /** 批次码 */
    @ApiModelProperty(value = "批次码")
    private String batchNo;

    /** 拨落地情形 0已核收 1超顶整批未收 */
    @ApiModelProperty(value = "拨落地情形 0已核收 1超顶整批未收")
    private int outcome;

    /** 情形回话（重复到达的挂「只照面」一句） */
    @ApiModelProperty(value = "情形回话")
    private String outcomeText;

    /** 同一批次码第二回到达：只照面，没动第一遍落地的任何一行 */
    @ApiModelProperty(value = "是否重复到达只照面")
    private boolean duplicateArrival;

    /** 写定的顶（抬头那一格认定后的值） */
    @ApiModelProperty(value = "写定的顶")
    private Integer capNum;

    /** 三个数之一：本拨行数 */
    @ApiModelProperty(value = "本拨行数")
    private int rowNum;

    /** 三个数之二：收下的行数 */
    @ApiModelProperty(value = "收下的行数")
    private int acceptNum;

    /** 三个数之三：退回的行数 */
    @ApiModelProperty(value = "退回的行数")
    private int retNum;

    /** 本拨切法 */
    @ApiModelProperty(value = "本拨切法")
    private String parseKind;

    /** 逐行去处（三个数就是从这份明细里一行一行数出来的） */
    @ApiModelProperty(value = "逐行去处")
    private List<RowOutcome> rows = new ArrayList<RowOutcome>();

    /** 切不动的行次（单独标出，递给回核看的人） */
    @ApiModelProperty(value = "切不动的行次")
    private List<Integer> parseFailedRowNos = new ArrayList<Integer>();

    /** 报文与底册不合的行次（以底册为准，单列出来） */
    @ApiModelProperty(value = "底册不合的行次")
    private List<Integer> regDiffRowNos = new ArrayList<Integer>();

    /**
     * 唯一装配口：核收那一趟与事后页面转述都走这里，三个数从递进来的行里
     * 一行一行数出来，不许另算一遍。超顶整批未收的拨一行未落，行数照当初
     * 点出来的那个数记，收下、退回都是 0。
     */
    public static FlowReceipt of(TNslpFlowBatch batch, List<TNslpFlowRow> rows, boolean duplicateArrival) {
        FlowReceipt r = new FlowReceipt();
        r.batchNo = batch.getBatchNo();
        r.capNum = batch.getCapNum();
        r.parseKind = batch.getParseKind();
        r.duplicateArrival = duplicateArrival;
        boolean overCap = batch.getStatus() != null && batch.getStatus() == FlowRowRules.BATCH_OVER_CAP;
        r.outcome = overCap ? OUTCOME_OVER_CAP : OUTCOME_ACCEPTED;
        r.outcomeText = (overCap ? "超顶整批未收" : "已核收") + (duplicateArrival ? "（重复到达，只照面）" : "");
        int accept = 0;
        int ret = 0;
        if (rows != null) {
            for (TNslpFlowRow row : rows) {
                RowOutcome o = new RowOutcome();
                o.rowNo = row.getRowNo();
                o.itemCode = row.getItemCode();
                o.status = row.getStatus();
                o.statusText = FlowRowRules.statusText(row.getStatus());
                o.retReason = row.getRetReason();
                o.parseFlag = row.getParseFlag();
                o.regDiffFlag = row.getRegDiffFlag();
                o.pointName = row.getPointName();
                o.zoneNo = row.getZoneNo();
                r.rows.add(o);
                if (row.getStatus() != null && row.getStatus() == FlowRowRules.STATUS_ACCEPTED) {
                    accept++;
                }
                if (row.getStatus() != null && row.getStatus() == FlowRowRules.STATUS_RETURNED) {
                    ret++;
                }
                if (row.getParseFlag() != null && row.getParseFlag() == 1) {
                    r.parseFailedRowNos.add(row.getRowNo());
                }
                if (row.getRegDiffFlag() != null && row.getRegDiffFlag() == 1) {
                    r.regDiffRowNos.add(row.getRowNo());
                }
            }
        }
        r.rowNum = overCap ? (batch.getRowNum() == null ? 0 : batch.getRowNum()) : r.rows.size();
        r.acceptNum = accept;
        r.retNum = ret;
        return r;
    }

    /** 一行去处（回执明细里的一格） */
    @ApiModel(value = "FlowReceiptRow", description = "回执逐行去处")
    public static class RowOutcome implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        /** 原报行次（系统排的；退回重报的仍显示当初那一个） */
        @ApiModelProperty(value = "原报行次")
        private Integer rowNo;

        /** 被对上的点位码 */
        @ApiModelProperty(value = "点位码")
        private String itemCode;

        /** 行落地情形 0未勾 1收下 2退回 */
        @ApiModelProperty(value = "行落地情形")
        private Integer status;

        /** 去处回话：未勾／收下／退回 */
        @ApiModelProperty(value = "去处回话")
        private String statusText;

        /** 退回理由（吃了哪一条）；未退空白 */
        @ApiModelProperty(value = "退回理由")
        private String retReason;

        /** 解析情形 0切得动 1切不动 */
        @ApiModelProperty(value = "解析情形")
        private Integer parseFlag;

        /** 底册比对 0相符或未报 1不合 */
        @ApiModelProperty(value = "底册比对")
        private Integer regDiffFlag;

        /** 点位题名（以底册带出为准） */
        @ApiModelProperty(value = "点位题名")
        private String pointName;

        /** 区册代号（以底册带出为准） */
        @ApiModelProperty(value = "区册代号")
        private String zoneNo;

        public Integer getRowNo() {
            return rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public Integer getStatus() {
            return status;
        }

        public String getStatusText() {
            return statusText;
        }

        public String getRetReason() {
            return retReason;
        }

        public Integer getParseFlag() {
            return parseFlag;
        }

        public Integer getRegDiffFlag() {
            return regDiffFlag;
        }

        public String getPointName() {
            return pointName;
        }

        public String getZoneNo() {
            return zoneNo;
        }
    }

    public String getBatchNo() {
        return batchNo;
    }

    public int getOutcome() {
        return outcome;
    }

    public String getOutcomeText() {
        return outcomeText;
    }

    public boolean isDuplicateArrival() {
        return duplicateArrival;
    }

    public Integer getCapNum() {
        return capNum;
    }

    public int getRowNum() {
        return rowNum;
    }

    public int getAcceptNum() {
        return acceptNum;
    }

    public int getRetNum() {
        return retNum;
    }

    public String getParseKind() {
        return parseKind;
    }

    public List<RowOutcome> getRows() {
        return rows;
    }

    public List<Integer> getParseFailedRowNos() {
        return parseFailedRowNos;
    }

    public List<Integer> getRegDiffRowNos() {
        return regDiffRowNos;
    }
}
