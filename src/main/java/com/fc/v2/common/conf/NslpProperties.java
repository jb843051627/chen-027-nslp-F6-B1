package com.fc.v2.common.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 区局声管岗名册相关配置。
 *
 * 每页几条由区局写在配置里（fuce.nslp.ledger-page-size），只此一处来源；
 * 没配或配得不正，按 {@link #DEFAULT_LEDGER_PAGE_SIZE} 认。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Component
@ConfigurationProperties(prefix = "fuce.nslp")
public class NslpProperties {

    /** 名册每页条数的认定默认值：配置空着、非正、离谱时按这个认 */
    public static final int DEFAULT_LEDGER_PAGE_SIZE = 10;
    /** 每页条数填过这个数算离谱，当没填 */
    public static final int LEDGER_PAGE_SIZE_ABSURD = 10000;

    /** 名册每页几条（区局在配置里写定） */
    private Integer ledgerPageSize;

    public Integer getLedgerPageSize() {
        return ledgerPageSize;
    }

    public void setLedgerPageSize(Integer ledgerPageSize) {
        this.ledgerPageSize = ledgerPageSize;
    }

    /** 唯一认法：空/非正/离谱按默认认，其余照写定的认 */
    public int effectiveLedgerPageSize() {
        if (ledgerPageSize == null || ledgerPageSize.intValue() <= 0
                || ledgerPageSize.intValue() > LEDGER_PAGE_SIZE_ABSURD) {
            return DEFAULT_LEDGER_PAGE_SIZE;
        }
        return ledgerPageSize.intValue();
    }
}
