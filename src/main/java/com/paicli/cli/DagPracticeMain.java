package com.paicli.cli;

import com.paicli.plan.DagPracticeExecutor;
import com.paicli.plan.DagTask;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DagPracticeMain {

    public static void main(String[] args) {
        DagTask a = new DagTask("A", List.of(), inputs ->
                Files.readString(Path.of("notes/dag-demo/README.md"), StandardCharsets.UTF_8));
        DagTask b = new DagTask("B", List.of(), inputs ->
                Files.readString(Path.of("notes/dag-demo/pom.xml"), StandardCharsets.UTF_8));
        DagTask c = new DagTask("C", List.of("A", "B"), inputs ->
                "项目说明：\n" + inputs.get("A") + "\n构建配置：\n" + inputs.get("B"));

        // 故意把 C 放在最前面：运行顺序必须由依赖决定。
        DagPracticeExecutor.Result result = new DagPracticeExecutor().run(List.of(c, a, b));
        System.out.println("实际执行顺序：" + result.executionOrder());
        System.out.println("任务状态：" + result.statuses());
        System.out.println("C 的汇总结果：\n" + result.results().get("C"));
    }
}
