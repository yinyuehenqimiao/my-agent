package com.myagent.framework.convention;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceRef {
    private Integer index;
    private String docId;
    private String docName;
    private String sourceType;
    private String fileType;
    private String url;
    private String excerpt;
}
