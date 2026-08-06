package com.portiq.dto;

import com.portiq.model.HoldingType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class HoldingRequest {

    /**
     * Every character a real symbol uses and nothing else: RELIANCE.NS, BRK-B, ^NSEI.
     *
     * <p>This is the load-bearing constraint on this DTO. A stored ticker is later interpolated
     * into the Yahoo chart URL and the news feed query, so an unconstrained value here is the entry
     * point for request smuggling into those calls - and tickers arrive from three places, a form,
     * a CSV, and a model reading an uploaded statement, only the first of which anyone eyeballs.
     */
    @NotBlank(message = "Ticker is required")
    @Size(max = 20, message = "Ticker must be 20 characters or fewer")
    @Pattern(regexp = "^[A-Za-z0-9^][A-Za-z0-9.\\-^]*$",
            message = "Ticker may only contain letters, digits, dots and hyphens, for example RELIANCE.NS")
    private String ticker;

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be 120 characters or fewer")
    private String name;

    @NotNull(message = "Type is required")
    private HoldingType type;

    /**
     * Bounded at both ends. The floor rejects zero and negative positions; the ceiling stops an
     * absurd value from reaching the BigDecimal arithmetic in the risk and performance engines,
     * where multiplying two unbounded decimals is a cheap way to exhaust memory.
     */
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
    @DecimalMax(value = "1000000000", message = "Quantity is unrealistically large")
    @Digits(integer = 10, fraction = 6, message = "Quantity has too many digits")
    private BigDecimal quantity;

    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Purchase price must be zero or positive")
    @DecimalMax(value = "1000000000", message = "Purchase price is unrealistically large")
    @Digits(integer = 10, fraction = 6, message = "Purchase price has too many digits")
    private BigDecimal purchasePrice;

    @PastOrPresent(message = "Purchase date cannot be in the future")
    private LocalDate purchaseDate;

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public HoldingType getType() { return type; }
    public void setType(HoldingType type) { this.type = type; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
}
