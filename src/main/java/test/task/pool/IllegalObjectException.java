package test.task.pool;

public class IllegalObjectException extends PoolException {

    public IllegalObjectException() {

    }

    public IllegalObjectException(String message) {
        super(message);
    }
}
