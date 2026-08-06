package com.portiq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The slice of an OpenAI-compatible chat completions response this application reads.
 *
 * <p>Replaces a chain of unchecked {@code Map}/{@code List} casts. Those casts were unchecked in
 * the literal compiler sense - nothing verified that {@code body.get("choices")} really was a list
 * of maps until a {@code ClassCastException} said so at runtime - and this is the one integration
 * where that matters most, because the payload comes from a third-party endpoint that is
 * configurable per deployment. A self-hosted gateway, a proxy that wraps errors in its own
 * envelope, or a provider changing its schema all arrive here as "not the shape we assumed".
 *
 * <p>Binding to records moves that failure to one place with a clear message, instead of a cast
 * exception thrown from the middle of a parsing method.
 *
 * <p>{@code ignoreUnknown} throughout: providers send usage counts, fingerprints and assorted
 * vendor extensions, and a new field appearing upstream must never break a running deployment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {}

    /**
     * The assistant text, or null when the response carried none.
     *
     * <p>Every level is optional because every level has been seen missing in practice: an empty
     * {@code choices} array when a provider filters a request, and a null {@code content} when a
     * model returns a tool call or stops on a content filter instead of producing text.
     */
    public String firstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice first = choices.get(0);
        if (first == null || first.message() == null) {
            return null;
        }
        return first.message().content();
    }
}
