package com.extremis.hub.tools.thumbnail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThumbnailGeneratorRequest {

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String game;

    @NotBlank(message = "must not be blank")
    @Size(max = 100, message = "must be at most 100 characters")
    private String topic;

    @NotNull(message = "must not be null")
    private VideoType videoType;

    @NotNull(message = "must not be null")
    private Tone tone;

    @Valid
    @Size(max = 10, message = "must contain at most 10 keywords")
    private List<@Size(max = 40, message = "each keyword must be at most 40 characters") String> keywords = List.of();

    /**
     * Optional user-uploaded reference photo, plain base64 (no
     * "data:" prefix -- the real content type is never trusted from
     * the client). See ReferenceImagePreparer, which decodes and
     * re-validates the actual bytes via ImageIO before ever using
     * them. Size-capped here at the raw base64-string level (~6MB
     * decoded) only to reject an obviously abusive upload before any
     * processing -- the real target ceiling (~120KB, the image
     * gateway's own live-verified limit -- see
     * OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES) is
     * enforced by downscaling/recompressing in ReferenceImagePreparer,
     * not here.
     */
    @Size(max = 8_000_000, message = "reference image is too large")
    private String referenceImageBase64;
}
