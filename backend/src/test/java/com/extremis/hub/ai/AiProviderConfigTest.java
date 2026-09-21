package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Gating only -- constructing either client makes no network call. */
class AiProviderConfigTest {

    private AiGenerationProvider build(
            boolean enabled, String provider, String anthropicKey, String baseUrl, String compatKey, String model) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        properties.setApiKey(anthropicKey);
        OpenAiCompatibleProperties compat = new OpenAiCompatibleProperties();
        compat.setBaseUrl(baseUrl);
        compat.setApiKey(compatKey);
        compat.setModel(model);
        return new AiProviderConfig(properties, compat).aiGenerationProvider();
    }

    @Test
    void registersNothingByDefaultWhenNothingIsConfigured() {
        assertThat(build(true, "openai-compatible", null, null, null, null)).isNull();
    }

    @Test
    void compatProviderNeedsAllThreeSettings() {
        assertThat(build(true, "openai-compatible", null, "https://h/v1", "sk-x", null)).isNull();
        assertThat(build(true, "openai-compatible", null, "https://h/v1", " ", "m")).isNull();
        assertThat(build(true, "openai-compatible", null, "", "sk-x", "m")).isNull();
    }

    @Test
    void registersTheCompatProviderWhenFullyConfigured() {
        AiGenerationProvider p = build(true, "openai-compatible", null, "https://h/v1", "sk-x", "fm-v1-lite");

        assertThat(p).isInstanceOf(OpenAiCompatibleGenerationProvider.class);
        assertThat(p.getModelName()).isEqualTo("fm-v1-lite");
    }

    @Test
    void killSwitchDisablesEitherProvider() {
        assertThat(build(false, "openai-compatible", null, "https://h/v1", "sk-x", "m")).isNull();
        assertThat(build(false, "anthropic", "sk-ant-test", null, null, null)).isNull();
    }

    @Test
    void anthropicProviderIsSelectedOnlyExplicitlyAndNeedsItsOwnKey() {
        assertThat(build(true, "anthropic", null, "https://h/v1", "sk-x", "m")).isNull();
        AiGenerationProvider p = build(true, "anthropic", "sk-ant-test", null, null, null);

        assertThat(p).isInstanceOf(ClaudeGenerationProvider.class);
        assertThat(p.getModelName()).isEqualTo("claude-opus-5");
    }

    @Test
    void unknownProviderNameRegistersNothing() {
        assertThat(build(true, "nonsense", "sk-ant-test", "https://h/v1", "sk-x", "m")).isNull();
    }
}
