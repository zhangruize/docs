package jt;

public class VolatileTest {
    volatile static int count = 0;
    volatile static int lock = 0;
    static int count2 = 0;
    private static int THREAD_COUNT = 2000;

    public static void main(String[] args) {
        Thread[] threads = new Thread[THREAD_COUNT];
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new TestThread();
        }

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].start();
        }
        System.out.println(count);
    }

    private static class TestThread extends Thread {
        @Override
        public void run() {
            while (true) {
                if (lock == 0) {
                    lock = 1;
                    count++;
                    lock = 0;
                    break;
                    // count2++;
                }
            }
        }
    }
}