package com.myagent.agent.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteUnitSplitterTest {

    @Test
    void splitsExplicitParallelBusinessRequests() {
        assertThat(RouteUnitSplitter.split("查询年假制度，并且查询远程办公制度"))
                .containsExactly("查询年假制度", "查询远程办公制度");
    }

    @Test
    void keepsCorrectionSentenceTogetherForSingleClassification() {
        assertThat(RouteUnitSplitter.split("不是查询年假制度，而是我要申请年假"))
                .containsExactly("不是查询年假制度，而是我要申请年假");
    }
}
