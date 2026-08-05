package com.portiq.service;

import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InsightsService {

    private final ChatCompletionClient chatCompletionClient;
    private final HoldingService holdingService;

    @Value("${app.insights.model:}")
    private String model;

    public InsightsService(ChatCompletionClient chatCompletionClient, HoldingService holdingService) {
        this.chatCompletionClient = chatCompletionClient;
        this.holdingService = holdingService;
    }

    public boolean isAvailable() {
        return chatCompletionClient.isConfigured();
    }

    public String generateSummary() {
        PerformanceSummary summary = holdingService.getAggregatePerformance();

        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content",
                        "You are a plain-spoken portfolio assistant. Summarize the investor's portfolio "
                                + "performance in 3 to 5 short sentences. No markdown, no bullet points, no headings. "
                                + "Mention the overall gain or loss, and call out the best and worst performing holdings by percent."),
                Map.of("role", "user", "content", buildPrompt(summary))
        );

        return chatCompletionClient.complete(model, messages).trim();
    }

    private String buildPrompt(PerformanceSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total cost basis: ").append(summary.getTotalCostBasis()).append("\n");
        sb.append("Total current value: ").append(summary.getTotalCurrentValue()).append("\n");
        sb.append("Overall gain/loss: ").append(summary.getTotalGainLoss())
                .append(" (").append(summary.getGainLossPercent()).append("%)\n");
        sb.append("Holdings:\n");
        for (HoldingPerformance h : summary.getHoldings()) {
            sb.append("- ").append(h.getTicker()).append(" (").append(h.getName()).append("): quantity ")
                    .append(h.getQuantity())
                    .append(", current value ").append(h.getCurrentValue())
                    .append(", gain/loss ").append(h.getGainLossPercent()).append("%\n");
        }
        return sb.toString();
    }
}
