package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * 名册摆法纯口径：全等/前缀、入册分桶、分层可见、留名。
 */
public class LedgerRulesTest {

    @Test
    public void exactOrPrefix() {
        assertTrue(LedgerRules.matchesExactOrPrefix("QN001", "QN"));   // 前缀
        assertTrue(LedgerRules.matchesExactOrPrefix("QN", "QN"));      // 全等
        assertTrue(LedgerRules.matchesExactOrPrefix("QN001", null));   // 不限
        assertFalse(LedgerRules.matchesExactOrPrefix(null, "QN"));     // 字段空不凑
        assertFalse(LedgerRules.matchesExactOrPrefix("JP001", "QN"));  // 不打头
    }

    @Test
    public void intakeBuckets() {
        assertEquals(LedgerRules.BUCKET_LIVE, LedgerRules.intakeBucket(0, 0));
        assertEquals(LedgerRules.BUCKET_LIVE, LedgerRules.intakeBucket(1, 0));
        // 旧行空步归在走，不当已压卡
        assertEquals(LedgerRules.BUCKET_LIVE, LedgerRules.intakeBucket(null, 0));
        assertEquals(LedgerRules.BUCKET_SEALED, LedgerRules.intakeBucket(2, 0));
        assertEquals(LedgerRules.BUCKET_STOPPED, LedgerRules.intakeBucket(0, 1));
        // 压卡优先于叫停
        assertEquals(LedgerRules.BUCKET_SEALED, LedgerRules.intakeBucket(2, 1));
    }

    @Test
    public void visibilityByLayerAndBureau() {
        assertTrue(LedgerRules.visibleToLayer("甲层", "甲层"));
        assertFalse(LedgerRules.visibleToLayer("乙层", "甲层"));
        assertFalse(LedgerRules.visibleToLayer(null, "甲层"));

        assertTrue(LedgerRules.visibleToBureau("QN00", Arrays.asList("QN00", "QN02")));
        assertFalse(LedgerRules.visibleToBureau("QN99", Arrays.asList("QN00")));
        // 划归全集空着，一行也看不见，不拿邻层凑
        assertFalse(LedgerRules.visibleToBureau("QN00", java.util.Collections.<String>emptyList()));
        assertFalse(LedgerRules.visibleToBureau("QN00", null));
    }

    @Test
    public void attributionNeedsAtLeastOne() {
        assertTrue(LedgerRules.attributed("建档岗甲", null));
        assertTrue(LedgerRules.attributed(null, "建档岗乙"));
        // 两处都空才按没办完
        assertFalse(LedgerRules.attributed(null, null));
        assertFalse(LedgerRules.attributed("  ", ""));
    }
}
