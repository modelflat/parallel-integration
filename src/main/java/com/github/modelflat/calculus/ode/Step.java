package com.github.modelflat.calculus.ode;

import java.util.Arrays;

class Step {

    private float t;
    private float[] y;

    Step(float t, float[] y) {
        this.t = t;
        this.y = y;
    }

    public float getT() {
        return t;
    }

    public float[] getY(boolean makeCopy) {
        return makeCopy ? Arrays.copyOf(y, y.length) : y;
    }

    public float getValue(int i) {
        return y[i];
    }
}
