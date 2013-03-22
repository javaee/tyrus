package org.glassfish.tyrus.core;

import javax.naming.InitialContext;
import javax.websocket.WebSocketContainer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base WebSocket container.
 *
 * Client and Server containers extend this to provide additional functionality.
 *
 * @author Jitendra Kotamraju
 */
public abstract class BaseContainer implements WebSocketContainer {
    private final ExecutorService executorService;

    public BaseContainer() {
        this.executorService = newExecutorService();
    }

    protected ExecutorService getExecutorService() {
        return executorService;
    }

    private static ExecutorService newExecutorService() {
        ExecutorService es = null;
        // Get the default MangedExecutorService, if available
        try {
            InitialContext ic = new InitialContext();
            es = (ExecutorService) ic.lookup("java:comp/DefaultManagedExecutorService");
        } catch (Exception e) {
            // ignore
        }
        if (es == null) {
            es = Executors.newCachedThreadPool(new DaemonThreadFactory());
        }
        return es;
    }

    private static class DaemonThreadFactory implements ThreadFactory {
        static final AtomicInteger poolNumber = new AtomicInteger(1);
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String namePrefix;

        DaemonThreadFactory() {
            namePrefix = "tyrus-" + poolNumber.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(null, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

}
