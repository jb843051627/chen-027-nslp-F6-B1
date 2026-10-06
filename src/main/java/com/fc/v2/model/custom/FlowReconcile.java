package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TNslpFlowRow;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 抽行复核的回话。
 *
 * 随手抽一行复核：这一行的去处要和拨首三个数互相印证，印不上的
 * 一行一行点名，「大致相符」这种说法不认。当年南津那 19 行，就是
 * 拨上记的数与流水里数出来的数对不住，又谁也指不出落在哪一处。
 *
 * @author fuce
 */
@ApiModel(value = "FlowReconcile", description = "抽行复核回话（印不上的一行一行点名）")
public class FlowReconcile implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次码 */
    @ApiModelProperty(value = "批次码")
    private String batchNo;

    /** 拨抬头上存着的当初点数（进门那一刻点出来的） */
    @ApiModelProperty(value = "拨抬头存着的当初点数")
    private Integer storedRowNum;

    /** 流水里此刻一行一行数出来的行数 */
    @ApiModelProperty(value = "流水里数出来的行数")
    private int ledgerRowNum;

    /** 流水里数出来的收下数 */
    @ApiModelProperty(value = "收下的行数")
    private int acceptNum;

    /** 流水里数出来的退回数 */
    @ApiModelProperty(value = "退回的行数")
    private int retNum;

    /** 印得上与否：印得上为 true，印不上为 false（offenders 里一行一行点着名） */
    @ApiModelProperty(value = "印得上与否")
    private boolean consistent;

    /** 印不上的行（写法不明的、已核收的拨里还空悬未勾的，都在此点名） */
    @ApiModelProperty(value = "印不上的行")
    private List<TNslpFlowRow> offenders = new ArrayList<TNslpFlowRow>();

    public static FlowReconcile of(String batchNo, Integer storedRowNum, int ledgerRowNum,
                                   int acceptNum, int retNum, boolean consistent,
                                   List<TNslpFlowRow> offenders) {
        FlowReconcile r = new FlowReconcile();
        r.batchNo = batchNo;
        r.storedRowNum = storedRowNum;
        r.ledgerRowNum = ledgerRowNum;
        r.acceptNum = acceptNum;
        r.retNum = retNum;
        r.consistent = consistent;
        if (offenders != null) {
            r.offenders = offenders;
        }
        return r;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public Integer getStoredRowNum() {
        return storedRowNum;
    }

    public int getLedgerRowNum() {
        return ledgerRowNum;
    }

    public int getAcceptNum() {
        return acceptNum;
    }

    public int getRetNum() {
        return retNum;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public List<TNslpFlowRow> getOffenders() {
        return offenders;
    }
}
