package com.portiq.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.portiq.security.EncryptedBigDecimalConverter;
import com.portiq.security.EncryptedLocalDateConverter;
import com.portiq.security.EncryptedStringConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @JsonBackReference
    private Portfolio portfolio;

    @NotBlank(message = "Ticker is required")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ticker;

    @NotBlank(message = "Name is required")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @NotNull(message = "Type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldingType type;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0001", message = "Quantity must be greater than zero")
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private BigDecimal quantity;

    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Purchase price must be zero or positive")
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "purchase_price", nullable = false, columnDefinition = "TEXT")
    private BigDecimal purchasePrice;

    @Convert(converter = EncryptedLocalDateConverter.class)
    @Column(name = "purchase_date", columnDefinition = "TEXT")
    private LocalDate purchaseDate;

    public Holding() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }
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
