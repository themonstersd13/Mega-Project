package com.synapse.memory;

public interface EmbeddingProvider {
    float[] embed(String text);
}
