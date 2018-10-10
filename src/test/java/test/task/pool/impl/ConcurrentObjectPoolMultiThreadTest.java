package test.task.pool.impl;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import test.task.pool.IllegalObjectException;
import test.task.pool.NotOpenedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentObjectPoolMultiThreadTest {
    private ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();

    @BeforeMethod
    public void setUp() {
        pool = new ConcurrentObjectPool<>();
        pool.open();
    }

    @Test
    public void testClose() throws Exception {
        String resource1 = "A";
        String resource2 = "B";
        pool.add(resource1);
        pool.add(resource2);

        Flow flow = new Flow(50)
                .addStep(pool::acquire, "acquired1")
                .addStep(pool::acquire, "acquired2")
                .addStep("try close", pool::close, "closed")
                .addStep(() -> pool.release(resource1), "released1")
                .addStep(() -> pool.release(resource2), "released2");

        flow.runAndWait();
        flow.verify("acquired1",
                "acquired2",
                "try close",
                "released1",
                "released2",
                "closed");
    }

    @Test
    public void testRemove() throws Exception {
        String resource1 = "A";
        String resource2 = "B";
        pool.add(resource1);
        pool.add(resource2);

        Flow flow = new Flow(50)
                .addStep(pool::acquire, "acquired1")
                .addStep(pool::acquire, "acquired2")
                .addStep("try remove " + resource1, () -> pool.remove(resource1), "removed " + resource1)
                .addStep("try remove " + resource2, () -> pool.remove(resource2), "removed " + resource2)
                .addStep("release1", () -> pool.release(resource1))
                .addStep("release2", () -> pool.release(resource2));

        flow.runAndWait();
        flow.verify("acquired1",
                "acquired2",
                "try remove " + resource1,
                "try remove " + resource2,
                "release1",
                "removed " + resource1,
                "release2",
                "removed " + resource2);
    }

    @Test
    public void testRemoveWaits() throws Exception {
        String resource1 = "A";
        String resource2 = "B";
        pool.add(resource1);
        pool.add(resource2);

        Flow flow = new Flow(50)
                .addStep(pool::acquire, "acquired1")
                .addStep(pool::acquire, "acquired2")
                .addStep("try remove " + resource1, () -> pool.remove(resource1), "removed " + resource1)
                .addStep("release2", () -> pool.release(resource2))
                .addStep(pool::acquire, "acquired2")
                .addStep("release2", () -> pool.release(resource2))
                .addStep(pool::acquire, "acquired2")
                .addStep("release1", () -> pool.release(resource1));

        flow.runAndWait();
        flow.verify("acquired1",
                "acquired2",
                "try remove " + resource1,
                "release2",
                "acquired2",
                "release2",
                "acquired2",
                "release1",
                "removed " + resource1);
    }

    @Test
    public void testRemoveAndRemoveNow() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(50)
                .addStep(pool::acquire, "acquired1")
                .addStep("try remove", () -> pool.remove(resource1), "removed")
                .addStep("try remove now", () -> pool.removeNow(resource1), "removed now")
                .addStep("release", () -> pool.release(resource1), "released");

        flow.runAndWait();
        flow.verify("acquired1",
                "try remove",
                "try remove now",
                "removed now",
                "removed",
                "release",
                "released");
    }

    @Test
    public void testAcquireAndClose() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(50)
                .addStep(pool::acquire, "acquired1")
                .addStep("try to acquire2", pool::acquire)
                .addStep("try to close now", pool::closeNow, "closed")
                .addStep("release1", () -> pool.release(resource1))
                .addStep("release1", () -> pool.release(resource1));

        flow.runAndWait();
        flow.verify("acquired1",
                "try to acquire2",
                "try to close now",
                "closed",
                "release1",
                "release1");
    }

    @Test
    public void testAcquireAndCloseNow() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(50)
                .addStep(pool::acquire, "acquired1")
                .addStep("try to acquire2", pool::acquire)
                .addStep("try to close now", pool::closeNow, "closed now")
                .addStep("release1", () -> pool.release(resource1))
                .addStep("release1", () -> pool.release(resource1));

        flow.runAndWait();
        flow.verify("acquired1",
                "try to acquire2",
                "try to close now",
                "closed now",
                "release1",
                "release1");
    }

    @Test
    public void testAcquireAndAdd() throws Exception {
        String resource1 = "A";

        Flow flow = new Flow(50)
                .addStep("try to acquire", pool::acquire, "acquired")
                .addStep(() -> pool.add(resource1), "added");

        flow.runAndWait();
        flow.verify("try to acquire",
                "added",
                "acquired");
    }


    @Test
    public void testAcquire() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(50)
                .addStep("try to acquire", pool::acquire, "acquired")
                .addStep("try to acquire", pool::acquire, "acquired")
                .addStep("try to acquire", pool::acquire, "acquired")
                .addStep(() -> pool.release(resource1), "released")
                .addStep(() -> pool.release(resource1), "released");

        flow.runAndWait();
        flow.verify("try to acquire",
                "acquired",
                "try to acquire",
                "try to acquire",
                "released",
                "acquired",
                "released",
                "acquired");
    }

    @Test(enabled = false)
    public void testAcquireAtTheSameTime() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(50)
                .addStep( 0, "try to acquire", pool::acquire, "acquired")
                .addStep( 1,"try to acquire", pool::acquire, "acquired")
                .addStep( 2,"try to acquire", pool::acquire, "acquired")
                .addStep(() -> pool.release(resource1), "released")
                .addStep(() -> pool.release(resource1), "released");

        flow.runAndWait();
        flow.verify("try to acquire",
                "acquired",
                "try to acquire",
                "try to acquire",
                "released",
                "acquired",
                "released",
                "acquired");
    }

    @Test
    public void testAcquireAndCloseAtTheSameTime() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(50)
                .addStep(0, "try to acquire", pool::acquire, "acquired")
                .addStep(1, "try to acquire", pool::acquire, "acquired")
                .addStep(2, "try to acquire", pool::acquire, "acquired")
                .addStep("try to close", pool::close, "closed")
                .addStep(() -> pool.release(resource1), "released")
                .addStep(() -> pool.release(resource1), "released");

        flow.runAndWait();
        flow.verify("try to acquire",
                "acquired",
                "try to acquire",
                "try to acquire",
                "try to close",
                "released",
                "closed",
                "released");
    }

    @Test
    public void testAcquireTimeout() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        Flow flow = new Flow(100)
                .addStep("try to acquire", pool::acquire, "acquired")
                .addStep("try to acquire with timeout", () -> pool.acquire(50, TimeUnit.MILLISECONDS), "timeout")
                .addStep(() -> pool.release(resource1), "released");

        flow.runAndWait();
        flow.verify("try to acquire",
                "acquired",
                "try to acquire with timeout",
                "timeout",
                "released");
    }

    @Test
    public void testAcquireRelease() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        AtomicInteger errorCounter = new AtomicInteger();
        Flow flow = new Flow(0);
        for (int i = 0; i < 10; i++) {
            AtomicReference<String> ref = new AtomicReference<>();
            flow.addStep(0, 1000, () -> {
                try {
                    if (ref.get() == null) {
                        ref.set(pool.acquire());
                    } else {
                        pool.release(ref.getAndSet(null));
                    }
                } catch (Exception e) {
                    errorCounter.incrementAndGet();
                }
            });
        }

        flow.runAndWait();
        Assert.assertEquals(0, errorCounter.get());
    }

    @Test
    public void testAcquireReleaseAddRemove() throws Exception {
        String resource1 = "A";
        pool.add(resource1);

        AtomicInteger errorCounter = new AtomicInteger();
        Flow flow = new Flow(0);
        for (int i = 0; i < 10; i++) {
            AtomicReference<String> ref = new AtomicReference<>();
            flow.addStep(0, 1000, () -> {
                try {
                    if (ref.get() == null) {
                        ref.set(pool.acquire());
                    } else {
                        pool.release(ref.getAndSet(null));
                    }
                } catch (Exception e) {
                    errorCounter.incrementAndGet();
                }
            });

            AtomicReference<String> addRemove = new AtomicReference<>();
            flow.addStep(0, 1000, () -> {
                try {
                    if (addRemove.get() == null) {
                        String newValue = new String();
                        addRemove.set(newValue);
                        pool.add(newValue);
                    } else {
                        pool.remove(addRemove.getAndSet(null));
                    }
                } catch (Exception e) {
                    errorCounter.incrementAndGet();
                }
            });
        }

        flow.runAndWait();
        Assert.assertEquals(0, errorCounter.get());
    }

    private class Flow {
        private final int delayIncrement;
        private List<Step> steps = new ArrayList<>();
        private List<String> log = new CopyOnWriteArrayList<>();
        private CountDownLatch startLatch = new CountDownLatch(1);
        private int currentDelay;

        private Flow(int delayIncrement) {
            this.delayIncrement = delayIncrement;
        }

        private Flow addStep(int delayIncrement, int times, Action action) {
            steps.add(new Step(currentDelay += delayIncrement, times, (l) -> action.perform(), log, startLatch));
            return this;
        }

        private Flow addStep(String before, Action action) {
            steps.add(new Step(currentDelay += delayIncrement, (l) -> {
                l.add(before);
                action.perform();
            }, log, startLatch));
            return this;
        }

        private Flow addStep(String before, Action action, String after) {
            steps.add(new Step(currentDelay += delayIncrement, (l) -> {
                l.add(before);
                action.perform();
                l.add(after);
            }, log, startLatch));
            return this;
        }

        private Flow addStep(int delayIncrement, String before, Action action, String after) {
            steps.add(new Step(currentDelay += delayIncrement, (l) -> {
                l.add(before);
                action.perform();
                l.add(after);
            }, log, startLatch));
            return this;
        }

        private Flow addStep(Action action, String after) {
            steps.add(new Step(currentDelay += delayIncrement, (l) -> {
                action.perform();
                l.add(after);
            }, log, startLatch));
            return this;
        }

        private void runAndWait() throws InterruptedException {
            steps.forEach(Step::start);
            startLatch.countDown();

            for (Step step : steps) {
                step.join();
            }
        }

        private void verify(String... expected) {
            Assert.assertEquals(log.toArray(new String[log.size()]), expected);
        }
    }

    private class Step extends Thread {
        private int times;
        private int delay;
        private LogAction action;
        private List<String> log;
        private CountDownLatch startLatch;

        private Step(int delay, LogAction action, List<String> log, CountDownLatch startLatch) {
            this(delay, 1, action, log, startLatch);
        }

        private Step(int delay, int times, LogAction action, List<String> log, CountDownLatch startLatch) {
            this.startLatch = startLatch;
            this.times = times;
            this.delay = delay;
            this.action = action;
            this.log = log;
        }

        @Override
        public void run() {
            try {
                startLatch.await();
                for (int i = 0; i < times; i++) {
                    Thread.sleep(delay);
                    action.perform(log);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private interface LogAction {

        void perform(List<String> log) throws Exception;

    }

    private interface Action {

        void perform() throws Exception;

    }

}