package com.murielkamgang.network;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.LruCache;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by kamga on 3/11/2017.
 */

/**
 * TCPMessenger will handle the tcp communication and caching between a socket server and the Android application,
 * but also will provide type safe response defined by user in the MainThread to facilitate UI updates.
 * <p>
 * <p>using {@link #getDefaultInstance()} will provide a singleton with the default port 49152</p>
 */
public class TCPMessenger {

    /**
     * Client max cache size, it is set to 5 for now in order not to have too many connected/open socket at the same time
     */
    private static final int CLIENT_CACHE_SIZE = 5;
    /**
     * Executor max thread pool to be spawn
     */
    private static final int MAX_THREAD_POOL_SIZE = 5;
    /**
     * TCPMessenger instances cache per {@link SocketConfig}
     */
    private volatile static ArrayMap<SocketConfig, TCPMessenger> instanceCache = new ArrayMap<>();
    /**
     * {@link SocketConfig} default instance with the default port and default time out
     * <p>
     * <p>{@link SocketConfig} to changes those values</p>
     */
    private static final SocketConfig defaultSocketConfig = new SocketConfig(Constant.TCP_PORT, Constant.DEFAULT_TIME_OUT);

    /**
     * Of course our logger
     */
    private final Logger logger = LoggerFactory.getLogger(TCPMessenger.class);

    /**
     * LRU socket client cache
     */
    private final CSLRUCache clientCache = new CSLRUCache(CLIENT_CACHE_SIZE);
    /**
     * Map to hold callback per request
     */
    private final ConcurrentHashMap<Request, WeakReference<Callback>> commandCallbackMap = new ConcurrentHashMap<>();
    /**
     * Map to hold request per future
     */
    private final ConcurrentHashMap<Request, Future> commandFutureMap = new ConcurrentHashMap<>();
    /**
     * Socket config on this instance
     */
    private final SocketConfig socketConfig;
    /**
     * ExecutorService of this instance
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREAD_POOL_SIZE);
    /**
     * Handler where callback will be invoke
     */
    private final Handler handler;

    /**
     * ObjectMapper
     */
    private ObjectMapper objectMapper;

    /**
     * Private constructor use {@link #getInstance} to get an instance of this
     *
     * @param socketConfig the socket config of this instance
     */
    private TCPMessenger(@NonNull SocketConfig socketConfig) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("TCPMessenger need to be initialized within the UIThread");
        }
        handler = new Handler();
        this.socketConfig = socketConfig;
    }

    /**
     * Get the default instance with default socket config {@link #defaultSocketConfig}
     *
     * @return the TCPMessenger instance with construct with {@link #defaultSocketConfig}
     */
    public static TCPMessenger getDefaultInstance() {
        return getInstance(defaultSocketConfig);
    }

    /**
     * @param socketConfig the socketConfig for the instance
     * @return the TCPMessenger, if instance was already created a new one wont be created unless that instance
     * is release with {@link #releaseInstance(TCPMessenger)}
     */
    public static TCPMessenger getInstance(@NonNull SocketConfig socketConfig) {
        TCPMessenger TCPMessenger = instanceCache.get(socketConfig);
        if (TCPMessenger == null) {
            synchronized (TCPMessenger.class) {
                TCPMessenger = instanceCache.get(socketConfig);
                if (TCPMessenger == null) {
                    TCPMessenger = new TCPMessenger(socketConfig);
                    instanceCache.put(socketConfig, TCPMessenger);
                }
            }
        }

        return TCPMessenger;
    }

    /**
     * Release any previously initialized TCPMessenger instance
     *
     * @param tcpMessenger the instance to be released
     *                           <p>please note that if instance is released it cant be used anymore</p>
     */
    public static void releaseInstance(@NonNull TCPMessenger tcpMessenger) {
        synchronized (TCPMessenger.class) {
            instanceCache.remove(tcpMessenger.getSocketConfig());
            tcpMessenger.shutdown();
        }
    }

    /**
     * Get this instance socketConfig
     *
     * @return the socketConfig of this
     */
    private SocketConfig getSocketConfig() {
        return socketConfig;
    }

    /**
     * Send a request to a specific device
     *
     * @param request           the request
     * @param responseClass the response class object expected
     * @param callback      the callback to be invoked
     * @param <T>           Type of the object expected
     * @return return a future of this request
     */
    public <T> Future sendCommand(final Request request, Class<T> responseClass, final Callback<T> callback) {
        checkCommand(request);

        if (Constant.ENABLE_LOGS) {
            logger.debug("command received {}", request);
            logger.debug("commandCallbackMap.size: {}", commandCallbackMap.size());
        }

        commandCallbackMap.put(request, new WeakReference<Callback>(callback));
        final Future future = enqueueCommand(request, responseClass);
        commandFutureMap.put(request, future);
        return future;
    }

    /**
     * Enqueue the request to the executorService
     *
     * @param request           the request
     * @param responseClass the response class object expected
     * @param <T>           Type of the object expected
     * @return return a future of this request
     * @throws IllegalStateException is thrown if this executorService was shutdown {@link #shutdown()}
     */
    private <T> Future enqueueCommand(final Request request, final Class<T> responseClass) {
        if (executorService.isShutdown()) {
            throw new IllegalStateException("executorService is shutdown");
        }
        return executorService.submit(new Runnable() {
            @Override
            public void run() {
                final Object[][] o = new Object[1][2];
                try {
                    o[0][0] = doSendCommand(request, responseClass);
                } catch (Exception e) {
                    if (Constant.ENABLE_LOGS) {
                        logger.debug("", e);
                    }

                    o[0][1] = e;
                }

                if (commandCallbackMap.containsKey(request)) {
                    final Callback<T> callback = commandCallbackMap.remove(request).get();
                    final Future future = commandFutureMap.remove(request);
                    if (callback == null || future == null || future.isCancelled()) {
                        return;
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            final T response;
                            if ((response = (T) o[0][0]) != null) {
                                if (Constant.ENABLE_LOGS) {
                                    logger.debug("dispatching response {}", response);
                                }
                                callback.onResponse(request, response);
                            } else {
                                if (Constant.ENABLE_LOGS) {
                                    logger.debug("dispatching error {}", o[0][1]);
                                }
                                callback.onError(request, o[0][1] == null ? null : (Throwable) o[0][1]);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Execute the request
     *
     * @param request           the request
     * @param responseClass the response class object expected
     * @param <T>           Type of the object expected
     * @return Type safe response T or null if something went wrong.
     */
    private <T> T doSendCommand(final Request request, Class<T> responseClass) throws IOException {
        initMapper();

        if (Constant.ENABLE_LOGS) {
            logger.debug("doSendCommand for {}", request);
        }

        final Socket client = getClientFor(request);
        T response;
        synchronized (client) {
            try {
                final JsonGenerator jsonWriter = objectMapper.getFactory().createGenerator(client.getOutputStream());
                if (Constant.ENABLE_LOGS) {
                    logger.debug("writing command {} to server", request.cmd);
                }
                jsonWriter.writeObject(request.cmd);
                jsonWriter.flush();

                if (Constant.ENABLE_LOGS) {
                    logger.debug("reading response for command", request.cmd);
                }
                final JsonParser jsonReader = objectMapper.getFactory().createParser(client.getInputStream());
                response = jsonReader.readValueAs(responseClass);
                if (Constant.ENABLE_LOGS) {
                    logger.debug("response for command {} {}", request.cmd, responseClass);
                }
            } catch (SocketException e) {
                try {
                    client.close();
                } catch (Exception e1) {
                    //Ignore
                }
                throw e;
            }
        }

        return response;
    }

    /**
     * Create or get cached socket client for specific request
     *
     * @param request the request
     * @return return the socket client
     */
    private Socket getClientFor(Request request) throws IOException {
        Socket client = clientCache.get(request.ip);

        if (client == null || client.isClosed()) {
            synchronized (clientCache) {
                client = clientCache.get(request.ip);
                if (client != null && !client.isClosed()) {//most have been probably created while thread was blocked.
                    return client;
                }

                if (Constant.ENABLE_LOGS) {
                    logger.debug("creating new client for ip {}", request.ip);
                }

                if (client != null) {
                    if (Constant.ENABLE_LOGS) {
                        logger.debug("force removing broken client for ip {} from cache", request.ip);
                    }
                    clientCache.remove(request.ip);

                    try {
                        client.close();//it could be already closed but for the sake of it.
                    } catch (Exception e) {
                        if (Constant.ENABLE_LOGS) {
                            logger.debug("error while trying to close broken client for ip {}", request.ip);
                        }
                    }

                }

                client = new Socket(request.ip, socketConfig.port);
                client.setSoTimeout(socketConfig.timeOut);
                clientCache.put(request.ip, client);
                if (Constant.ENABLE_LOGS) {
                    logger.debug("new client socket created and cached {}", client);
                }
            }

            return client;
        }

        return client;
    }

    /**
     * Check if a request is valid
     *
     * @param request the request to be examined
     * @throws NullPointerException if the request command or ip is null
     */
    private void checkCommand(Request request) {
        if (request == null) {
            throw new NullPointerException("request cannot be null");
        }

        if (request.cmd == null || request.ip == null) {
            throw new NullPointerException("request or ip cannot be null");
        }
    }

    /**
     * Init the mapper
     */
    private void initMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
    }

    /**
     * Shutdown this instance, clear all cache and close all cached client socket
     * <p>
     * <p>note that this will be useless after this method is called</p>
     */
    public void shutdown() {
        if (Constant.ENABLE_LOGS) {
            logger.debug("shutting down...");
        }

        executorService.shutdownNow();
        commandCallbackMap.clear();
        commandFutureMap.clear();
        clientCache.evictAll();
    }

    /**
     * Callback to be register to {@link TCPMessenger} in order to invoke request callback
     *
     * @param <T> Type T of the expect request response
     */
    public interface Callback<T> {

        /**
         * Invoke when request was successful
         *
         * @param request the request request for this response
         * @param t   the type safe object response
         */
        void onResponse(Request request,T t);

        /**
         * Invoke when an error occur
         *
         * @param request       the request for this response
         * @param throwable the throwable thrown when the error occur
         */
        void onError(Request request, Throwable throwable);


    }

    private static class CSLRUCache extends LruCache<String, Socket> {

        private final Logger logger = LoggerFactory.getLogger(CSLRUCache.class);

        /**
         * @param maxSize for caches that do not override {@link #sizeOf}, this is
         *                the maximum number of entries in the cache. For all other caches,
         *                this is the maximum sum of the sizes of the entries in this cache.
         */
        public CSLRUCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, Socket oldValue, Socket newValue) {
            if (Constant.ENABLE_LOGS) {
                logger.debug("entryRemoved() evicted? {} key: {}, oldClient: {}, newClient: {}", evicted, key, oldValue, newValue);
            }
            if (evicted) {
                try {
                    oldValue.close();
                } catch (Exception e) {
                    if (Constant.ENABLE_LOGS) {
                        logger.debug("error while trying to brutally close evicted client {}", oldValue);
                    }
                }
            }
        }
    }

    /**
     * Request object
     */
    public static class Request {

        private static AtomicInteger atomicInteger = new AtomicInteger(0);
        /**
         * The id of the request, auto incremented each time a new instance of this is created
         */
        private final int id;
        /**
         * The ip of the request
         */
        public String ip;
        /**
         * The command of the request
         */
        public Object cmd;

        public Request(String ip, Object cmd) {
            this.ip = ip;
            this.cmd = cmd;
            id = atomicInteger.incrementAndGet();
        }

        @Override
        public String toString() {
            return "Request{" +
                    "id=" + id +
                    ", ip='" + ip + '\'' +
                    ", cmd=" + cmd +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Request request = (Request) o;

            if (id != request.id) return false;
            if (ip != null ? !ip.equals(request.ip) : request.ip != null) return false;
            return cmd != null ? cmd.equals(request.cmd) : request.cmd == null;

        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + (ip != null ? ip.hashCode() : 0);
            result = 31 * result + (cmd != null ? cmd.hashCode() : 0);
            return result;
        }
    }
}
