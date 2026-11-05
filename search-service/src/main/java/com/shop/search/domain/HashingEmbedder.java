package com.shop.search.domain;

import java.util.Locale;

public final class HashingEmbedder {
    public static final int DIM = 128;

    private HashingEmbedder() {}

    public static double[] embed(String text) {
        double[] vector = new double[DIM];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int hash = token.hashCode();
            vector[Math.floorMod(hash, DIM)] += (hash & 1) == 0 ? 1.0 : -1.0;
        }
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }

    public static double cosine(double[] left, double[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return dot;
    }
}
