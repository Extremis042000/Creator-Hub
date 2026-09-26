package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Gating only -- constructing the client makes no network call. */
class ImageProviderConfigTest {

    private OpenAiCompatibleImageProperties properties(boolean enabled, String url, String key, String model) {
        OpenAiCompatibleImageProperties p = new OpenAiCompatibleImageProperties();
        p.setEnabled(enabled);
        p.setGenerationsUrl(url);
        p.setApiKey(key);
        p.setModel(model);
        return p;
    }

    @Test
    void registersNothingByDefaultWhenNothingIsConfigured() {
        assertThat(new ImageProviderConfig(properties(true, null, null, null)).imageGenerationProvider()).isNull();
    }

    @Test
    void needsAllThreeSettings() {
        assertThat(new ImageProviderConfig(
            properties(true, "https://h/v1/images/generations", "sk-x", null)).imageGenerationProvider()).isNull();
        assertThat(new ImageProviderConfig(
            properties(true, "https://h/v1/images/generations", " ", "m")).imageGenerationProvider()).isNull();
        assertThat(new ImageProviderConfig(
            properties(true, "", "sk-x", "m")).imageGenerationProvider()).isNull();
    }

    @Test
    void registersTheProviderWhenFullyConfigured() {
        ImageGenerationProvider p = new ImageProviderConfig(
            properties(true, "https://h/v1/images/generations", "sk-x", "qwen-image/z-image-turbo"))
            .imageGenerationProvider();

        assertThat(p).isInstanceOf(OpenAiCompatibleImageProvider.class);
        assertThat(p.getModelName()).isEqualTo("qwen-image/z-image-turbo");
        assertThat(p.getProviderName()).isEqualTo("openai-compatible-image");
    }

    @Test
    void killSwitchDisablesTheProviderEvenWhenFullyConfigured() {
        assertThat(new ImageProviderConfig(
            properties(false, "https://h/v1/images/generations", "sk-x", "m")).imageGenerationProvider()).isNull();
    }
}
