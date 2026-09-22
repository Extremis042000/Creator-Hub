package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Gating only -- constructing either client makes no network call. */
class AiProviderConfigTest {

    private OpenAiCompatibleProperties compat(String chatUrl, String key, String model) {
        OpenAiCompatibleProperties p = new OpenAiCompatibleProperties();
        p.setChatUrl(chatUrl);
        p.setApiKey(key);
        p.setModel(model);
        return p;
    }

    private AiGenerationProvider build(
            boolean enabled, String provider, String anthropicKey, OpenAiCompatibleProperties primary,
            OpenAiCompatibleProperties secondary) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        properties.setApiKey(anthropicKey);
        return new AiProviderConfig(properties, primary, secondary).aiGenerationProvider();
    }

    @Test
    void registersNothingByDefaultWhenNothingIsConfigured() {
        assertThat(build(true, "openai-compatible", null, compat(null, null, null), compat(null, null, null)))
            .isNull();
    }

    @Test
    void compatProviderNeedsAllThreeSettings() {
        assertThat(build(true, "openai-compatible", null,
            compat("https://h/v1/chat/completions", "sk-x", null), compat(null, null, null))).isNull();
        assertThat(build(true, "openai-compatible", null,
            compat("https://h/v1/chat/completions", " ", "m"), compat(null, null, null))).isNull();
        assertThat(build(true, "openai-compatible", null,
            compat("", "sk-x", "m"), compat(null, null, null))).isNull();
    }

    @Test
    void registersTheSingleCompatProviderDirectlyWhenOnlyOneIsConfigured() {
        AiGenerationProvider p = build(true, "openai-compatible", null,
            compat("https://h/v1/chat/completions", "sk-x", "fm-v1-lite"), compat(null, null, null));

        assertThat(p).isInstanceOf(OpenAiCompatibleGenerationProvider.class);
        assertThat(p.getModelName()).isEqualTo("fm-v1-lite");
    }

    @Test
    void registersTheSingleCompatProviderDirectlyWhenOnlySecondaryIsConfigured() {
        AiGenerationProvider p = build(true, "openai-compatible", null,
            compat(null, null, null), compat("https://api.free.ai/v1/chat/", "sk-free-x", "qwen7b"));

        assertThat(p).isInstanceOf(OpenAiCompatibleGenerationProvider.class);
        assertThat(p.getModelName()).isEqualTo("qwen7b");
    }

    @Test
    void wrapsInACompositeAndRoundRobinsWhenBothAreConfigured() {
        AiGenerationProvider p = build(true, "openai-compatible", null,
            compat("https://h1/v1/chat/completions", "sk-x", "fm-v1-lite"),
            compat("https://api.free.ai/v1/chat/", "sk-free-x", "qwen7b"));

        assertThat(p).isInstanceOf(CompositeAiGenerationProvider.class);
        assertThat(p.getProviderName()).isEqualTo("round-robin");
        assertThat(p.getModelName())
            .isEqualTo("openai-compatible/fm-v1-lite, openai-compatible/qwen7b");
    }

    @Test
    void killSwitchDisablesEitherProvider() {
        assertThat(build(false, "openai-compatible", null,
            compat("https://h/v1/chat/completions", "sk-x", "m"), compat(null, null, null))).isNull();
        assertThat(build(false, "anthropic", "sk-ant-test", compat(null, null, null), compat(null, null, null)))
            .isNull();
    }

    @Test
    void anthropicProviderIsSelectedOnlyExplicitlyAndNeedsItsOwnKey() {
        assertThat(build(true, "anthropic", null,
            compat("https://h/v1/chat/completions", "sk-x", "m"), compat(null, null, null))).isNull();
        AiGenerationProvider p = build(true, "anthropic", "sk-ant-test", compat(null, null, null),
            compat(null, null, null));

        assertThat(p).isInstanceOf(ClaudeGenerationProvider.class);
        assertThat(p.getModelName()).isEqualTo("claude-opus-5");
    }

    @Test
    void unknownProviderNameRegistersNothing() {
        assertThat(build(true, "nonsense", "sk-ant-test",
            compat("https://h/v1/chat/completions", "sk-x", "m"), compat(null, null, null))).isNull();
    }
}
