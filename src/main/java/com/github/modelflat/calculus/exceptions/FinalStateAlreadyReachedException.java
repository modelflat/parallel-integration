package com.github.modelflat.calculus.exceptions;

public class FinalStateAlreadyReachedException extends RuntimeException {

    public FinalStateAlreadyReachedException(int overflow) {
        super(String.format("Cannot do more than %d: final state already reached", overflow));
    }

}
