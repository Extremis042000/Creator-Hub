package com.extremis.hub.tools.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.extremis.hub.ai.AiUsageGuard;
import com.extremis.hub.ai.AiUsageProperties;
import com.extremis.hub.ai.ImageGenerationException;
import com.extremis.hub.ai.ImageGenerationProvider;
import com.extremis.hub.ai.ImageGenerationRequest;
import com.extremis.hub.ai.ImageGenerationResult;
import com.extremis.hub.repository.AiGenerationLogRepository;
import com.extremis.hub.repository.UserRepository;
import com.extremis.hub.tools.common.TextSanitizer;
import com.extremis.hub.web.BusinessRuleViolationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Phase 41. Unlike Title/Description's *ServiceAiTest, there's no
 * template fallback to fall back TO here -- every failure path
 * asserts a thrown BusinessRuleViolationException instead.
 */
class ThumbnailGeneratorServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private ThumbnailGeneratorRequest request() {
        ThumbnailGeneratorRequest r = new ThumbnailGeneratorRequest();
        r.setGame("Valorant");
        r.setTopic("1v5 clutch on Ascent");
        r.setVideoType(VideoType.HIGHLIGHT);
        r.setTone(Tone.HYPE);
        r.setKeywords(List.of("ace"));
        return r;
    }

    private AiUsageGuard permissiveUsageGuard() {
        AiUsageProperties generous = new AiUsageProperties();
        generous.setRateLimitPerMinute(1000);
        generous.setDailyCallCeiling(1000);
        return new AiUsageGuard(generous, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
    }

    private ImageGenerationProvider fakeProvider(String imageUrl) {
        return new ImageGenerationProvider() {
            @Override public String getProviderName() { return "fake-image"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public ImageGenerationResult generate(ImageGenerationRequest request) {
                return new ImageGenerationResult(imageUrl, request.width(), request.height(), "fake-model");
            }
        };
    }

    private ImageGenerationProvider failingProvider() {
        return new ImageGenerationProvider() {
            @Override public String getProviderName() { return "fake-image"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public ImageGenerationResult generate(ImageGenerationRequest request) {
                throw new ImageGenerationException("simulated failure");
            }
        };
    }

    @Test
    void generatesAThumbnailAtRealYouTubeResolution() {
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.of(fakeProvider("https://example.com/thumb.png")),
            permissiveUsageGuard(), new ReferenceImagePreparer());

        ThumbnailGeneratorResponse response = service.generate(request(), USER_ID);

        assertThat(response.getImageUrl()).isEqualTo("https://example.com/thumb.png");
        assertThat(response.getWidth()).isEqualTo(1280);
        assertThat(response.getHeight()).isEqualTo(720);
    }

    @Test
    void noProviderConfiguredThrowsRatherThanReturningAnyFallback() {
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.empty(), permissiveUsageGuard(), new ReferenceImagePreparer());

        assertThatThrownBy(() -> service.generate(request(), USER_ID))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void providerFailureThrowsRatherThanReturningAnyFallback() {
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.of(failingProvider()), permissiveUsageGuard(), new ReferenceImagePreparer());

        assertThatThrownBy(() -> service.generate(request(), USER_ID))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rateLimitedCallThrowsWithoutEverCallingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        ImageGenerationProvider countingProvider = new ImageGenerationProvider() {
            @Override public String getProviderName() { return "fake-image"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public ImageGenerationResult generate(ImageGenerationRequest request) {
                callCount.incrementAndGet();
                return new ImageGenerationResult("https://example.com/x.png", 1280, 720, "fake-model");
            }
        };
        AiUsageProperties tightLimit = new AiUsageProperties();
        tightLimit.setRateLimitPerMinute(1);
        AiUsageGuard guard = new AiUsageGuard(tightLimit, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), guard, new ReferenceImagePreparer());

        service.generate(request(), USER_ID); // consumes the one allowed slot

        assertThatThrownBy(() -> service.generate(request(), USER_ID))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(callCount.get()).isEqualTo(1); // the throttled second call never reached the provider
    }

    @Test
    void dailyCeilingReachedThrowsWithoutEverCallingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        ImageGenerationProvider countingProvider = new ImageGenerationProvider() {
            @Override public String getProviderName() { return "fake-image"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public ImageGenerationResult generate(ImageGenerationRequest request) {
                callCount.incrementAndGet();
                return new ImageGenerationResult("https://example.com/x.png", 1280, 720, "fake-model");
            }
        };
        AiUsageProperties tightCeiling = new AiUsageProperties();
        tightCeiling.setDailyCallCeiling(0);
        AiUsageGuard guard = new AiUsageGuard(tightCeiling, mock(AiGenerationLogRepository.class), mock(UserRepository.class));
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), guard, new ReferenceImagePreparer());

        assertThatThrownBy(() -> service.generate(request(), USER_ID))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(callCount.get()).isZero();
    }

    @Test
    void noReferenceImageMeansTheProviderSeesANullOne() {
        AtomicInteger seenReferenceImage = new AtomicInteger(-1);
        ImageGenerationProvider provider = new ImageGenerationProvider() {
            @Override public String getProviderName() { return "fake-image"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public ImageGenerationResult generate(ImageGenerationRequest request) {
                seenReferenceImage.set(request.referenceImageDataUri() == null ? 0 : 1);
                return new ImageGenerationResult("https://example.com/x.png", 1280, 720, "fake-model");
            }
        };
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.of(provider), permissiveUsageGuard(), new ReferenceImagePreparer());

        service.generate(request(), USER_ID);

        assertThat(seenReferenceImage.get()).isZero();
    }

    @Test
    void invalidReferenceImageThrowsBeforeReachingTheProvider() {
        AtomicInteger callCount = new AtomicInteger();
        ImageGenerationProvider countingProvider = new ImageGenerationProvider() {
            @Override public String getProviderName() { return "fake-image"; }
            @Override public String getModelName() { return "fake-model"; }
            @Override public ImageGenerationResult generate(ImageGenerationRequest request) {
                callCount.incrementAndGet();
                return new ImageGenerationResult("https://example.com/x.png", 1280, 720, "fake-model");
            }
        };
        ThumbnailGeneratorRequest request = request();
        request.setReferenceImageBase64("not a real image at all");
        ThumbnailGeneratorService service = new ThumbnailGeneratorService(
            new TextSanitizer(), Optional.of(countingProvider), permissiveUsageGuard(), new ReferenceImagePreparer());

        assertThatThrownBy(() -> service.generate(request, USER_ID))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(callCount.get()).isZero();
    }
}
