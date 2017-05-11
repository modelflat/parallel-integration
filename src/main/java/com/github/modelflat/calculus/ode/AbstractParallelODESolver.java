package com.github.modelflat.calculus.ode;

import com.github.modelflat.calculus.exceptions.FinalStateAlreadyReachedException;
import com.github.modelflat.calculus.exceptions.InvalidInitialConditionsVectorException;
import com.github.modelflat.calculus.exceptions.NonUniformWorkDistributionException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@SuppressWarnings("WeakerAccess")
public abstract class AbstractParallelODESolver implements Iterator<Step>, Iterable<Step> {

    protected enum Stage {LOCAL, GLOBAL}

    protected static class ParallelPart implements Runnable {

        private AbstractParallelODESolver host;
        private int partNumber;
        private int start;
        private int stop;

        ParallelPart(AbstractParallelODESolver host, int partNumber) {
            this.host = host;
            this.partNumber = partNumber;
            start = partNumber * host.partSize;
            stop = (partNumber + 1) * host.partSize;
        }

        @Override
        public void run() {
            //System.out.println(String.format("part for %d-%d started", start, stop));
            while (host.hasNext() && !host.stop) {
                try {
                    if (host.hasRequested()) {
                        host.performStep(this, start, stop);
                    } else {
                        waitForRequest();
                    }
                } catch (InterruptedException e) {
                    if (!host.stop) {
                        e.printStackTrace();
                    }
                    return;
                }
            }
            host.flowLock.lock();
            try {
                if (host.stop) {
                    host.signalReadyOnce();
                }
            } finally {
                host.flowLock.unlock();
            }
        }

        private void waitForRequest() throws InterruptedException {
            host.flowLock.lock();
            try {
                host.signalReadyOnce();
                while (!host.hasRequested() && !host.stop) {
                    host.request.await();
                }
            } finally {
                host.flowLock.unlock();
            }
        }

        public int getPartNumber() {
            return partNumber;
        }
    }

    protected Function[] functions;

    protected float[] y0;
    protected float[] y1;

    protected float tStart;
    protected float t;
    protected float tFinal;
    protected float step;

    private int threadCount;
    private int partSize;
    private AtomicInteger workPool;
    // TODO probably implement work pool
    /* For now there is a problem with work pool model implementation -- it is unclear how the client
    * can specify LOCAL synchronization points in this case.  */

    private final Object interStageLock = new Object();
    private final Object computationLock = new Object();
    private final Object signalLock = new Object();
    private boolean alreadySignalled = false;
    private CyclicBarrier fence;
    private final Lock flowLock = new ReentrantLock();
    private Condition request;
    private Condition ready;

    private int remaining;
    private boolean executorsStarted = false;
    private boolean stop = false;
    private Thread watcher;
    private Thread[] workers;

    public AbstractParallelODESolver(Function[] functions, int threadCount) throws
            NonUniformWorkDistributionException {
        this.functions = functions;
        this.threadCount = threadCount;
        if (functions.length % threadCount != 0) {
            if (functions.length / threadCount == 0) {
                this.threadCount = functions.length;
            } else {
                throw new NonUniformWorkDistributionException(functions.length, threadCount);
            }
        }
        this.remaining = 0;
        this.fence = new CyclicBarrier(threadCount);
        this.ready = flowLock.newCondition();
        this.request = flowLock.newCondition();
    }

    public void setNewState(float[] y0, float tStart, float tFinal, float step)
            throws InvalidInitialConditionsVectorException {
        this.y0 = y0;
        if (functions.length != y0.length) {
            throw new InvalidInitialConditionsVectorException(
                    "Function count and initial conditions vector size should be equal");
        }
        this.y1 = new float[y0.length];
        this.partSize = y0.length / threadCount;
        this.tStart = tStart;
        this.t = tStart;
        this.tFinal = tFinal;
        this.step = step;
    }

    public final float getCurrentT() {
        return t;
    }

    public final float[] getCurrentY(boolean makeCopy) {
        if (makeCopy) {
            return Arrays.copyOf(y0, y0.length);
        }
        return y0;
    }

    public final int getMaxRemainingStepsCount() {
        return (int)((tFinal - t) / step);
    }

    public final void computeFinalState() {
        if (hasNext()) {
            try {
                computeMoreSteps(getMaxRemainingStepsCount());
            } catch (FinalStateAlreadyReachedException ignored) {
            }
        }
    }

    public final void computeStep() throws FinalStateAlreadyReachedException {
        computeMoreSteps(1);
    }

    public final void computeMoreSteps(int count) throws FinalStateAlreadyReachedException {
        startExecutors();

        synchronized (computationLock) {
            remaining = count;
            flowLock.lock();
            try {
                resetSignalOnce();
                request.signalAll();
                System.out.println("waiting for result");
                ready.await();
                if (remaining > 0 && !stop) {
                    throw new FinalStateAlreadyReachedException(count);
                }
            } catch (InterruptedException e) {
                if (!stop) {
                    e.printStackTrace();
                }
            } finally {
                flowLock.unlock();
            }
        }
    }

    public final void startExecutors() {
        if (!executorsStarted) {
            workers = workers == null ? new Thread[threadCount] : workers;
            stop = false;
            for (int i = 0; i < threadCount; ++i) {
                workers[i] = new Thread(new ParallelPart(AbstractParallelODESolver.this, i),
                        "PDESolver ParallelPart #" + i);
                workers[i].start();
            }
            executorsStarted = true;

            watcher = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < threadCount; ++i) {
                        try {
                            workers[i].join();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    executorsStarted = false;
                }
            }, "PDESolver Watcher Thread");
            watcher.start();
        }
    }

    /**
     * Waits for current computation and then destroys all worker threads
     */
    public final void finish() {
        // if there are executor workers, they all have common requesting thread,
        // which are currently waiting for computation to finish on computationLock object.
        synchronized (computationLock) {
            interrupt();
        }
    }

    public final void interrupt() {
        if (workers == null) {
            return;
        }
        stop = true;
        for (Thread thread : workers) {
            thread.interrupt();
        }
        try {
            watcher.join();
        } catch (InterruptedException ignored) {
        }
        workers = null;
    }

    protected final void synchronize(ParallelPart part, Stage stage) throws InterruptedException {
        synchronized (interStageLock) {
            if (fence.getNumberWaiting() == threadCount - 1 && stage == Stage.GLOBAL) {
                moveToNextStep();
            }
        }
        try {
            fence.await();
        } catch (BrokenBarrierException ignored) {
        }
    }

    private boolean hasRequested() {
        return remaining > 0;
    }

    private void moveToNextStep() {
        remaining--;
        float[] temp = y0;
        y0 = y1;
        y1 = temp;
        t += step;
    }

    private void signalReadyOnce() {
        synchronized (signalLock) {
            if (alreadySignalled) {
                return;
            }
            ready.signalAll();
            alreadySignalled = true;
        }
    }

    private void resetSignalOnce() {
        synchronized (signalLock) {
            if (!alreadySignalled) {
                return;
            }
            alreadySignalled = false;
        }
    }

    @Override
    public Iterator<Step> iterator() {
        return this;
    }

    @Override
    public void forEach(Consumer<? super Step> action) {
        action.accept(new Step(t, y0));
    }

    @Override
    public boolean hasNext() {
        return t < tFinal;
    }

    @Override
    public Step next() {
        computeMoreSteps(1);
        return new Step(t, y0);
    }

    protected abstract void performStep(ParallelPart part, int start, int stop) throws InterruptedException;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        interrupt();
        functions = null;
        y1 = null;
        y0 = null;
    }
}
