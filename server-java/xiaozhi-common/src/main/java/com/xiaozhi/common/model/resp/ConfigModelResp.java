package com.xiaozhi.common.model.resp;

import java.util.List;

/** Available model metadata only; never contains provider credentials. */
public record ConfigModelResp(String id, String label, boolean defaultModel, List<String> inputModalities,
                              List<String> reasoningEfforts, String defaultReasoningEffort) {
    public ConfigModelResp(String id, String label, boolean defaultModel, List<String> inputModalities) {
        this(id, label, defaultModel, inputModalities, List.of(), null);
    }
}
