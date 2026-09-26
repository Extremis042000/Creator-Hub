package com.extremis.hub.tools.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.extremis.hub.ai.OpenAiCompatibleImageProvider;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Uses real, in-memory generated images (no fixture files) and the
 * real ImageIO codec -- exercises the actual decode/scale/recompress
 * pipeline, not a mocked one.
 */
class ReferenceImagePreparerTest {

    private final ReferenceImagePreparer preparer = new ReferenceImagePreparer();

    private String pngBase64(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void aLargeImageIsDownscaledAndCompressedUnderTheSafeCeiling() {
        // A large, high-entropy-free solid colour compresses trivially either way --
        // this asserts the PIPELINE runs end to end and respects the ceiling, not that
        // compression alone was necessary for this particular input.
        String dataUri = preparer.prepare(pngBase64(3000, 3000, Color.RED));

        assertThat(dataUri).startsWith("data:image/jpeg;base64,");
        byte[] decoded = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
        assertThat(decoded.length).isLessThanOrEqualTo(OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES);
    }

    @Test
    void aDetailedHighFrequencyImageStillEndsUpUnderTheCeilingAfterQualityStepping() {
        // A fine checkerboard has real high-frequency detail (unlike a
        // solid colour) that JPEG can't discard for free at high quality --
        // this exercises the quality-stepping loop actually doing work,
        // without the unpredictable worst-case compression ratio of pure
        // random noise (which risks a flaky test at the ceiling boundary).
        BufferedImage checkerboard = new BufferedImage(1400, 1400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = checkerboard.createGraphics();
        for (int x = 0; x < checkerboard.getWidth(); x += 4) {
            for (int y = 0; y < checkerboard.getHeight(); y += 4) {
                g.setColor(((x / 4 + y / 4) % 2 == 0) ? Color.BLACK : Color.WHITE);
                g.fillRect(x, y, 4, 4);
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(checkerboard, "png", out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String base64 = Base64.getEncoder().encodeToString(out.toByteArray());

        String dataUri = preparer.prepare(base64);

        byte[] decoded = Base64.getDecoder().decode(dataUri.substring(dataUri.indexOf(',') + 1));
        assertThat(decoded.length).isLessThanOrEqualTo(OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES);
    }

    @Test
    void aSmallImagePassesThroughWithoutError() {
        String dataUri = preparer.prepare(pngBase64(64, 64, Color.BLUE));

        assertThat(dataUri).startsWith("data:image/jpeg;base64,");
    }

    @Test
    void invalidBase64Throws() {
        assertThatThrownBy(() -> preparer.prepare("not valid base64!!! ###"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void validBase64ThatIsNotAnImageThrows() {
        String notAnImage = Base64.getEncoder().encodeToString("just some plain text bytes".getBytes());

        assertThatThrownBy(() -> preparer.prepare(notAnImage))
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}
