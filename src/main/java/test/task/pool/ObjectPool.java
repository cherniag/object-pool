package test.task.pool;

import java.util.concurrent.TimeUnit;

public interface ObjectPool<R> {

    void open();

    boolean isOpen();

    void close() throws InterruptedException;

    void closeNow();

    R acquire() throws NotOpenedException, InterruptedException;

    R acquire(long timeout, TimeUnit timeUnit) throws NotOpenedException, InterruptedException;

    void release(R resource);

    boolean add(R resource);

    boolean remove(R resource) throws InterruptedException;

    boolean removeNow(R resource);

}
