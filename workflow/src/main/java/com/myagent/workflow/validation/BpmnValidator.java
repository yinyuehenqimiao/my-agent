package com.myagent.workflow.validation;

import com.myagent.framework.exception.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.HashSet;
import java.util.Set;

/**
 * BPMN 安全校验器：节点白名单 + 禁止脚本/Java/HTTP + 表达式白名单 + 发布前检查。
 * <p>
 * 第一版仅允许：startEvent、endEvent、serviceTask（工具/校验/检索/模板）、userTask（确认/审批）、exclusiveGateway、sequenceFlow。
 * 禁止：scriptTask、任意 Java 类（flowable:class/expression/delegateExpression）、脚本、动态 SQL、任意 HTTP 节点。
 * 条件表达式使用白名单字段与操作符。
 */
@Slf4j
@Component
public class BpmnValidator {

    /** 允许的节点元素标签 */
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "startEvent", "endEvent", "serviceTask", "userTask", "exclusiveGateway",
            "sequenceFlow", "parallelGateway", "boundaryEvent", "subProcess");
    /** 绝对禁止的节点 */
    private static final Set<String> FORBIDDEN_TAGS = Set.of(
            "scriptTask", "sendTask", "receiveTask", "manualTask", "businessRuleTask",
            "callActivity", "transaction");
    /** 条件表达式允许的字段（流程变量白名单） */
    private static final Set<String> EXPR_ALLOWED_FIELDS = Set.of(
            "days", "leaveType", "status", "year", "userId", "department", "businessRole",
            "approved", "enough", "conflict", "workingday");
    /** 条件表达式允许的操作符 */
    private static final Set<String> EXPR_ALLOWED_OPS = Set.of(
            "==", "!=", ">", ">=", "<", "<=", "&&", "||", "!", "true", "false",
            "and", "or", "not", "eq", "ne", "gt", "ge", "lt", "le");
    /** 节点数上限 */
    private static final int NODE_LIMIT = 200;

    /** 校验结果 */
    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }

    /** 完整校验（含发布前检查） */
    public ValidationResult validateForPublish(String bpmnXml) {
        ValidationResult base = validateSyntaxAndNodes(bpmnXml);
        if (!base.valid()) return base;
        Document doc = parse(bpmnXml);
        if (doc == null) return ValidationResult.fail("BPMN XML 解析失败");
        // 开始/结束节点
        if (doc.getElementsByTagName("startEvent").getLength() == 0)
            return ValidationResult.fail("缺少开始节点");
        if (doc.getElementsByTagName("endEvent").getLength() == 0)
            return ValidationResult.fail("缺少结束节点");
        // 节点数上限
        int nodeCount = countBpmnNodes(doc);
        if (nodeCount > NODE_LIMIT)
            return ValidationResult.fail("节点数超过上限 " + NODE_LIMIT);
        // 不可达节点检查（粗粒度：无入边的非开始节点视为不可达）
        ValidationResult reachability = checkReachability(doc);
        if (!reachability.valid()) return reachability;
        return ValidationResult.ok();
    }

    /** 语法与节点白名单校验（保存草稿时也用） */
    public ValidationResult validateSyntaxAndNodes(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) return ValidationResult.fail("BPMN 内容为空");
        Document doc = parse(bpmnXml);
        if (doc == null) return ValidationResult.fail("BPMN XML 解析失败");
        NodeList children = doc.getDocumentElement().getChildNodes();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element el)) continue;
            String tag = el.getTagName();
            if (tag.equals("process")) {
                ValidationResult r = validateProcess(el);
                if (!r.valid()) return r;
            } else if (!isMetaTag(tag)) {
                return ValidationResult.fail("不允许的顶层元素：" + tag);
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateProcess(Element process) {
        NodeList nodes = process.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element el)) continue;
            String tag = el.getTagName();
            if (FORBIDDEN_TAGS.contains(tag))
                return ValidationResult.fail("禁止的节点类型：" + tag);
            if (!ALLOWED_TAGS.contains(tag))
                return ValidationResult.fail("不在白名单的节点：" + tag);
            // 禁止 Java 类/脚本/表达式委托
            ValidationResult r = checkNoJavaOrScript(el);
            if (!r.valid()) return r;
            // 条件表达式白名单
            if (tag.equals("sequenceFlow")) {
                r = checkCondition(el);
                if (!r.valid()) return r;
            }
        }
        return ValidationResult.ok();
    }

    private ValidationResult checkNoJavaOrScript(Element el) {
        // 白名单中央委托：平台唯一的 WorkflowTaskDelegate（class 或 delegateExpression 指向该 bean），按节点分发到 SPI；禁止任意其他
        java.util.Set<String> WHITELISTED_CLASSES = java.util.Set.of(
                "com.myagent.workflow.handler.WorkflowTaskDelegate");
        java.util.Set<String> WHITELISTED_DELEGATE_EXPR = java.util.Set.of(
                "${workflowTaskDelegate}", "workflowTaskDelegate");
        String[] forbiddenAttrs = {"flowable:expression", "flowable:script", "scriptFormat"};
        for (String attr : forbiddenAttrs) {
            if (el.hasAttribute(attr))
                return ValidationResult.fail("禁止 Java/脚本/表达式委托属性 " + attr + " 在节点 " + el.getAttribute("id"));
        }
        if (el.hasAttribute("flowable:class")) {
            String v = el.getAttribute("flowable:class");
            if (!WHITELISTED_CLASSES.contains(v))
                return ValidationResult.fail("禁止非白名单 flowable:class " + v + " 在节点 " + el.getAttribute("id"));
        }
        if (el.hasAttribute("flowable:delegateExpression")) {
            String v = el.getAttribute("flowable:delegateExpression");
            if (!WHITELISTED_DELEGATE_EXPR.contains(v))
                return ValidationResult.fail("禁止非白名单 delegateExpression " + v + " 在节点 " + el.getAttribute("id"));
        }
        if (el.hasAttribute("flowable:type")) {
            String v = el.getAttribute("flowable:type");
            if (!isAllowedSafeType(v))
                return ValidationResult.fail("不支持的 flowable:type：" + v);
        }
        if (el.getElementsByTagName("script").getLength() > 0)
            return ValidationResult.fail("禁止 script 子元素在节点 " + el.getAttribute("id"));
        return ValidationResult.ok();
    }

    private boolean isAllowedSafeType(String type) {
        return type != null && Set.of("tool", "validate", "permission", "knowledge", "template", "confirm", "approve").contains(type);
    }

    private ValidationResult checkCondition(Element seqFlow) {
        String expr = seqFlow.getAttribute("conditionExpression");
        if (expr == null || expr.isBlank()) return ValidationResult.ok();
        // 简单白名单校验：仅允许字段名与操作符出现
        String normalized = expr.replaceAll("[${}\\s]", "");
        // 提取标识符
        String[] tokens = normalized.split("[()!&|=<>]+");
        for (String t : tokens) {
            if (t.isBlank()) continue;
            if (t.matches("\\d+")) continue;            // 数字
            if (EXPR_ALLOWED_OPS.contains(t)) continue; // 操作符
            if (EXPR_ALLOWED_FIELDS.contains(t)) continue; // 白名单字段
            // 形如 var.field 的流程变量访问，取 field 校验
            String[] parts = t.split("\\.");
            if (parts.length == 2 && EXPR_ALLOWED_FIELDS.contains(parts[1])) continue;
            return ValidationResult.fail("条件表达式包含非白名单字段：" + t + "（表达式：" + expr + "）");
        }
        return ValidationResult.ok();
    }

    private ValidationResult checkReachability(Document doc) {
        NodeList all = doc.getElementsByTagName("*");
        Set<String> withIncoming = new HashSet<>();
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element el)) continue;
            if (el.getTagName().equals("sequenceFlow") && el.hasAttribute("targetRef")) {
                withIncoming.add(el.getAttribute("targetRef"));
            }
        }
        for (int i = 0; i < all.getLength(); i++) {
            if (!(all.item(i) instanceof Element el)) continue;
            String tag = el.getTagName();
            if (!isFlowNode(tag)) continue;
            String id = el.getAttribute("id");
            if (!tag.equals("startEvent") && !withIncoming.contains(id)) {
                return ValidationResult.fail("存在不可达节点：" + id);
            }
        }
        return ValidationResult.ok();
    }

    private boolean isFlowNode(String tag) {
        return ALLOWED_TAGS.contains(tag) && !tag.equals("sequenceFlow");
    }

    private int countBpmnNodes(Document doc) {
        int n = 0;
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            if (all.item(i) instanceof Element el && isFlowNode(el.getTagName())) n++;
        }
        return n;
    }

    private boolean isMetaTag(String tag) {
        return Set.of("definitions", "message", "error", "signal", "collaboration", "participant", "ioSpecification", "extensionElements", "import", "relationship", "bpmnDiagram").contains(tag);
    }

    private Document parse(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 安全：禁用外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
        } catch (Exception e) {
            log.warn("BPMN 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
