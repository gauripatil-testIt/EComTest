package com.ecomtest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ImageOrderRequest {

    @NotNull
    @NotEmpty
    @Valid
    private List<ImageOrderEntry> images;

    public List<ImageOrderEntry> getImages() {
        return images;
    }

    public void setImages(List<ImageOrderEntry> images) {
        this.images = images;
    }

    public static class ImageOrderEntry {

        @NotNull
        private Long imageId;

        @NotNull
        private Integer displayOrder;

        public Long getImageId() {
            return imageId;
        }

        public void setImageId(Long imageId) {
            this.imageId = imageId;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
        }
    }
}
