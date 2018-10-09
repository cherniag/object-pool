package test.task.pool.impl;

import org.testng.Assert;
import org.testng.annotations.Test;
import test.task.pool.NotOpenedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentObjectPoolTest {

    @Test
    public void testOpen() throws Exception {
        ConcurrentObjectPool pool = new ConcurrentObjectPool();
        Assert.assertFalse(pool.isOpen());
        pool.open();
        Assert.assertTrue(pool.isOpen());
    }

    @Test
    public void testClose() throws Exception {
        ConcurrentObjectPool pool = new ConcurrentObjectPool();
        pool.open();
        pool.close();
        Assert.assertFalse(pool.isOpen());
    }

    @Test
    public void testCloseNow() throws Exception {
        ConcurrentObjectPool pool = new ConcurrentObjectPool();
        pool.open();
        pool.closeNow();
        Assert.assertFalse(pool.isOpen());
    }

    @Test
    public void testAdd() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        Assert.assertTrue(pool.add("A"));
    }

    @Test(expectedExceptions = NotOpenedException.class)
    public void testAcquireNotOpened() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.add("A");
        Assert.assertEquals(pool.acquire(), "A");
    }

    @Test
    public void testAcquire() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        pool.add("A");
        Assert.assertEquals(pool.acquire(), "A");
    }

    @Test(expectedExceptions = NotOpenedException.class)
    public void testAcquireWithTimeoutNotOpened() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.add("A");
        pool.acquire(1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testAcquireWithTimeout() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        pool.add("A");
        Assert.assertEquals(pool.acquire(1, TimeUnit.MILLISECONDS), "A");
    }

    @Test
    public void testRelease() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        pool.add("A");
        String acquired = pool.acquire();
        pool.release(acquired);
    }

    @Test
    public void testRemove() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        String resource = "A";
        pool.add(resource);
        Assert.assertTrue(pool.remove(resource));
    }

    @Test
    public void testRemoveNotExisting() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        String resource = "A";
        pool.add(resource);
        Assert.assertFalse(pool.remove("B"));
    }

    @Test
    public void testRemoveNow() throws Exception {
        ConcurrentObjectPool<String> pool = new ConcurrentObjectPool<>();
        pool.open();
        String resource = "A";
        pool.add(resource);
        Assert.assertTrue(pool.removeNow(resource));
    }

    @Test
    public void testCloseConcurrent() throws Exception {

    }
}