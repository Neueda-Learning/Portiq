package com.portiq.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkDeleteRequest {

    /**
     * Capped so one request cannot ask the database to load an unbounded id list into memory.
     * A thousand is far beyond any real selection in the UI.
     */
    @NotEmpty(message = "At least one holding id is required")
    @Size(max = 1000, message = "At most 1000 holdings can be deleted in one request")
    private List<@NotNull @Positive(message = "Holding ids must be positive") Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
