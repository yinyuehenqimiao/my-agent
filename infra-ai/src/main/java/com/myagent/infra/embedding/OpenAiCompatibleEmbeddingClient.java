package com.myagent.infra.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myagent.framework.errorcode.BaseErrorCode;
import com.myagent.framework.exception.ServiceException;
import com.myagent.infra.config.AiProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String provider() {
        return aiProperties.getEmbedding().getProvider();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return embed(texts, provider(), aiProperties.getEmbedding().getModel());
    }

    @Override
    public List<float[]> embed(List<String> texts, String providerName, String model) {
        if (texts == null || texts.isEmpty()) {
            throw new ServiceException("embedding 文本不能为空");
        }
        if (!StringUtils.hasText(model)) {
            throw new ServiceException("embedding 模型不能为空");
        }
        AiProperties.ProviderConfig provider = requireProvider(providerName);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);

        Request httpRequest = new Request.Builder()
                .url(joinUrl(provider.getUrl(), provider.getEndpoints().getEmbedding()))
                .header("Authorization", "Bearer " + provider.getApiKey())
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new ServiceException("Embedding 调用失败 HTTP " + response.code() + ": " + raw);
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Embedding 调用异常: " + e.getMessage(), e, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private AiProperties.ProviderConfig requireProvider(String name) {
        AiProperties.ProviderConfig provider = aiProperties.getProviders().get(name);
        if (provider == null || !StringUtils.hasText(provider.getUrl()) || !StringUtils.hasText(provider.getApiKey())) {
            throw new ServiceException("Embedding 供应商未正确配置: " + name);
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
