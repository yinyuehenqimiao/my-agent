package com.myagent.rag.trace;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.myagent.framework.trace.RagTraceContext;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.NodeScore;
import com.myagent.rag.dto.SubQuestionIntent;
import com.myagent.rag.enums.IntentKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将节点返回值压缩为可落库、可展示的短摘要，避免把大对象或流式句柄整段写入审计。
 */
public final class TraceResultSummarizer {

    private TraceResultSummarizer() {
    }

    public static String toExtraData(Object result, String errorMessage, int maxLen) {
        return toExtraData(result, errorMessage, maxLen, null);
    }

    public static String toExtraData(Object result, String errorMessage, int maxLen, String nodeType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (errorMessage != null && !errorMessage.isBlank()) {
            payload.put("error", errorMessage);
        }
        String typedSummary = typedSummary(nodeType, result);
        if (typedSummary != null && !typedSummary.isBlank()) {
            payload.put("resultSummary", typedSummary);
        }
        if (result != null) {
            payload.put("resultType", result.getClass().getSimpleName());
            if (!payload.containsKey("resultSummary")) {
                String summary = briefSummary(result);
                if (summary != null && !summary.isBlank()) {
                    payload.put("resultSummary", summary);
                }
            }
            Object snapshot = compactSnapshot(result);
            if (snapshot != null) {
                payload.put("result", snapshot);
            }
        } else if (!payload.containsKey("resultSummary")
                && (errorMessage == null || errorMessage.isBlank())) {
            payload.put("resultSummary", "null");
        }
        return truncate(JSONUtil.toJsonStr(payload), Math.max(64, maxLen));
    }

    public static String extractSummary(String extraData) {
        if (extraData == null || extraData.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(extraData);
            String summary = obj.getStr("resultSummary");
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
            String error = obj.getStr("error");
            if (error != null && !error.isBlank()) {
                return error;
            }
        } catch (Exception ignored) {
            return truncate(extraData, 200);
        }
        return null;
    }

    private static String typedSummary(String nodeType, Object result) {
        String type = nodeType == null ? "" : nodeType.toUpperCase();
        if ("LLM_ROUTING".equals(type)) {
            String provider = RagTraceContext.getAttr("llm.selectedProvider");
            String modelId = RagTraceContext.getAttr("llm.selectedModelId");
            if (provider != null || modelId != null) {
                StringBuilder sb = new StringBuilder("已选用模型：");
                if (provider != null) {
                    sb.append(provider);
                }
                if (modelId != null && !modelId.isBlank()) {
                    if (provider != null) {
                        sb.append(" / ");
                    }
                    sb.append(modelId);
                }
                return sb.toString();
            }
            return "LLM 路由已完成（未记录到具体模型）";
        }
        if ("LLM_PROVIDER".equals(type) && result instanceof CharSequence text) {
            String intentSummary = summarizeIntentScoreJson(text.toString());
            if (intentSummary != null) {
                return "模型返回意图打分（不是最终答复）：" + intentSummary;
            }
            return "模型原始输出：" + truncate(text.toString(), 180);
        }
        return null;
    }

    private static String briefSummary(Object result) {
        if (result instanceof CharSequence text) {
            String intentSummary = summarizeIntentScoreJson(text.toString());
            if (intentSummary != null) {
                return intentSummary;
            }
            return truncate(text.toString(), 240);
        }
        if (result instanceof Number || result instanceof Boolean) {
            return String.valueOf(result);
        }
        if (result instanceof Collection<?> collection) {
            return summarizeCollection(collection);
        }
        if (isStreamingLike(result)) {
            return result.getClass().getSimpleName() + "(stream)";
        }
        try {
            JSONObject obj = result instanceof Map<?, ?> map
                    ? new JSONObject(map)
                    : JSONUtil.parseObj(JSONUtil.toJsonStr(result));
            String routeLike = summarizeRouteLike(obj);
            if (routeLike != null) {
                return routeLike;
            }
            if (obj.containsKey("name") && obj.containsKey("status")) {
                return obj.getStr("name") + "=" + obj.getStr("status");
            }
            if (result instanceof Map<?, ?> map) {
                return "map(size=" + map.size() + ")";
            }
            return truncate(obj.toString(), 240);
        } catch (Exception ignored) {
            if (result instanceof Map<?, ?> map) {
                return "map(size=" + map.size() + ")";
            }
            return truncate(String.valueOf(result), 240);
        }
    }

    @SuppressWarnings("unchecked")
    private static String summarizeCollection(Collection<?> collection) {
        if (collection.isEmpty()) {
            return "未匹配到候选意图";
        }
        Object first = collection.iterator().next();
        if (first instanceof NodeScore) {
            return summarizeNodeScores((Collection<NodeScore>) collection);
        }
        if (first instanceof SubQuestionIntent) {
            return summarizeSubQuestionIntents((Collection<SubQuestionIntent>) collection);
        }
        try {
            JSONArray arr = JSONUtil.parseArray(JSONUtil.toJsonStr(collection));
            List<String> parts = new ArrayList<>();
            int limit = Math.min(3, arr.size());
            for (int i = 0; i < limit; i++) {
                Object item = arr.get(i);
                if (!(item instanceof JSONObject obj)) {
                    continue;
                }
                if (obj.containsKey("nodeScores") || obj.containsKey("subQuestion")) {
                    String part = summarizeSubQuestionJson(obj);
                    if (part != null) {
                        parts.add(part);
                    }
                    continue;
                }
                String part = summarizeScoreItem(obj);
                if (part != null && !part.isBlank()) {
                    parts.add(part);
                }
            }
            if (!parts.isEmpty()) {
                return "意图候选 " + collection.size() + " 个：最高 " + String.join("；", parts);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "列表结果 " + collection.size() + " 项";
    }

    private static String summarizeNodeScores(Collection<NodeScore> scores) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        for (NodeScore score : scores) {
            if (i++ >= 3) {
                break;
            }
            IntentNode node = score.getNode();
            String name = node == null ? null : firstNonBlank(node.getName(), node.getFullPath(), node.getId());
            String kind = node == null || node.getKind() == null ? null : node.getKind().name();
            StringBuilder sb = new StringBuilder();
            sb.append(name == null ? "未知意图" : name);
            String kindLabel = kindLabel(kind);
            if (kindLabel != null) {
                sb.append("（").append(kindLabel).append("）");
            }
            sb.append(" ").append(String.format("%.2f", score.getScore()));
            parts.add(sb.toString());
        }
        return "意图候选 " + scores.size() + " 个：最高 " + String.join("；", parts);
    }

    private static String summarizeSubQuestionIntents(Collection<SubQuestionIntent> intents) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        for (SubQuestionIntent intent : intents) {
            if (i++ >= 3) {
                break;
            }
            List<NodeScore> scores = intent.nodeScores();
            if (scores == null || scores.isEmpty()) {
                parts.add((intent.subQuestion() == null ? "子问题" : intent.subQuestion()) + " → 无意图");
                continue;
            }
            NodeScore top = scores.get(0);
            IntentNode node = top.getNode();
            String name = node == null ? "未知意图" : firstNonBlank(node.getName(), node.getId());
            String kind = node == null || node.getKind() == null ? null : kindLabel(node.getKind().name());
            parts.add((intent.subQuestion() == null ? "子问题" : truncate(intent.subQuestion(), 24))
                    + " → " + name
                    + (kind == null ? "" : "（" + kind + "）")
                    + " " + String.format("%.2f", top.getScore()));
        }
        return "子问题意图 " + intents.size() + " 组：" + String.join("；", parts);
    }

    private static String summarizeSubQuestionJson(JSONObject obj) {
        String question = obj.getStr("subQuestion");
        JSONArray scores = obj.getJSONArray("nodeScores");
        if (scores == null || scores.isEmpty()) {
            return (question == null ? "子问题" : question) + " → 无意图";
        }
        Object first = scores.get(0);
        if (!(first instanceof JSONObject scoreObj)) {
            return null;
        }
        String item = summarizeScoreItem(scoreObj);
        if (item == null) {
            return null;
        }
        return (question == null ? "子问题" : truncate(question, 24)) + " → " + item;
    }

    private static String summarizeIntentScoreJson(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (!(trimmed.startsWith("[") || trimmed.startsWith("{"))) {
            return null;
        }
        try {
            Object parsed = JSONUtil.parse(trimmed);
            JSONArray arr;
            if (parsed instanceof JSONArray jsonArray) {
                arr = jsonArray;
            } else if (parsed instanceof JSONObject obj && obj.containsKey("results")) {
                arr = obj.getJSONArray("results");
            } else {
                return null;
            }
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            Object first = arr.get(0);
            if (!(first instanceof JSONObject obj)) {
                return null;
            }
            if (!(obj.containsKey("id") || obj.containsKey("score") || obj.containsKey("node"))) {
                return null;
            }
            List<String> parts = new ArrayList<>();
            int limit = Math.min(3, arr.size());
            for (int i = 0; i < limit; i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject scoreObj) {
                    String part = summarizeScoreItem(scoreObj);
                    if (part != null) {
                        parts.add(part);
                    }
                }
            }
            if (parts.isEmpty()) {
                return null;
            }
            return "共 " + arr.size() + " 个：" + String.join("；", parts);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String summarizeScoreItem(JSONObject obj) {
        JSONObject node = obj.getJSONObject("node");
        String name = null;
        String kind = null;
        String id = null;
        if (node != null) {
            name = firstNonBlank(node.getStr("name"), node.getStr("fullPath"));
            kind = node.getStr("kind");
            id = node.getStr("id");
        }
        if (name == null) {
            name = firstNonBlank(obj.getStr("name"), obj.getStr("intentName"), obj.getStr("id"));
        }
        if (kind == null) {
            kind = firstNonBlank(obj.getStr("kind"), obj.getStr("intentKind"));
        }
        if (id == null) {
            id = obj.getStr("id");
        }
        Double score = obj.getDouble("score");
        String reason = obj.getStr("reason");
        String kindLabel = kindLabel(kind);
        // 原始 LLM 打分常只有 id，没有中文名：至少展示 id
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name);
        } else if (id != null) {
            sb.append(id);
        } else {
            return null;
        }
        if (kindLabel != null) {
            sb.append("（").append(kindLabel).append("）");
        } else if (kind == null && id != null && id.contains("hr")) {
            // 无 kind 时不强行猜测
        }
        if (score != null) {
            sb.append(" ").append(String.format("%.2f", score));
        }
        if (reason != null && !reason.isBlank()) {
            sb.append("：").append(truncate(reason, 48));
        }
        return sb.toString();
    }

    private static String summarizeRouteLike(JSONObject obj) {
        if (!(obj.containsKey("decision") || obj.containsKey("intentCode") || obj.containsKey("target"))) {
            return null;
        }
        String decision = obj.getStr("decision");
        String target = obj.getStr("target");
        String intent = firstNonBlank(obj.getStr("intentName"), obj.getStr("intentCode"));
        StringBuilder sb = new StringBuilder();
        String decisionLabel = decisionLabel(decision);
        if (decisionLabel != null) {
            sb.append(decisionLabel);
        } else if (decision != null) {
            sb.append(decision);
        }
        if (intent != null && !intent.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" → ");
            }
            sb.append(intent);
        } else if (target != null && !target.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(" → ");
            }
            sb.append(target);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String kindLabel(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        return switch (kind.toUpperCase()) {
            case "KB" -> "知识库问答";
            case "WORKFLOW" -> "固定流程";
            case "SYSTEM" -> "系统交互";
            case "MCP" -> "实时工具/MCP";
            case "REACT" -> "开放式智能体";
            default -> kind;
        };
    }

    private static String decisionLabel(String decision) {
        if (decision == null || decision.isBlank()) {
            return null;
        }
        return switch (decision.toUpperCase()) {
            case "WORKFLOW" -> "走固定流程";
            case "REACT" -> "走开放式智能体";
            case "SYSTEM" -> "走系统回答";
            case "CLARIFY" -> "需要补充信息";
            case "DENY" -> "拒绝执行";
            default -> decision;
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Object compactSnapshot(Object result) {
        if (result instanceof CharSequence || result instanceof Number || result instanceof Boolean) {
            return result;
        }
        if (isStreamingLike(result)) {
            return null;
        }
        try {
            if (result instanceof Collection<?> collection) {
                if (!collection.isEmpty() && collection.iterator().next() instanceof NodeScore) {
                    List<Map<String, Object>> compact = new ArrayList<>();
                    int i = 0;
                    for (Object item : collection) {
                        if (i++ >= 5 || !(item instanceof NodeScore score)) {
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        IntentNode node = score.getNode();
                        if (node != null) {
                            row.put("id", node.getId());
                            row.put("name", node.getName());
                            IntentKind kind = node.getKind();
                            row.put("kind", kind == null ? null : kind.name());
                        }
                        row.put("score", score.getScore());
                        compact.add(row);
                    }
                    return compact;
                }
                String json = JSONUtil.toJsonStr(collection);
                if (json.length() <= 800) {
                    return JSONUtil.parse(json);
                }
                return truncate(json, 800);
            }
            String json = result instanceof Map<?, ?> map
                    ? JSONUtil.toJsonStr(map)
                    : JSONUtil.toJsonStr(result);
            if (json.length() <= 800) {
                return JSONUtil.parse(json);
            }
            return truncate(json, 800);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isStreamingLike(Object result) {
        String name = result.getClass().getName();
        return name.contains("Publisher")
                || name.contains("Flux")
                || name.contains("Mono")
                || name.contains("Stream")
                || name.contains("Iterator")
                || name.contains("Spliterator");
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen - 1)) + "…";
    }
}
