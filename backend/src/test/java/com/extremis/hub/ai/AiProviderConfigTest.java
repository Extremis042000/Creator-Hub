package com.extremis.hub.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Gating only -- constructing the SDK client with a dummy key makes no network call. */
class AiProviderConfigTest {

    private AiGenerationProvider build(boolean enabled, String apiKey) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.setApiKey(apiKey);
        return new AiProviderConfig(properties).aiGenerationProvider();
    }

    @Test
    void registersNothingWhenKeyIsNull() {
        assertThat(build(true, null)).isNull();
    }

    @Test
    void registersNothingWhenKeyIsBlank() {
        assertThat(build(true, "   ")).isNull();
    }

    @Test
    void registersNothingWhenKillSwitchIsOffEvenWithAKey() {
        assertThat(build(false, "sk-ant-test")).isNull();
    }

    @Test
    void registersTheClaudeProviderWhenEnabledAndKeyed() {
        AiGenerationProvider provider = build(true, "sk-ant-test");

        assertThat(provider).isInstanceOf(ClaudeGenerationProvider.class);
        assertThat(provider.getProviderName()).isEqualTo("anthropic");
        assertThat(provider.getModelName()).isEqualTo("claude-opus-5");
    }
}
