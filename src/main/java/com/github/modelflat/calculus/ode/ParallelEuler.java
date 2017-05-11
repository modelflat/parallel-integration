package com.github.modelflat.calculus.ode;

import com.github.modelflat.calculus.exceptions.NonUniformWorkDistributionException;

public class ParallelEuler extends AbstractParallelODESolver {

    public ParallelEuler(Function[] functions, int threadCount) throws NonUniformWorkDistributionException {
        super(functions, threadCount);
    }

    @Override
    protected void performStep(ParallelPart part, int start, int stop) throws InterruptedException {
        for (int i = start; i < stop; ++i) {
            y1[i] = y0[i] + step*functions[i].call(t, y0);
        }
        synchronize(part, Stage.GLOBAL);
    }
}
