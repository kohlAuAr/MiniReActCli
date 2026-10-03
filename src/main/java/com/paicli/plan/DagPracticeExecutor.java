package com.paicli.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DagPracticeExecutor {

    public enum Status {
        PENDING, RUNNING, COMPLETED, FAILED, BLOCKED
    }

    public record Result(Map<String, Status> statuses, Map<String, String> results,
                         List<String> executionOrder) {}

    public Result run(List<DagTask> tasks) {
        Map<String, Status> statuses = new LinkedHashMap<>();
        Map<String, String> results = new LinkedHashMap<>();
        List<String> executionOrder = new ArrayList<>();
        for (DagTask task : tasks) {
            if (statuses.put(task.id(), Status.PENDING) != null) {
                throw new IllegalArgumentException("重复任务 ID: " + task.id());
            }
        }
        for (DagTask task : tasks) {
            for (String dependency : task.dependencies()) {
                if (!statuses.containsKey(dependency)) {
                    throw new IllegalArgumentException("未知依赖: " + dependency);
                }
            }
        }

        while (statuses.containsValue(Status.PENDING)) {
            boolean progressed = false;
            for (DagTask task : tasks) {
                if (statuses.get(task.id()) != Status.PENDING) {
                    continue;
                }
                for (String dependency : task.dependencies()) {
                    Status dependencyStatus = statuses.get(dependency);
                    if (dependencyStatus == Status.FAILED || dependencyStatus == Status.BLOCKED) {
                        statuses.put(task.id(), Status.BLOCKED);
                        progressed = true;
                        break;
                    }
                }
            }

            List<DagTask> readyTasks = findReadyTasks(tasks, statuses);
            for (DagTask task : readyTasks) {
                Map<String, String> dependencyResults = new LinkedHashMap<>();
                for (String dependency : task.dependencies()) {
                    dependencyResults.put(dependency, results.get(dependency));
                }
                statuses.put(task.id(), Status.RUNNING);
                executionOrder.add(task.id());
                try {
                    results.put(task.id(), task.action().execute(dependencyResults));
                    statuses.put(task.id(), Status.COMPLETED);
                } catch (Exception e) {
                    results.put(task.id(), "任务执行失败: " + e.getMessage());
                    statuses.put(task.id(), Status.FAILED);
                }
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalStateException("任务依赖存在循环，无法继续执行");
            }
        }
        return new Result(statuses, results, executionOrder);
    }

    private List<DagTask> findReadyTasks(List<DagTask> tasks, Map<String, Status> statuses) {
        // 你实现整个方法：只选 PENDING 且所有依赖都是 COMPLETED 的任务。
        // 无依赖的 PENDING 任务可以运行；已经完成、失败、阻塞的任务不能重复运行。
        // 返回本轮可运行的任务列表，不在这里执行任务或修改状态。
        throw new UnsupportedOperationException("依赖调度方法等待你实现");
    }
}
