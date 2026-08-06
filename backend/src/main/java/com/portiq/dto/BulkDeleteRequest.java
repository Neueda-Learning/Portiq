package com.portiq.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BulkDeleteRequest {

    @NotEmpty(message = "At least one holding id is required")
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
