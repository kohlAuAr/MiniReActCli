package com.paicli.plan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.paicli.plan.DagPracticeExecutor.Status.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(2)
class DagPracticeExecutorTest {

    @Test
    void waitsForDependenciesAndPassesTheirActualResults() {
        DagTask a = new DagTask("A", List.of(), inputs -> "说明");
        DagTask b = new DagTask("B", List.of(), inputs -> "配置");
        DagTask c = new DagTask("C", List.of("A", "B"), inputs -> {
            assertEquals(Map.of("A", "说明", "B", "配置"), inputs);
            return inputs.get("A") + "|" + inputs.get("B");
        });

        DagPracticeExecutor.Result result = new DagPracticeExecutor().run(List.of(c, a, b));

        assertEquals(Set.of("A", "B"), Set.copyOf(result.executionOrder().subList(0, 2)));
        assertEquals("C", result.executionOrder().get(2));
        assertEquals("说明|配置", result.results().get("C"));
        assertEquals(Map.of("A", COMPLETED, "B", COMPLETED, "C", COMPLETED), result.statuses());
    }

    @Test
    void blocksFailedDependenciesButStillRunsIndependentTasks() {
        DagTask a = new DagTask("A", List.of(), inputs -> { throw new IOException("读取失败"); });
        DagTask b = new DagTask("B", List.of(), inputs -> "配置");
        DagTask c = new DagTask("C", List.of("A", "B"), inputs -> "不应汇总");

        DagPracticeExecutor.Result result = new DagPracticeExecutor().run(List.of(c, a, b));

        assertEquals(FAILED, result.statuses().get("A"));
        assertEquals(COMPLETED, result.statuses().get("B"));
        assertEquals(BLOCKED, result.statuses().get("C"));
        assertEquals("任务执行失败: 读取失败", result.results().get("A"));
        assertFalse(result.results().containsKey("C"));
        assertFalse(result.executionOrder().contains("C"));
    }

    @Test
    void rejectsCircularDependenciesInsteadOfWaitingForever() {
        DagTask a = new DagTask("A", List.of("B"), inputs -> "A");
        DagTask b = new DagTask("B", List.of("A"), inputs -> "B");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new DagPracticeExecutor().run(List.of(a, b)));

        assertTrue(error.getMessage().contains("循环"));
    }

    @Test
    void rejectsUnknownDependenciesBeforeExecutingTasks() {
        DagTask a = new DagTask("A", List.of(), inputs -> {
            fail("错误计划不应开始执行");
            return "A";
        });
        DagTask c = new DagTask("C", List.of("missing"), inputs -> "C");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new DagPracticeExecutor().run(List.of(a, c)));

        assertEquals("未知依赖: missing", error.getMessage());
    }
}
