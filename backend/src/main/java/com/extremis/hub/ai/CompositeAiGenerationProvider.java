package com.extremis.hub.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Round-robins across two or more configured backends to spread real
 * load evenly (Phase 26c) rather than always hammering one free
 * gateway's daily quota, and fails over to the next delegate on error
 * rather than surfacing it immediately -- the caller only sees a
 * failure once every delegate has been tried. Order isn't fixed: each
 * call starts at the next delegate in rotation (an AtomicInteger
 * counter, wrapped with floorMod so it never goes negative), so it's
 * genuinely round-robin, not "primary until it fails, then always the
 * backup."
 */
public class CompositeAiGenerationProvider implements AiGenerationProvider {

    private final List<AiGenerationProvider> delegates;
    private final AtomicInteger counter = new AtomicInteger();

    public CompositeAiGenerationProvider(List<AiGenerationProvider> delegates) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("CompositeAiGenerationProvider needs at least one delegate.");
        }
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public String getProviderName() {
        return "round-robin";
    }

    @Override
    public String getModelName() {
        return delegates.stream()
            .map(d -> d.getProviderName() + "/" + d.getModelName())
            .collect(Collectors.joining(", "));
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        int start = Math.floorMod(counter.getAndIncrement(), delegates.size());
        AiGenerationException lastFailure = null;

        for (int offset = 0; offset < delegates.size(); offset++) {
            AiGenerationProvider delegate = delegates.get((start + offset) % delegates.size());
            try {
                return delegate.generate(request);
            } catch (AiGenerationException e) {
                lastFailure = e;
            }
        }
        throw new AiGenerationException("All " + delegates.size() + " AI providers failed.", lastFailure);
    }
}
