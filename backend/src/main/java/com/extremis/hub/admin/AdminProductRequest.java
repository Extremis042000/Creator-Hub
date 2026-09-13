package com.extremis.hub.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared shape for create and update -- update additionally carries
 * `active` (see AdminProductUpdateRequest). fileRef must already
 * exist as a file inside the secure files directory (no upload
 * endpoint yet -- the founder places files there manually for now).
 */
@Getter
@Setter
public class AdminProductRequest {

    @NotBlank(message = "must not be blank")
    @Size(max = 64, message = "must be at most 64 characters")
    private String categorySlug;

    @NotBlank(message = "must not be blank")
    @Size(max = 255, message = "must be at most 255 characters")
    private String name;

    @Min(value = 0, message = "must not be negative")
    private long priceCents;

    @Size(max = 8, message = "must be at most 8 characters")
    private String currency = "USD";

    @NotBlank(message = "must not be blank")
    @Size(max = 255, message = "must be at most 255 characters")
    private String fileRef;
}
