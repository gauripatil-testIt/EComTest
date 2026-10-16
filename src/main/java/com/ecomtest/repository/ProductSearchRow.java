package com.ecomtest.repository;

import com.ecomtest.entity.Product;

public record ProductSearchRow(Product product, boolean nameMatched, boolean skuMatched) {
}
