package com.myagent.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Comparator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RetrievedChunk {

    public static final Comparator<RetrievedChunk> BY_SCORE_DESC =
            (a, b) -> Float.compare(sortScore(b), sortScore(a));

    private static float sortScore(RetrievedChunk chunk) {
        Float score = chunk.getScore();
        return score == null || !Float.isFinite(score) ? Float.NEGATIVE_INFINITY : score;
    }

    private String id;
    private String text;
    private Float score;
    private Float rerankScore;
    private String collectionName;
    private String docId;
    private Integer chunkIndex;
    private String docName;
}
