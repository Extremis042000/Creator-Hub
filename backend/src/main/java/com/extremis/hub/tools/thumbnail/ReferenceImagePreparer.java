package com.extremis.hub.tools.thumbnail;

import com.extremis.hub.ai.OpenAiCompatibleImageProvider;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Turns a user-uploaded reference photo into a base64 data URI safely
 * under OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES
 * (Phase 40's live-verified gateway request-size ceiling). Never
 * trusts a client-claimed content type or file extension: the bytes
 * must actually decode as a real raster image via ImageIO, or they're
 * rejected outright. Downscales to a modest max dimension and
 * re-encodes as JPEG (compresses far better than PNG for a photo),
 * stepping quality down and, if that alone isn't enough, shrinking
 * dimensions further, until the result fits under the safe ceiling.
 * Pure JDK (java.awt/javax.imageio) -- no new dependency for a $0
 * budget project.
 */
@Component
public class ReferenceImagePreparer {

    private static final int MAX_DIMENSION = 1024;
    private static final int SHRUNK_MAX_DIMENSION = 640;
    private static final float INITIAL_QUALITY = 0.85f;
    private static final float MIN_QUALITY = 0.4f;
    private static final float QUALITY_STEP = 0.15f;

    public String prepare(String base64) {
        byte[] raw = decode(base64);
        BufferedImage original = decodeImage(raw);
        byte[] jpeg = compressUnderCeiling(original, MAX_DIMENSION);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    private byte[] decode(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolationException("Reference image data isn't valid base64.");
        }
    }

    private BufferedImage decodeImage(byte[] raw) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(raw));
            if (image == null) {
                throw new BusinessRuleViolationException("Reference image isn't a readable image file.");
            }
            return image;
        } catch (IOException e) {
            throw new BusinessRuleViolationException("Reference image isn't a readable image file.");
        }
    }

    /** Steps JPEG quality down first; if still over the ceiling at the quality floor, shrinks dimensions once more and retries. */
    private byte[] compressUnderCeiling(BufferedImage original, int maxDimension) {
        BufferedImage normalized = scaleAndFlatten(original, maxDimension);

        float quality = INITIAL_QUALITY;
        byte[] best = encodeJpeg(normalized, quality);
        while (best.length > OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP;
            best = encodeJpeg(normalized, quality);
        }

        if (best.length > OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES) {
            if (maxDimension > SHRUNK_MAX_DIMENSION) {
                return compressUnderCeiling(original, SHRUNK_MAX_DIMENSION);
            }
            throw new BusinessRuleViolationException(
                "Reference image is too complex to fit under the size limit -- try a simpler photo.");
        }
        return best;
    }

    /** Always produces a fresh TYPE_INT_RGB image (white background) at or below maxDimension -- normalizes away any alpha channel before JPEG encoding, which has none. */
    private BufferedImage scaleAndFlatten(BufferedImage image, int maxDimension) {
        int width = image.getWidth();
        int height = image.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage flattened = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flattened.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newWidth, newHeight);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return flattened;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new BusinessRuleViolationException("Couldn't process the reference image.");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleViolationException("Couldn't process the reference image.");
        } finally {
            writer.dispose();
        }
    }
}
