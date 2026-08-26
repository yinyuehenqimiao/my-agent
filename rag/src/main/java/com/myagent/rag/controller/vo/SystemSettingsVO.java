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

package com.myagent.rag.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 系统设置只读汇总（对齐 ragent，当前先覆盖前端知识库创建 / 上传所需字段）
 */
@Data
@Builder
public class SystemSettingsVO {

    private AISettings ai;
    private UploadSettings upload;

    @Data
    @Builder
    public static class UploadSettings {
        private Long maxFileSize;
        private Long maxRequestSize;
    }

    @Data
    @Builder
    public static class AISettings {
        private Map<String, ProviderConfig> providers;
        private ModelGroup embedding;

        @Data
        @Builder
        public static class ProviderConfig {
            private String url;
            private String apiKey;
            private Map<String, String> endpoints;
        }

        @Data
        @Builder
        public static class ModelGroup {
            private String defaultModel;
            private List<ModelCandidate> candidates;
        }

        @Data
        @Builder
        public static class ModelCandidate {
            private String id;
            private String provider;
            private String model;
            private String url;
            private Integer dimension;
            private Integer priority;
            private Boolean enabled;
        }
    }
}
