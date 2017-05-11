package com.github.modelflat.calculus.exceptions;

public class NonUniformWorkDistributionException extends Exception {

    public NonUniformWorkDistributionException(int items, int threads) {
        super(String.format(
                "Non-implemented case - non-uniform work distribution: %d work items per %d threads",
                items, threads));
    }

}
