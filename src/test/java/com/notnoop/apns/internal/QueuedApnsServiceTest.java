package com.notnoop.apns.internal;

import static org.junit.Assert.*;

import java.util.concurrent.Semaphore;

import org.junit.Test;
import static org.mockito.Mockito.*;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.ApnsService;

public class QueuedApnsServiceTest {

    @Test(expected=IllegalStateException.class)
    public void sendWithoutStarting() {
        QueuedApnsService service = new QueuedApnsService(null);
        service.push(notification);
    }

    ApnsNotification notification = new ApnsNotification("2342", "{}");

    @Test
    public void pushEvantually() {
        ConnectionStub connection = spy(new ConnectionStub(0, 1));
        ApnsService service = newService(connection, null);

        service.push(notification);
        connection.semaphor.acquireUninterruptibly();

        verify(connection, times(1)).sendMessage(notification);
    }

    @Test
    public void dontBlock() {
        final int delay = 10000;
        ConnectionStub connection = spy(new ConnectionStub(delay, 2));
        QueuedApnsService queued =
            new QueuedApnsService(new ApnsServiceImpl(connection, null));
        queued.start();
        long time1 = System.currentTimeMillis();
        queued.push(notification);
        queued.push(notification);
        long time2 = System.currentTimeMillis();
        assertTrue("queued.push() blocks", (time2 - time1) < delay);

        connection.interrupt();
        connection.semaphor.acquireUninterruptibly();
        verify(connection, times(2)).sendMessage(notification);

        queued.stop();
    }

    protected ApnsService newService(ApnsConnection connection, ApnsFeedbackConnection feedback) {
        ApnsService service = new ApnsServiceImpl(connection, null);
        ApnsService queued = new QueuedApnsService(service);
        queued.start();
        return queued;
    }

    static class ConnectionStub extends ApnsConnection {
        Semaphore semaphor;
        int delay;

        public ConnectionStub(int delay, int expectedCalls) {
            super(null, null, 80);
            this.semaphor = new Semaphore(1-expectedCalls);
            this.delay = delay;
        }

        volatile boolean stop;

        protected synchronized void sendMessage(ApnsNotification m) {
            long time = System.currentTimeMillis();
            while (!stop && (System.currentTimeMillis() < time + delay));
            semaphor.release();
        }

        protected void interrupt() {
            stop = true;
        }

    }
}
