package test.task.pool;

import java.util.concurrent.TimeUnit;

public interface ObjectPool<R> {

    void open();

    boolean isOpen();

    void close() throws InterruptedException;

    void closeNow();

    R acquire() throws NotOpenedException, InterruptedException;

    R acquire(long timeout, TimeUnit timeUnit) throws NotOpenedException, InterruptedException;

    void release(R resource) throws IllegalObjectException;

    boolean add(R resource) throws IllegalObjectException;

    boolean remove(R resource) throws InterruptedException, IllegalObjectException;

    boolean removeNow(R resource) throws IllegalObjectException;

}
