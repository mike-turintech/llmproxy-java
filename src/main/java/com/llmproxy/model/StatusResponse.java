package com.llmproxy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {
    private boolean openai;
    private boolean gemini;
    private boolean mistral;
    private boolean claude;
}
