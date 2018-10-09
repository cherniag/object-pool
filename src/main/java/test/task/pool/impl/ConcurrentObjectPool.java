package test.task.pool.impl;

import test.task.pool.NotOpenedException;
import test.task.pool.ObjectPool;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentObjectPool<R> implements ObjectPool<R> {
    private volatile boolean isOpened = false;
    private Lock isOpenedLock = new ReentrantLock();
    private Lock acquireLock = new ReentrantLock();
    private Condition acquireCondition = acquireLock.newCondition();
    private Condition releaseCondition = acquireLock.newCondition();
    private Condition removeCondition = acquireLock.newCondition();
    private Set<R> available = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<R> busy = Collections.newSetFromMap(new IdentityHashMap<>());
    private Set<R> removeQueue = Collections.newSetFromMap(new IdentityHashMap<>());

    public void open() {
        try {
            isOpenedLock.lock();
            isOpened = true;
        } finally {
            isOpenedLock.unlock();
        }
    }

    public boolean isOpen() {
        try {
            isOpenedLock.lock();
            return isOpened;
        } finally {
            isOpenedLock.unlock();
        }
    }

    // @TODO: InterruptedException ?
    public void close() throws InterruptedException {
        log(" close");
        try {
            isOpenedLock.lock();
            isOpened = false;
            log(" close: isOpened = false");
        } finally {
            isOpenedLock.unlock();
        }

        // @TODO: separate lock?
        try {
            acquireLock.lock();
            while (!busy.isEmpty()) {
                log(" close: await for release");
                releaseCondition.await();
            }
            cleanUp();
        } finally {
            acquireLock.unlock();
        }
    }

    public void closeNow() {
        try {
            isOpenedLock.lock();
            isOpened = false;
        } finally {
            isOpenedLock.unlock();
        }

        try {
            acquireLock.lock();
            cleanUp();
        } finally {
            acquireLock.unlock();
        }
    }

    // @TODO: InterruptedException ?
    // @TODO: opened lock ?
    public R acquire() throws NotOpenedException, InterruptedException {
        log(" acquire");
        try {
            isOpenedLock.lock();
            checkIsOpened();

            try {
                acquireLock.lock();
                if (available.isEmpty()) {
                    log(" acquire: - no available, await");
                    acquireCondition.await();
                }
                return get(false);
            } finally {
                acquireLock.unlock();
            }
        } finally {
            isOpenedLock.unlock();
        }
    }

    // @TODO: InterruptedException ?
    public R acquire(long timeout, TimeUnit timeUnit) throws NotOpenedException, InterruptedException {
        try {
            isOpenedLock.lock();
            checkIsOpened();

            try {
                acquireLock.lock();
                if (available.isEmpty()) {
                    acquireCondition.await(timeout, timeUnit);
                    return get(true);
                }
                return get(false);
            } finally {
                acquireLock.unlock();
            }
        } finally {
            isOpenedLock.unlock();
        }
    }

    public void release(R resource) {
        try {
            log(" release");
            acquireLock.lock();
            put(resource);
            releaseCondition.signal();
            acquireCondition.signal();
        } finally {
            acquireLock.unlock();
        }
        log(" release finished");
    }

    public boolean add(R resource) {
        try {
            acquireLock.lock();
            boolean modified = available.add(resource);
            acquireCondition.signal();
            return modified;
        } finally {
            acquireLock.unlock();
        }
    }

    // @TODO: InterruptedException ?
    // @TODO: check removeQueue section
    public boolean remove(R resource) throws InterruptedException {
        log(" remove " + resource);
        try {
            acquireLock.lock();
            if (available.remove(resource)) {
                log(" remove: is available");
                return true;
            }
            if (busy.contains(resource)) {
                log(" remove: is busy");
                removeQueue.add(resource);

                while (removeQueue.contains(resource)) {
                    log(" remove: await for release");
                    removeCondition.await();
                }
                log(" remove: removed");
                return true;
            }
        } finally {
            acquireLock.unlock();
        }
        log(" remove: not found");
        return false;
    }
    public boolean removeNow(R resource) {
        try {
            acquireLock.lock();
            if (available.remove(resource)) {
                return true;
            }
            if (busy.contains(resource)) {
                busy.remove(resource);
                return true;
            }
        } finally {
            acquireLock.unlock();
        }
        return false;
    }

    @Override
    public String toString() {
        return "ConcurrentObjectPool{" +
                "available=" + available +
                ", busy=" + busy +
                ", removeQueue=" + removeQueue +
                '}';
    }

    private R get(boolean nullable) {
        Iterator<R> iterator = available.iterator();
        R element = null;
        if (iterator.hasNext()) {
            element = iterator.next();
            iterator.remove();
            busy.add(element);
        }
        if (element == null && !nullable) {
            throw new IllegalArgumentException();
        }
        log(" get " + element);
        return element;
    }

    private void put(R item) {
        log(" put " + item);
        boolean removed = busy.remove(item);
        // @TODO: unknown element?
        if (removed) {
            boolean shouldBeRemoved = removeQueue.remove(item);
            log(" put, shouldBeRemoved = " + shouldBeRemoved);
            if (shouldBeRemoved) {
                removeCondition.signalAll();
            } else {
                available.add(item);
            }
        }
    }

    private void checkIsOpened() throws NotOpenedException {
        if (!isOpened) {
            throw new NotOpenedException();
        }
    }

    private void cleanUp() {
        log(" cleanUp");

        busy.clear();
        available.clear();
        removeQueue.clear();

        releaseCondition.signalAll();
        acquireCondition.signalAll();
        removeCondition.signalAll();
    }

    private void log(String s) {
        System.err.println(new Date() + " " + Thread.currentThread().getName() + s);
    }

}
