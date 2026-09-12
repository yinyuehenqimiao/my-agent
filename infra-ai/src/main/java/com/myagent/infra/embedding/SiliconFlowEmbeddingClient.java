/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.myagent.infra.embedding;

import com.google.gson.JsonObject;
import com.myagent.infra.enums.ModelProvider;
import com.myagent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

@Service
public class SiliconFlowEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public SiliconFlowEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    protected int maxBatchSize() {
        return 32;
    }

    /**
     * SiliconFlow 仅 Qwen/Qwen3 Embedding 系列支持 dimensions；
     * BAAI/bge-m3 等模型传该字段会返回 HTTP 400（code 20015）。
     */
    @Override
    protected void customizeRequestBody(JsonObject body, ModelTarget target) {
        super.customizeRequestBody(body, target);
        if (!supportsDimensionsParameter(target)) {
            body.remove("dimensions");
        }
    }

    private boolean supportsDimensionsParameter(ModelTarget target) {
        String model = target.candidate().getModel();
        return model != null && model.contains("Qwen") && model.contains("Embedding");
    }
}
