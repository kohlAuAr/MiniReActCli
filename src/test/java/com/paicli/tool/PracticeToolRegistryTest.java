package com.paicli.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeToolRegistryTest {

    @TempDir
    Path projectRoot;

    @Test
    void readsRealFileUsingJsonPathArgument() throws Exception {
        Files.writeString(projectRoot.resolve("demo.txt"), "TODO: 添加登录校验", StandardCharsets.UTF_8);
        PracticeToolRegistry registry = new PracticeToolRegistry(projectRoot);

        assertEquals("TODO: 添加登录校验", registry.executeTool("read_file", "{\"path\":\"demo.txt\"}"));
    }

    @Test
    void returnsUnknownToolInsteadOfTryingToExecuteIt() {
        PracticeToolRegistry registry = new PracticeToolRegistry(projectRoot);

        assertEquals("未知工具：missing_tool", registry.executeTool("missing_tool", "{}"));
    }

    @Test
    void handlesInvalidJsonAndNonObjectArguments() {
        PracticeToolRegistry registry = new PracticeToolRegistry(projectRoot);

        assertTrue(registry.executeTool("read_file", "{bad json").startsWith("工具执行失败: "));
        assertEquals("工具参数必须是JSON对象", registry.executeTool("read_file", "[]"));
    }

    @Test
    void returnsFileFailureAsToolResult() {
        PracticeToolRegistry registry = new PracticeToolRegistry(projectRoot);

        assertTrue(registry.executeTool("read_file", "{\"path\":\"missing.txt\"}")
                .startsWith("工具执行失败: "));
    }

    @Test
    void keepsPathCheckInTheRegisteredExecutor() {
        PracticeToolRegistry registry = new PracticeToolRegistry(projectRoot);

        assertTrue(registry.executeTool("read_file", "{\"path\":\"../outside.txt\"}")
                .startsWith("工具执行失败: 路径不能超出项目根目录"));
    }

    @Test
    void writesRealFileUsingPathAndContentArguments() throws Exception {
        Path file = projectRoot.resolve("demo.txt");
        Files.writeString(file, "原内容", StandardCharsets.UTF_8);
        PracticeToolRegistry registry = new PracticeToolRegistry(projectRoot);

        assertEquals("文件已写入: demo.txt",
                registry.executeTool("write_file", "{\"path\":\"demo.txt\",\"content\":\"你好\"}"));
        assertEquals("你好", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void rejectsWriteOutsideProjectRoot() throws Exception {
        Path root = Files.createDirectory(projectRoot.resolve("workspace"));
        PracticeToolRegistry registry = new PracticeToolRegistry(root);

        assertTrue(registry.executeTool("write_file", "{\"path\":\"../outside.txt\",\"content\":\"你好\"}")
                .startsWith("工具执行失败: 路径不能超出项目根目录"));
        assertTrue(Files.notExists(projectRoot.resolve("outside.txt")));
    }
}
