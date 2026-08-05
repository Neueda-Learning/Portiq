package com.portiq.dto;

import java.util.List;

public class HoldingImportResult {

    private int imported;
    private List<String> errors;

    public HoldingImportResult(int imported, List<String> errors) {
        this.imported = imported;
        this.errors = errors;
    }

    public int getImported() { return imported; }
    public void setImported(int imported) { this.imported = imported; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
