package com.github.modelflat.calculus.ode;

import com.github.modelflat.calculus.exceptions.InvalidInitialConditionsVectorException;
import com.github.modelflat.calculus.exceptions.NonUniformWorkDistributionException;

public class ParallelRK extends AbstractParallelODESolver {

    private float[][] a;
    private float[] b;
    private float[] c;

    private float[][] k;
    private int order;
    private float[] yTemp;

    public static ParallelRK createRK2EulerModified(Function[] functions, int threadCount)
            throws NonUniformWorkDistributionException {
        return new ParallelRK(functions, threadCount,
                new float[][] {
                        {0},
                        {1},
                },
                new float[] {0.5f, 0.5f},
                new float[] {0, 1}, 2);
    }

    public static ParallelRK createRK2EulerRecalc(Function[] functions, int threadCount)
            throws NonUniformWorkDistributionException {
        return new ParallelRK(functions, threadCount,
                new float[][] {
                        {0},
                        {.5f}
                },
                new float[] {0, 1},
                new float[] {0, 0.5f}, 2);
    }

    public static ParallelRK createRK4(Function[] functions, int threadCount)
            throws  NonUniformWorkDistributionException {
        return new ParallelRK(functions, threadCount,
                new float[][] {
                        {0, 0, 0},
                        {0.5f, 0, 0},
                        {0, 0.5f, 0},
                        {0, 0, 1}},
                new float[] {1.0f/6, 2.0f/6, 2.0f/6, 1.0f/6},
                new float[] {0, 0.5f, 0.5f, 1}, 4);
    }

    public ParallelRK(Function[] functions, int threadCount, float[][] a, float[] b, float[] c, int order)
            throws NonUniformWorkDistributionException {
        super(functions, threadCount);
        this.order = order;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    private void updateStage1(int order, int i) {
        yTemp[i] = y0[i];
        for (int k = 0; k < order; ++k) {
            yTemp[i] += step * a[order][k] * this.k[k][i];
        }
    }

    private void updateStage2(int order, int i) {
        k[order][i] = functions[i].call(t + c[order] * step, yTemp);
        y1[i] += step * b[order] * k[order][i];
    }

    @Override
    protected void performStep(AbstractParallelODESolver.ParallelPart part, int start, int stop)
            throws InterruptedException {
        for (int s = 0; s < order; ++s) {
            for (int i = start; i < stop; ++i) {
                if (s == 0) {
                    y1[i] = y0[i];
                }
                updateStage1(s, i);
            }
            synchronize(part, Stage.LOCAL);
            for (int i = start; i < stop; ++i) {
                updateStage2(s, i);
            }
            synchronize(part, s == order - 1 ? Stage.GLOBAL : Stage.LOCAL);
        }
    }

    @Override
    public void setNewState(float[] y0, float tStart, float tFinal, float step)
            throws InvalidInitialConditionsVectorException {
        super.setNewState(y0, tStart, tFinal, step);
        this.k = new float[order][y0.length];
        this.yTemp = new float[y0.length];
    }
}
