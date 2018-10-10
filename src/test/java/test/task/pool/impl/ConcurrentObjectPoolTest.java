package test.task.pool.impl;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import test.task.pool.IllegalObjectException;
import test.task.pool.NotOpenedException;

import java.util.concurrent.TimeUnit;

public class ConcurrentObjectPoolTest {
    private ConcurrentObjectPool<String> pool;

    @BeforeMethod
    public void setUp() {
        pool = new ConcurrentObjectPool<>();
    }

    @Test
    public void testOpen() throws Exception {
        Assert.assertFalse(pool.isOpen());
        pool.open();
        Assert.assertTrue(pool.isOpen());
    }

    @Test
    public void testClose() throws Exception {
        pool.open();
        pool.close();
        Assert.assertFalse(pool.isOpen());
    }

    @Test
    public void testCloseNow() throws Exception {
        pool.open();
        pool.closeNow();
        Assert.assertFalse(pool.isOpen());
    }

    @Test
    public void testAdd() throws Exception {
        pool.open();
        Assert.assertTrue(pool.add("A"));
    }

    @Test
    public void testAddNotOpened() throws Exception {
        Assert.assertTrue(pool.add("A"));
    }

    @Test(expectedExceptions = IllegalObjectException.class)
    public void testAddNull() throws Exception {
        pool.open();
        pool.add(null);
    }

    @Test(expectedExceptions = NotOpenedException.class)
    public void testAcquireNotOpened() throws Exception {
        pool.add("A");
        Assert.assertEquals(pool.acquire(), "A");
    }

    @Test
    public void testAcquire() throws Exception {
        pool.open();
        pool.add("A");
        Assert.assertEquals(pool.acquire(), "A");
    }

    @Test(expectedExceptions = NotOpenedException.class)
    public void testAcquireWithTimeoutNotOpened() throws Exception {
        pool.add("A");
        pool.acquire(1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testAcquireWithTimeout() throws Exception {
        pool.open();
        pool.add("A");
        Assert.assertEquals(pool.acquire(1, TimeUnit.MILLISECONDS), "A");
    }

    @Test
    public void testRelease() throws Exception {
        pool.open();
        pool.add("A");
        String acquired = pool.acquire();
        pool.release(acquired);
    }

    @Test(expectedExceptions = IllegalObjectException.class)
    public void testReleaseNull() throws Exception {
        pool.open();
        pool.release(null);
    }

    @Test
    public void testReleaseClosed() throws Exception {
        pool.open();
        String resource = "A";
        pool.add(resource);
        pool.close();
        pool.release(resource);
    }

    @Test
    public void testRemove() throws Exception {
        pool.open();
        String resource = "A";
        pool.add(resource);
        Assert.assertTrue(pool.remove(resource));
    }

    @Test(expectedExceptions = IllegalObjectException.class)
    public void testRemoveNull() throws Exception {
        pool.open();
        pool.remove(null);
    }

    @Test
    public void testRemoveNotOpened() throws Exception {
        String resource = "A";
        pool.add(resource);
        Assert.assertTrue(pool.remove(resource));
    }

    @Test
    public void testRemoveClosed() throws Exception {
        pool.open();
        String resource = "A";
        pool.add(resource);
        pool.close();
        Assert.assertFalse(pool.remove(resource));
    }

    @Test
    public void testRemoveNotExisting() throws Exception {
        pool.open();
        String resource = "A";
        pool.add(resource);
        Assert.assertFalse(pool.remove("B"));
    }

    @Test
    public void testRemoveNow() throws Exception {
        pool.open();
        String resource = "A";
        pool.add(resource);
        Assert.assertTrue(pool.removeNow(resource));
    }

    @Test(expectedExceptions = IllegalObjectException.class)
    public void testRemoveNowNull() throws Exception {
        pool.open();
        pool.removeNow(null);
    }

    @Test
    public void testRemoveNowNotOpened() throws Exception {
        String resource = "A";
        pool.add(resource);
        Assert.assertTrue(pool.removeNow(resource));
    }

    @Test
    public void testRemoveNowClosed() throws Exception {
        pool.open();
        String resource = "A";
        pool.add(resource);
        pool.close();
        Assert.assertFalse(pool.removeNow(resource));
    }

}