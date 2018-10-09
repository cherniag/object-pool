package test.task.pool.impl;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class MultiThreadConcurrentObjectPoolTest {

    @Test
    public void testClose() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        String resource = "A";
        pool.add(resource);
        List<String> log = new ArrayList<>();
        Step step1 = new Step(50, () -> {
            pool.acquire();
            log.add("acquired");
        });
        Step step2 = new Step(100, () -> {
            log.add("try close");
            pool.close();
            log.add("closed");
        });
        Step step3 = new Step(150, () -> {
            pool.release(resource);
            log.add("released");
        });
        step1.start();
        step2.start();
        step3.start();

        step1.join();
        step2.join();
        step3.join();

        Assert.assertEquals(log.toArray(new String[log.size()]), new String[]{"acquired", "try close", "released", "closed"});
    }

    private class Step extends Thread {
        private int times;
        private int delay;
        private Action action;

        private Step(int times, int delay, Action action) {
            this.times = times;
            this.delay = delay;
            this.action = action;
        }

        private Step(int delay, Action action) {
            this(1, delay, action);
        }

        @Override
        public void run() {
            for (int i = 0; i < times; i++) {
                try {
                    Thread.sleep(delay);
                    action.perform();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    interface Action {

        void perform() throws Exception;

    }
}