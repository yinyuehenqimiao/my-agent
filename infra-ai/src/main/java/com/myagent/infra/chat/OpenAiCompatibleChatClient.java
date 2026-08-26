package com.myagent.infra.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myagent.framework.convention.ChatMessage;
import com.myagent.framework.convention.ChatRequest;
import com.myagent.framework.errorcode.BaseErrorCode;
import com.myagent.framework.exception.ServiceException;
import com.myagent.infra.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI 兼容协议 ChatClient（SiliconFlow / 百炼兼容模式 / 本地网关均可）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatClient implements ChatClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String provider() {
        return aiProperties.getChat().getProvider();
    }

    @Override
    public String chat(ChatRequest request, String model) {
        AiProperties.ProviderConfig provider = requireProvider(provider());
        ObjectNode body = buildBody(request, model, false);
        Request httpRequest = new Request.Builder()
                .url(joinUrl(provider.getUrl(), provider.getEndpoints().getChat()))
                .header("Authorization", "Bearer " + provider.getApiKey())
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ServiceException("模型调用失败 HTTP " + response.code() + ": " + raw);
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new ServiceException("模型返回空内容: " + raw);
            }
            return content.asText();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("模型调用异常: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public void streamChat(ChatRequest request, String model, StreamCallback callback) {
        AiProperties.ProviderConfig provider = requireProvider(provider());
        ObjectNode body = buildBody(request, model, true);
        Request httpRequest = new Request.Builder()
                .url(joinUrl(provider.getUrl(), provider.getEndpoints().getChat()))
                .header("Authorization", "Bearer " + provider.getApiKey())
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String raw = response.body() == null ? "" : response.body().string();
                throw new ServiceException("流式模型调用失败 HTTP " + response.code() + ": " + raw);
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new ServiceException("流式响应体为空");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull() && StringUtils.hasText(delta.asText())) {
                        callback.onContent(delta.asText());
                    }
                }
                callback.onComplete();
            }
        } catch (ServiceException e) {
            callback.onError(e);
            throw e;
        } catch (Exception e) {
            callback.onError(e);
            throw new ServiceException("流式模型调用异常: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private ObjectNode buildBody(ChatRequest request, String model, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        } else if (aiProperties.getChat().getTemperature() != null) {
            body.put("temperature", aiProperties.getChat().getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        } else if (aiProperties.getChat().getMaxTokens() != null) {
            body.put("max_tokens", aiProperties.getChat().getMaxTokens());
        }
        if (request.getTopP() != null) {
            body.put("top_p", request.getTopP());
        }

        ArrayNode messages = body.putArray("messages");
        List<ChatMessage> list = request.getMessages();
        if (list == null || list.isEmpty()) {
            throw new ServiceException("messages 不能为空");
        }
        for (ChatMessage message : list) {
            ObjectNode item = messages.addObject();
            item.put("role", message.getRole().apiValue());
            item.put("content", message.getContent());
        }
        return body;
    }

    private AiProperties.ProviderConfig requireProvider(String name) {
        AiProperties.ProviderConfig provider = aiProperties.getProviders().get(name);
        if (provider == null) {
            throw new ServiceException("未配置 AI 供应商: " + name);
        }
        if (!StringUtils.hasText(provider.getUrl())) {
            throw new ServiceException("供应商 url 为空: " + name);
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new ServiceException("未配置 API Key，请设置环境变量或 application.yaml 中 ai.providers." + name + ".api-key");
        }
        return provider;
    }

    private static String joinUrl(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }
}
