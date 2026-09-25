package com.ecomtest.dto;

import java.math.BigDecimal;

public interface ProductSearchProjection {

    Long getId();

    String getName();

    String getSku();

    String getDescription();

    BigDecimal getPrice();

    Integer getStock();

    String getStatus();

    Double getRank();

    String getNameHighlight();

    String getSkuHighlight();

    String getDescriptionHighlight();
}
