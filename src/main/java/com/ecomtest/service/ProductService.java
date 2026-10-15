package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.dto.ProductSearchResult;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductService {

    private static final int NAME_FIELD_WEIGHT = 100;
    private static final int SKU_FIELD_WEIGHT = 10;
    private static final int DESCRIPTION_FIELD_WEIGHT = 1;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public List<Product> list() {
        return productRepository.findAll();
    }

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        productRepository.delete(product);
    }

    public Page<ProductSearchResult> search(String q, ProductStatus status, BigDecimal minPrice,
                                            BigDecimal maxPrice, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (q == null || q.isBlank()) {
            List<ProductSearchResult> results = productRepository.findFiltered(status, minPrice, maxPrice).stream()
                    .sorted(Comparator.comparing(Product::getId))
                    .map(product -> ProductSearchResult.from(product, List.of(), null))
                    .toList();
            return toPage(results, pageable);
        }

        String term = q.trim();
        List<ProductSearchResult> results = productRepository
                .findSearchCandidates(term, status, minPrice, maxPrice).stream()
                .map(product -> new ScoredProduct(product, score(product, term)))
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed()
                        .thenComparing(scored -> scored.product().getId()))
                .map(scored -> {
                    List<String> matchedFields = matchedFields(scored.product(), term);
                    String highlight = matchedFields.isEmpty()
                            ? null
                            : buildHighlight(scored.product(), term, matchedFields.get(0));
                    return ProductSearchResult.from(scored.product(), matchedFields, highlight);
                })
                .toList();
        return toPage(results, pageable);
    }

    private Page<ProductSearchResult> toPage(List<ProductSearchResult> results, Pageable pageable) {
        int total = results.size();
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<ProductSearchResult> content = start >= total ? List.of() : results.subList(start, end);
        return new PageImpl<>(content, pageable, total);
    }

    private int score(Product product, String term) {
        return NAME_FIELD_WEIGHT * scoreField(product.getName(), term)
                + SKU_FIELD_WEIGHT * scoreField(product.getSku(), term)
                + DESCRIPTION_FIELD_WEIGHT * scoreField(product.getDescription(), term);
    }

    private int scoreField(String fieldValue, String term) {
        if (fieldValue == null) {
            return 0;
        }
        String value = fieldValue.toLowerCase();
        String needle = term.toLowerCase();
        if (value.equals(needle)) {
            return 3;
        }
        if (value.startsWith(needle)) {
            return 2;
        }
        if (value.contains(needle)) {
            return 1;
        }
        return 0;
    }

    private List<String> matchedFields(Product product, String term) {
        List<String> fields = new ArrayList<>();
        if (scoreField(product.getName(), term) > 0) {
            fields.add("name");
        }
        if (scoreField(product.getSku(), term) > 0) {
            fields.add("sku");
        }
        if (scoreField(product.getDescription(), term) > 0) {
            fields.add("description");
        }
        return fields;
    }

    private String buildHighlight(Product product, String term, String field) {
        String value = switch (field) {
            case "name" -> product.getName();
            case "sku" -> product.getSku();
            case "description" -> product.getDescription();
            default -> null;
        };
        if (value == null) {
            return null;
        }
        String lowerValue = value.toLowerCase();
        String lowerTerm = term.toLowerCase();
        int index = lowerValue.indexOf(lowerTerm);
        if (index < 0) {
            return value;
        }
        return value.substring(0, index)
                + "[[" + value.substring(index, index + term.length()) + "]]"
                + value.substring(index + term.length());
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }

    private record ScoredProduct(Product product, int score) {
    }
}
