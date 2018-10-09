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
        String resource1 = "A";
        String resource2 = "B";
        pool.add(resource1);
        pool.add(resource2);
        List<String> log = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(100, () -> {
            pool.acquire();
            log.add("acquired1");
        }));
        steps.add(new Step(150, () -> {
            pool.acquire();
            log.add("acquired2");
        }));
        steps.add(new Step(200, () -> {
            log.add("try close");
            pool.close();
            log.add("closed");
        }));
        steps.add(new Step(300, () -> {
            pool.release(resource1);
            log.add("released1");
        }));
        steps.add(new Step(350, () -> {
            pool.release(resource2);
            log.add("released2");
        }));

        steps.forEach(Step::start);

        for (Step step : steps) {
            step.join();
        }

        Assert.assertEquals(log.toArray(new String[log.size()]),
                new String[]{"acquired1", "acquired2", "try close", "released1", "released2", "closed"});
    }

    @Test
    public void testRemove() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        String resource1 = "A";
        String resource2 = "B";
        pool.add(resource1);
        pool.add(resource2);
        List<String> log = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(100, () -> {
            pool.acquire();
            log.add("acquired1");
        }));
        steps.add(new Step(150, () -> {
            pool.acquire();
            log.add("acquired2");
        }));
        steps.add(new Step(200, () -> {
            log.add("try remove " + resource1);
            pool.remove(resource1);
            log.add("removed " + resource1);
        }));
        steps.add(new Step(250, () -> {
            log.add("try remove " + resource2);
            pool.remove(resource2);
            log.add("removed " + resource2);
        }));
        steps.add(new Step(300, () -> {
            log.add("release1");
            pool.release(resource1);
        }));
        steps.add(new Step(350, () -> {
            log.add("release2");
            pool.release(resource2);
        }));

        steps.forEach(Step::start);

        for (Step step : steps) {
            step.join();
        }

        Assert.assertEquals(log.toArray(new String[log.size()]),
                new String[]{"acquired1",
                        "acquired2",
                        "try remove " + resource1,
                        "try remove " + resource2,
                        "release1",
                        "removed " + resource1,
                        "release2",
                        "removed " + resource2});
    }

    @Test
    public void testAcquireAndClose() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        String resource1 = "A";
        pool.add(resource1);
        List<String> log = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(100, () -> {
            pool.acquire();
            log.add("acquired1");
        }));
        steps.add(new Step(150, () -> {
            log.add("try to acquire2");
            pool.acquire();
        }));
        steps.add(new Step(200, () -> {
            log.add("try to close");
            pool.close();
            log.add("closed");
        }));
        steps.add(new Step(250, () -> {
            log.add("release1");
            pool.release(resource1);
        }));
        steps.add(new Step(300, () -> {
            log.add("release1");
            pool.release(resource1);
        }));

        steps.forEach(Step::start);

        for (Step step : steps) {
            step.join();
        }

        Assert.assertEquals(log.toArray(new String[log.size()]),
                new String[]{"acquired1",
                        "try to acquire2",
                        "try to close",
                        "release1",
                        "release1",
                        "closed",
                        });
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