package test.task.pool.impl;

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
        try {
            isOpenedLock.lock();
            // @TODO: separate lock?
            try {
                acquireLock.lock();
                while (!busy.isEmpty()) {
                    releaseCondition.await();
                }
                cleanUp();
            } finally {
                acquireLock.unlock();
            }
        } finally {
            isOpenedLock.unlock();
        }
    }

    public void closeNow() {
        try {
            isOpenedLock.lock();
            try {
                acquireLock.lock();
                cleanUp();
            } finally {
                acquireLock.unlock();
            }
        } finally {
            isOpenedLock.unlock();
        }
    }

    // @TODO: InterruptedException ?
    public R acquire() throws NotOpenedException, InterruptedException {
        try {
            isOpenedLock.lock();
            checkIsOpened();

            try {
                acquireLock.lock();
                if (available.isEmpty()) {
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

    public void release(R resource) throws NotOpenedException {
        try {
            isOpenedLock.lock();
            checkIsOpened();
            try {
                acquireLock.lock();
                put(resource);
                releaseCondition.signal();
                acquireCondition.signal();
            } finally {
                acquireLock.unlock();
            }
        } finally {
            isOpenedLock.unlock();
        }
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
        return element;
    }

    private void put(R item) {
        boolean removed = busy.remove(item);
        // @TODO: unknown element?
        if (removed) {
            if (removeQueue.remove(item)) {
                removeCondition.signal();
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
        isOpened = false;

        busy.clear();
        available.clear();
        removeQueue.clear();

        releaseCondition.signalAll();
        acquireCondition.signalAll();
        removeCondition.signalAll();
    }

}
