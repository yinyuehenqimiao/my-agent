package com.myagent.workflow.service;

import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.model.WorkflowProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowProcessCompilerTest {

    private final WorkflowProcessCompiler compiler = new WorkflowProcessCompiler();

    @Test
    void compilesSameProcessToStableContent() {
        WorkflowProcess process = basic();
        var first = compiler.compile("leave", process);
        var second = compiler.compile("leave", process);
        assertEquals(first.contentHash(), second.contentHash());
        assertTrue(first.bpmnXml().contains("workflowTaskDelegate"));
        assertTrue(first.bpmnXml().contains("nodeType"));
        assertTrue(first.bpmnXml().contains("nodeVersion"));
    }

    @Test
    void configurationChangeInvalidatesContentHash() {
        WorkflowProcess first = basic();
        WorkflowProcess second = new WorkflowProcess(
                List.of(node("s", "start"), new WorkflowProcess.Node("reply", "reply", 1, Map.of("template", "已完成")), node("e", "end")),
                first.edges());
        org.junit.jupiter.api.Assertions.assertNotEquals(
                compiler.compile("leave", first).contentHash(), compiler.compile("leave", second).contentHash());
    }

    @Test
    void rejectsConditionWithoutDefaultExit() {
        WorkflowProcess process = new WorkflowProcess(
                List.of(node("s", "start"), node("c", "condition"), node("a", "end")),
                List.of(edge("1", "s", "c", null), edge("2", "c", "a", new WorkflowProcess.Condition("days", "gt", "3"))));
        assertThrows(ClientException.class, () -> compiler.compile("leave", process));
    }

    @Test
    void rejectsCyclesAndUnknownNodes() {
        WorkflowProcess cycle = new WorkflowProcess(List.of(node("s", "start"), node("e", "end")),
                List.of(edge("1", "s", "e", null), edge("2", "e", "s", null)));
        assertThrows(ClientException.class, () -> compiler.compile("leave", cycle));
        WorkflowProcess unknown = new WorkflowProcess(List.of(node("s", "start"), node("x", "script"), node("e", "end")),
                List.of(edge("1", "s", "x", null), edge("2", "x", "e", null)));
        assertThrows(ClientException.class, () -> compiler.compile("leave", unknown));
    }

    @Test
    void rejectsWritePathThatBypassesConfirmation() {
        WorkflowProcess process = new WorkflowProcess(
                List.of(node("s", "start"), node("write", "tool.execute"), node("e", "end")),
                List.of(edge("1", "s", "write", null), edge("2", "write", "e", null)));
        assertThrows(ClientException.class, () -> compiler.compile("leave", process));
    }

    private WorkflowProcess basic() { return new WorkflowProcess(List.of(node("s", "start"), node("reply", "reply"), node("e", "end")), List.of(edge("1", "s", "reply", null), edge("2", "reply", "e", null))); }
    private WorkflowProcess.Node node(String id, String type) { return new WorkflowProcess.Node(id, type, 1, Map.of()); }
    private WorkflowProcess.Edge edge(String id, String source, String target, WorkflowProcess.Condition condition) { return new WorkflowProcess.Edge(id, source, target, null, condition); }
}
