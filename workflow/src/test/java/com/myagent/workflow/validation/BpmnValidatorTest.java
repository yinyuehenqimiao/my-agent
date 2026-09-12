package com.myagent.workflow.validation;

import com.myagent.workflow.validation.BpmnValidator.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 阶段 3 BPMN 安全校验测试：节点白名单、禁止脚本/Java/HTTP、表达式白名单、发布前检查。 */
class BpmnValidatorTest {

    private final BpmnValidator validator = new BpmnValidator();

    private String bpmn(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns:flowable=\"http://flowable.org/bpmn\" xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<process id=\"p1\" isExecutable=\"true\">" + body + "</process></definitions>";
    }

    private String startEnd() {
        return "<startEvent id=\"s\"/><sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"e\"/>"
                + "<endEvent id=\"e\"/>";
    }

    @Test
    void legalBpmnPassesSyntaxAndPublish() {
        String xml = bpmn(startEnd());
        assertTrue(validator.validateSyntaxAndNodes(xml).valid());
        assertTrue(validator.validateForPublish(xml).valid());
    }

    @Test
    void scriptTaskRejected() {
        String xml = bpmn("<startEvent id=\"s\"/><scriptTask id=\"st\" scriptFormat=\"groovy\"><script>1+1</script></scriptTask>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"st\"/><sequenceFlow id=\"f2\" sourceRef=\"st\" targetRef=\"e\"/><endEvent id=\"e\"/>");
        ValidationResult r = validator.validateSyntaxAndNodes(xml);
        assertFalse(r.valid());
        assertTrue(r.reason().contains("scriptTask"));
    }

    @Test
    void javaClassDelegateRejected() {
        String xml = bpmn("<startEvent id=\"s\"/><serviceTask id=\"t\" flowable:class=\"com.evil.Task\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"t\"/><sequenceFlow id=\"f2\" sourceRef=\"t\" targetRef=\"e\"/><endEvent id=\"e\"/>");
        ValidationResult r = validator.validateSyntaxAndNodes(xml);
        assertFalse(r.valid());
        assertTrue(r.reason().contains("flowable:class"));
    }

    @Test
    void safeServiceTaskTypeAllowed() {
        String xml = bpmn("<startEvent id=\"s\"/><serviceTask id=\"t\" flowable:type=\"tool\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"t\"/><sequenceFlow id=\"f2\" sourceRef=\"t\" targetRef=\"e\"/><endEvent id=\"e\"/>");
        assertTrue(validator.validateSyntaxAndNodes(xml).valid());
    }

    @Test
    void forbiddenNodeRejected() {
        String xml = bpmn("<startEvent id=\"s\"/><callActivity id=\"c\" calledElement=\"other\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"c\"/><sequenceFlow id=\"f2\" sourceRef=\"c\" targetRef=\"e\"/><endEvent id=\"e\"/>");
        assertFalse(validator.validateSyntaxAndNodes(xml).valid());
    }

    @Test
    void conditionWhitelistFieldAllowed() {
        String xml = bpmn("<startEvent id=\"s\"/>"
                + "<exclusiveGateway id=\"g\"/><sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"g\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"e\" conditionExpression=\"${days &gt; 3}\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"g\" targetRef=\"e2\"/><endEvent id=\"e\"/><endEvent id=\"e2\"/>");
        assertTrue(validator.validateSyntaxAndNodes(xml).valid());
    }

    @Test
    void conditionNonWhitelistFieldRejected() {
        String xml = bpmn("<startEvent id=\"s\"/>"
                + "<exclusiveGateway id=\"g\"/><sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"g\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"e\" conditionExpression=\"${password == 'x'}\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"g\" targetRef=\"e2\"/><endEvent id=\"e\"/><endEvent id=\"e2\"/>");
        ValidationResult r = validator.validateSyntaxAndNodes(xml);
        assertFalse(r.valid());
        assertTrue(r.reason().contains("password"));
    }

    @Test
    void publishRequiresStartAndEnd() {
        String xml = bpmn("<startEvent id=\"s\"/>");
        ValidationResult r = validator.validateForPublish(xml);
        assertFalse(r.valid());
        assertTrue(r.reason().contains("结束"));
    }

    @Test
    void publishDetectsUnreachableNode() {
        // endEvent e2 无入边
        String xml = bpmn("<startEvent id=\"s\"/><sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"e\"/><endEvent id=\"e\"/>"
                + "<endEvent id=\"e2\"/>");
        ValidationResult r = validator.validateForPublish(xml);
        assertFalse(r.valid());
        assertTrue(r.reason().contains("不可达"));
    }

    @Test
    void emptyBpmnRejected() {
        assertFalse(validator.validateSyntaxAndNodes("").valid());
        assertFalse(validator.validateSyntaxAndNodes(null).valid());
    }
}
