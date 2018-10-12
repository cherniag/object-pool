package test.task.pool.impl;

import test.task.pool.IllegalObjectException;
import test.task.pool.NotOpenedException;
import test.task.pool.ObjectPool;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentObjectPool<R> implements ObjectPool<R> {
    private volatile boolean isOpened = false;
    private final Lock isOpenedLock = new ReentrantLock();
    private final Lock acquireLock = new ReentrantLock();
    private final Condition acquireCondition = acquireLock.newCondition();
    private final Condition releaseCondition = acquireLock.newCondition();
    private final Condition removeCondition = acquireLock.newCondition();
    private final Set<R> available = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<R> busy = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<R> removeQueue = Collections.newSetFromMap(new IdentityHashMap<>());

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

    public void close() throws InterruptedException {
        try {
            isOpenedLock.lock();
            isOpened = false;
        } finally {
            isOpenedLock.unlock();
        }

        try {
            acquireLock.lock();
            while (!busy.isEmpty()) {
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


    public R acquire() throws NotOpenedException, InterruptedException {
        checkIsOpened();

        try {
            acquireLock.lock();
            while (available.isEmpty() && isOpened) {
                acquireCondition.await();
            }
            return get(false);
        } finally {
            acquireLock.unlock();
        }
    }

    public R acquire(long timeout, TimeUnit timeUnit) throws NotOpenedException, InterruptedException {
        validateTimeout(timeout, timeUnit);
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
    }

    public void release(R resource) throws IllegalObjectException {
        validateResource(resource);

        try {
            acquireLock.lock();
            put(resource);
            releaseCondition.signal();
            acquireCondition.signal();
        } finally {
            acquireLock.unlock();
        }
    }

    public boolean add(R resource) throws IllegalObjectException {
        validateResource(resource);

        try {
            acquireLock.lock();
            boolean modified = available.add(resource);
            acquireCondition.signal();
            return modified;
        } finally {
            acquireLock.unlock();
        }
    }

    public boolean remove(R resource) throws InterruptedException, IllegalObjectException {
        validateResource(resource);
        try {
            acquireLock.lock();
            if (available.remove(resource)) {
                return true;
            }
            if (busy.contains(resource)) {
                removeQueue.add(resource);

                while (removeQueue.contains(resource)) {
                    removeCondition.await();
                }
                return true;
            }
        } finally {
            acquireLock.unlock();
        }
        return false;
    }

    public boolean removeNow(R resource) throws IllegalObjectException {
        validateResource(resource);

        try {
            acquireLock.lock();
            if (available.remove(resource)) {
                return true;
            }
            if (removeQueue.contains(resource)) {
                removeQueue.remove(resource);
                removeCondition.signalAll();
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

    private R get(boolean nullable) throws NotOpenedException {
        try {
            isOpenedLock.lock();
            if (!isOpened) {
                throw new NotOpenedException();
            }
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
            return element;
        } finally {
            isOpenedLock.unlock();
        }
    }

    private void put(R item) {
        boolean removed = busy.remove(item);
        // @TODO: unknown element?
        if (removed) {
            boolean shouldBeRemoved = removeQueue.remove(item);
            if (shouldBeRemoved) {
                removeCondition.signalAll();
            } else {
                available.add(item);
            }
        }
    }

    private void checkIsOpened() throws NotOpenedException {
        try {
            isOpenedLock.lock();
            if (!isOpened) {
                throw new NotOpenedException();
            }
        } finally {
            isOpenedLock.unlock();
        }
    }

    private void cleanUp() {
        busy.clear();
        available.clear();
        removeQueue.clear();

        releaseCondition.signalAll();
        acquireCondition.signalAll();
        removeCondition.signalAll();
    }

    private void validateResource(R resource) throws IllegalObjectException {
        if (resource == null) {
            throw new IllegalObjectException("Object can not be null");
        }
    }

    private void validateTimeout(long timeout, TimeUnit timeUnit) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Timeout should not be negative");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("Time unit should not be null");
        }
    }
}
