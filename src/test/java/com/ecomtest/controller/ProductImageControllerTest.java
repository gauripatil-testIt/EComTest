package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String sku) {
        return """
                {
                  "name": "Wireless Mouse",
                  "sku": "%s",
                  "price": 19.99,
                  "stock": 100,
                  "status": "ACTIVE"
                }
                """.formatted(sku);
    }

    private Long createProduct(String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private byte[] genuineJpegBytes(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadsGenuineImageAndReturnsRenditionUrls() throws Exception {
        Long productId = createProduct("SKU-IMG-UPLOAD-1");
        byte[] jpegBytes = genuineJpegBytes(300, 200, Color.RED);

        MockMultipartFile file = new MockMultipartFile("files", "mouse.jpg", "image/jpeg", jpegBytes);

        mockMvc.perform(multipart("/api/products/{productId}/images", productId)
                        .file(file)
                        .header("Authorization", basicAuthHeader("admin", "admin")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploaded.length()").value(1))
                .andExpect(jsonPath("$.uploaded[0].thumbnailUrl").isNotEmpty())
                .andExpect(jsonPath("$.uploaded[0].mediumUrl").isNotEmpty())
                .andExpect(jsonPath("$.uploaded[0].fullUrl").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(0));
    }

    @Test
    void rejectsFileThatIsNotAGenuineImageEvenWithImageExtension() throws Exception {
        Long productId = createProduct("SKU-IMG-INVALID-1");
        byte[] notAnImage = "this is definitely not image data".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile("files", "fake.jpg", "image/jpeg", notAnImage);

        mockMvc.perform(multipart("/api/products/{productId}/images", productId)
                        .file(file)
                        .header("Authorization", basicAuthHeader("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.uploaded.length()").value(0))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].filename").value("fake.jpg"))
                .andExpect(jsonPath("$.errors[0].message", org.hamcrest.Matchers.containsStringIgnoringCase("genuine")));
    }

    @Test
    void settingSecondImageAsMainUnsetsThePreviousMain() throws Exception {
        Long productId = createProduct("SKU-IMG-MAIN-1");
        byte[] first = genuineJpegBytes(100, 100, Color.BLUE);
        byte[] second = genuineJpegBytes(100, 100, Color.GREEN);

        MockMultipartFile firstFile = new MockMultipartFile("files", "first.jpg", "image/jpeg", first);
        String firstMetadata = """
                [{"filename":"first.jpg","displayOrder":0,"isMain":true}]
                """;

        mockMvc.perform(multipart("/api/products/{productId}/images", productId)
                        .file(firstFile)
                        .param("metadata", firstMetadata)
                        .header("Authorization", basicAuthHeader("admin", "admin")))
                .andExpect(status().isCreated());

        MockMultipartFile secondFile = new MockMultipartFile("files", "second.jpg", "image/jpeg", second);
        String secondMetadata = """
                [{"filename":"second.jpg","displayOrder":1,"isMain":true}]
                """;

        mockMvc.perform(multipart("/api/products/{productId}/images", productId)
                        .file(secondFile)
                        .param("metadata", secondMetadata)
                        .header("Authorization", basicAuthHeader("admin", "admin")))
                .andExpect(status().isCreated());

        String listResponse = mockMvc.perform(get("/api/products/{productId}/images", productId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var imagesNode = objectMapper.readTree(listResponse);
        int mainCount = 0;
        boolean imageWithDisplayOrderOneIsMain = false;
        for (var node : imagesNode) {
            if (node.get("isMain").asBoolean()) {
                mainCount++;
                if (node.get("displayOrder").asInt() == 1) {
                    imageWithDisplayOrderOneIsMain = true;
                }
            }
        }
        assertEquals(1, mainCount);
        assertTrue(imageWithDisplayOrderOneIsMain, "Expected the second uploaded image (displayOrder=1) to be the main image");
    }

    @Test
    void deletesImageAndRemovesItFromSubsequentListing() throws Exception {
        Long productId = createProduct("SKU-IMG-DELETE-1");
        byte[] jpegBytes = genuineJpegBytes(120, 120, Color.YELLOW);

        MockMultipartFile file = new MockMultipartFile("files", "delete-me.jpg", "image/jpeg", jpegBytes);

        String uploadResponse = mockMvc.perform(multipart("/api/products/{productId}/images", productId)
                        .file(file)
                        .header("Authorization", basicAuthHeader("admin", "admin")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long imageId = objectMapper.readTree(uploadResponse).get("uploaded").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/products/{productId}/images/{imageId}", productId, imageId)
                        .header("Authorization", basicAuthHeader("admin", "admin")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{productId}/images", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + imageId + ")]").isEmpty());
    }

    @Test
    void uploadWithoutCredentialsIsRejected() throws Exception {
        Long productId = createProduct("SKU-IMG-AUTH-1");
        byte[] jpegBytes = genuineJpegBytes(100, 100, Color.CYAN);

        MockMultipartFile file = new MockMultipartFile("files", "unauth.jpg", "image/jpeg", jpegBytes);

        int status = mockMvc.perform(multipart("/api/products/{productId}/images", productId)
                        .file(file))
                .andReturn().getResponse().getStatus();

        assertTrue(status == 401 || status == 403,
                "Expected 401 or 403 for unauthenticated upload, got " + status);
    }

    @Test
    void getImagesRemainsAccessibleWithoutAuthentication() throws Exception {
        Long productId = createProduct("SKU-IMG-PUBLIC-1");

        mockMvc.perform(get("/api/products/{productId}/images", productId))
                .andExpect(status().isOk());
    }
}
