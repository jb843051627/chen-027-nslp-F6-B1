package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fc.v2.mapper.auto.TNslpTaskItemMapper;
import com.fc.v2.model.auto.TNslpTaskItem;
import com.fc.v2.model.custom.TaskDispatchResult;
import com.fc.v2.service.ITNslpTaskItemDispatchService;

/**
 * 老派单服务统一到夜间走表口径后的测试：
 * 窗外不动单、空单安全不越界、只收还搁着的（含空情形存量行）、执行直接走新派单那一趟。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpTaskItemServiceImplTest {

    @Mock
    private TNslpTaskItemMapper itemMapper;
    @Mock
    private ITNslpTaskItemDispatchService dispatchService;
    @InjectMocks
    private TNslpTaskItemServiceImpl service;

    private List<TNslpTaskItem> store;

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TNslpTaskItem row(long id, String dueAt, Integer status) {
        TNslpTaskItem r = new TNslpTaskItem();
        r.setId(Long.valueOf(id));
        r.setItemNo("IT" + id);
        r.setDueAt(dueAt == null ? null : d(dueAt));
        r.setAmount((BigDecimal) null);
        r.setStatus(status);
        r.setDelFlag(Integer.valueOf(0));
        return r;
    }

    @BeforeEach
    public void setUp() {
        store = new ArrayList<TNslpTaskItem>();
        // 库桩把搁着的行都递上来，情形/届期的取舍仍由口径在内存里断（与夜间走表同源）
        when(itemMapper.selectList(any())).thenAnswer(inv -> new ArrayList<TNslpTaskItem>(store));
    }

    @Test
    public void outsideWindow_returnsEmpty_andTouchesNothing() {
        store.add(row(1L, "2026-10-01 00:00:00", 0));
        // 别的时段（非 02:00–05:00）触发：一行也不给，更不会去动数据
        assertTrue(service.listDue(d("2026-10-06 01:30:00")).isEmpty());
        assertTrue(service.listDue(d("2026-10-06 06:00:00")).isEmpty());
        assertTrue(service.listDue(null).isEmpty());
    }

    @Test
    public void emptySheet_isSafe_noIndexOutOfBounds() {
        // 老实现一头撞在 due.get(0) 上；统一后空单是合法结果，安全回空表
        List<TNslpTaskItem> due = service.listDue(d("2026-10-06 03:00:00"));
        assertTrue(due.isEmpty());
    }

    @Test
    public void listDue_onlyWaitingAndDue_blankStatusLegacyIncluded() {
        TNslpTaskItem blankLegacy = row(1L, "2026-10-06 03:00:00", null);   // 空情形老行，恰好届期
        TNslpTaskItem waiting = row(2L, "2026-10-01 00:00:00", 0);          // 显式搁着、已届期
        TNslpTaskItem done = row(3L, "2026-10-01 00:00:00", 1);             // 已发出：不再参与
        TNslpTaskItem fail = row(4L, "2026-10-01 00:00:00", 2);             // 发不成：不再参与
        TNslpTaskItem future = row(5L, "2026-12-01 00:00:00", 0);           // 还没届期
        store.add(blankLegacy);
        store.add(waiting);
        store.add(done);
        store.add(fail);
        store.add(future);

        List<TNslpTaskItem> due = service.listDue(d("2026-10-06 03:00:00"));
        assertEquals(2, due.size());
        // 最届期的在前：id=2（10.01）先于 id=1（10.06）
        assertEquals(Long.valueOf(2L), due.get(0).getId());
        assertEquals(Long.valueOf(1L), due.get(1).getId());
    }

    @Test
    public void runOnce_delegatesToNightDispatch_andReturnsSentCount() {
        // 执行只走夜间走表那唯一一趟，成功条数取「发出几行」
        when(dispatchService.runOnce(any())).thenReturn(TaskDispatchResult.of(5, 4, 1));
        assertEquals(4, service.runOnce(d("2026-10-06 03:00:00")));
        verify(dispatchService).runOnce(any());
    }
}
