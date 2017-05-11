import com.github.modelflat.calculus.ode.ParallelEuler;
import com.github.modelflat.calculus.ode.Function;
import com.github.modelflat.calculus.ode.ParallelRK;
import com.github.modelflat.calculus.exceptions.InvalidInitialConditionsVectorException;
import com.github.modelflat.calculus.exceptions.NonUniformWorkDistributionException;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static void main(String[] args) {
        //test();
        //performance();
        //System.out.println("leaving main()");
    }

    public static void test() {
        final float gamma = -.1f;
        final float omega = .5f;

        Function[] functions = new Function[] {
                new Function() {
                    @Override
                    public float call(float t, float[] y) {
                        return y[1];
                    }
                },
                new Function() {
                    @Override
                    public float call(float t, float[] y) {
                        return -(2 * gamma * y[1] + y[0] * omega * omega);
                    }
                }
        };

        float[] initial = new float[] {1, 1};

        //RK r;
        ParallelEuler r;
        try {
            //r = new RK(functions, 2, 4);
            //r.setNewState(initial, 0, 1, 0.1f);
            r = new ParallelEuler(functions, 2);
            r.setNewState(initial, 0, 1, 0.1f);
        } catch (NonUniformWorkDistributionException | InvalidInitialConditionsVectorException e) {
            e.printStackTrace();
            return;
        }

        long time = System.nanoTime();
        /*
        for (Step s : r) {
            System.out.println(s.getT() + " " +Arrays.toString(s.getY(false)));
        }
        */
        r.computeFinalState();

        System.out.println(2 + " " + (System.nanoTime() - time) / 1000000f);

        System.out.println(r.getCurrentT());
        System.out.println(Arrays.toString(r.getCurrentY(true)));

    }

    private static long fact(int n) {
        long r = 1;
        for (int i = 1; i < n; ++i) {
            r *= i;
        }
        return r;
    }

    public static void performance() {

        int order = 4;

        final int n = 8*5*3*10*2;

        System.out.println(String.format("testing performance for N = %d functions of complexity N" +
                "\n(overall complexity is (N*(order-1)!+N*N*order)/p operations (%d)/p, where p - thread count)",
                n, n*(fact(order - 1)) + n * order * n));

        Function[] functions = new Function[n];
        for (int i = 0; i < n; ++i) {
             functions[i] = new Function() {
                        @Override
                        public float call(float t, float[] y) {
                            float s = t;
                            for (int i = 0; i < n; i++) {
                                s += Math.sin(i - n);
                            }
                            return s/n;
                        }
                    };
        }
        System.out.println("p\t\ttime spent, ms");

        int[] test = new int[] {/*1,2,3,4,5,6,8,10,*/12,24,48,96,300};

        for (int threadCount : test) {
            float[] initial = new float[n];
            for (int j = 0; j < n; j++) {
                initial[j] = (float) Math.random();
            }

            final ParallelRK r;
            try {
                r = ParallelRK.createRK4(functions, threadCount);
                r.setNewState(initial, 0, 1, 0.1f);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            long time = System.nanoTime();
            r.computeStep();
            System.out.println(threadCount + "\t\t" + (System.nanoTime() - time) / 1000000f);
            r.finish();
        }
    }

}
