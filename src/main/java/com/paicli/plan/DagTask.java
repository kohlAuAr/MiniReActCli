package com.paicli.plan;

import java.util.List;
import java.util.Map;

public record DagTask(String id, List<String> dependencies, Action action) {

    @FunctionalInterface
    public interface Action {
        String execute(Map<String, String> dependencyResults) throws Exception;
    }
}
