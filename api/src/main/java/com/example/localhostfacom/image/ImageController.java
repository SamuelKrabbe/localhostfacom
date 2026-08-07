package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.image.dto.ImageResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/images")
public class ImageController {

    private final ImageService service;

    public ImageController(ImageService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("empty-file", "No file was uploaded");
        }
        try {
            Image image = service.uploadAndSave(file.getBytes());
            return new ImageResponse(
                    image.getId(), service.publicUrl(image), image.getWidth(), image.getHeight());
        } catch (IOException exception) {
            throw ApiException.badRequest("unreadable-upload", "Could not read the uploaded file");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
