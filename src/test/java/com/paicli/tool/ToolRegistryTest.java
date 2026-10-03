package com.paicli.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @TempDir
    Path projectRoot;

    @Test
    void shouldReadFileInsideProjectRoot() throws Exception {
        Files.writeString(
                projectRoot.resolve("hello.txt"),
                "hello",
                StandardCharsets.UTF_8
        );

        ToolRegistry registry = new ToolRegistry(projectRoot);

        String result = registry.executeTool(
                "read_file",
                "{\"path\":\"hello.txt\"}"
        );

        assertEquals("hello", result);
    }

    @Test
    void shouldWriteAndReadFileInsideProjectRoot() {
        ToolRegistry registry = new ToolRegistry(projectRoot);

        String writeResult = registry.executeTool(
                "write_file",
                "{\"path\":\"notes/result.txt\",\"content\":\"done\"}"
        );

        String readResult = registry.executeTool(
                "read_file",
                "{\"path\":\"notes/result.txt\"}"
        );

        assertTrue(writeResult.startsWith("文件已写入:"));
        assertEquals("done", readResult);
    }

    @Test
    void shouldRejectPathOutsideProjectRoot() {
        ToolRegistry registry = new ToolRegistry(projectRoot);

        String result = registry.executeTool(
                "read_file",
                "{\"path\":\"../outside.txt\"}"
        );

        assertTrue(result.contains("路径不能超出项目根目录"));
    }

    @Test
    void shouldExposeThreeToolDefinitions() {
        ToolRegistry registry = new ToolRegistry(projectRoot);

        assertEquals(3, registry.getToolDefinitions().size());
    }
}
