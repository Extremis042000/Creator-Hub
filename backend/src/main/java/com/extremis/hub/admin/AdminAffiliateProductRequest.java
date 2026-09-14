package com.extremis.hub.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Shared shape for create and update -- update additionally carries
 * `active` (see AdminAffiliateProductUpdateRequest) since a brand-new
 * product always starts active=true with no way to set otherwise.
 */
@Getter
@Setter
public class AdminAffiliateProductRequest {

    @NotBlank(message = "must not be blank")
    @Size(max = 255, message = "must be at most 255 characters")
    private String name;

    @Size(max = 255, message = "must be at most 255 characters")
    private String brand;

    @Size(max = 64, message = "must be at most 64 characters")
    private String category;

    @Size(max = 255, message = "must be at most 255 characters")
    private String priceInfo;

    @NotBlank(message = "must not be blank")
    @Size(max = 2048, message = "must be at most 2048 characters")
    @Pattern(regexp = "^https?://.+", message = "must be a valid http(s) URL")
    private String affiliateUrl;

    @Size(max = 128, message = "must be at most 128 characters")
    private String merchant;

    @Size(max = 64, message = "must be at most 64 characters")
    private String region;

    @Size(max = 1000, message = "must be at most 1000 characters")
    private String disclosureText;

    @Size(max = 2048, message = "must be at most 2048 characters")
    @Pattern(regexp = "^$|^https?://.+", message = "must be a valid http(s) URL")
    private String imageUrl;
}
