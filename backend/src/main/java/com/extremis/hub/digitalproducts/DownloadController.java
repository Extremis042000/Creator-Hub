package com.extremis.hub.digitalproducts;

import com.extremis.hub.domain.Product;
import com.extremis.hub.repository.ProductRepository;
import com.extremis.hub.web.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed token itself is the credential (same model as a
 * presigned S3 URL) -- no Authorization header required or checked
 * here, only signature + expiry (DownloadTokenService) and that the
 * resolved file path stays inside the secure files directory.
 */
@RestController
@RequestMapping("/api/v1/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private static final String INVALID_LINK_MESSAGE = "This download link is invalid or has expired.";

    private final DownloadTokenService downloadTokenService;
    private final ProductRepository productRepository;
    private final DigitalProductProperties properties;

    @GetMapping("/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) throws IOException {
        UUID productId = downloadTokenService.verifyAndGetProductId(token)
            .orElseThrow(() -> new ResourceNotFoundException(INVALID_LINK_MESSAGE));

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException(INVALID_LINK_MESSAGE));

        Path baseDir = Paths.get(properties.getSecureFilesDir()).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(product.getFileRef()).normalize();
        // fileRef is admin-entered; defend against it escaping the secure
        // directory (e.g. "../../etc/passwd") even though only admins can set it.
        if (!filePath.startsWith(baseDir) || !Files.isRegularFile(filePath)) {
            throw new ResourceNotFoundException(INVALID_LINK_MESSAGE);
        }

        Resource resource = new FileSystemResource(filePath);
        String filename = filePath.getFileName().toString();
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentLength(Files.size(filePath))
            .body(resource);
    }
}
