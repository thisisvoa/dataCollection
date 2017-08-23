/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kuyun.modbus.master;

import com.codahale.metrics.*;
import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.ModbusResponseException;
import com.digitalpetri.modbus.ModbusTimeoutException;
import com.digitalpetri.modbus.codec.ModbusRequestEncoder;
import com.digitalpetri.modbus.codec.ModbusResponseDecoder;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.requests.ModbusRequest;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.digitalpetri.modbus.responses.ModbusResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ModbusRtuMaster {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<Short, PendingRequest<? extends ModbusResponse>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger transactionId = new AtomicInteger(0);

    private final Map<String, Metric> metrics = new ConcurrentHashMap<>();

    private final Counter requestCounter = new Counter();
    private final Counter responseCounter = new Counter();
    private final Counter lateResponseCounter = new Counter();
    private final Counter timeoutCounter = new Counter();
    private final Timer responseTimer = new Timer();

    private final ChannelManager channelManager;

    private final ModbusRtuMasterConfig config;

    public ModbusRtuMaster(ModbusRtuMasterConfig config) {
        this.config = config;

        channelManager = new ChannelManager(this);

        metrics.put(metricName("request-counter"), requestCounter);
        metrics.put(metricName("response-counter"), responseCounter);
        metrics.put(metricName("late-response-counter"), lateResponseCounter);
        metrics.put(metricName("timeout-counter"), timeoutCounter);
        metrics.put(metricName("response-timer"), responseTimer);
    }

    public ModbusRtuMasterConfig getConfig() {
        return config;
    }

    public CompletableFuture<ModbusRtuMaster> connect() {
        CompletableFuture<ModbusRtuMaster> future = new CompletableFuture<>();

        channelManager.getChannel().whenComplete((ch, ex) -> {
            if (ch != null) future.complete(ModbusRtuMaster.this);
            else future.completeExceptionally(ex);
        });

        return future;
    }

    public CompletableFuture<ModbusRtuMaster> disconnect() {
        return channelManager.disconnect().thenApply(v -> this);
    }

    public <T extends ModbusResponse> CompletableFuture<T> sendRequest(ModbusRequest request, int unitId) {
        CompletableFuture<T> future = new CompletableFuture<>();

        channelManager.getChannel().whenComplete((ch, ex) -> {
            if (ch != null) {
                short txId = (short) transactionId.incrementAndGet();

                Timeout timeout = config.getWheelTimer().newTimeout(t -> {
                    if (t.isCancelled()) return;

                    PendingRequest<? extends ModbusResponse> timedOut = pendingRequests.remove(txId);
                    if (timedOut != null) {
                        timedOut.promise.completeExceptionally(new ModbusTimeoutException(config.getTimeout()));
                        timeoutCounter.inc();
                    }
                }, config.getTimeout().getSeconds(), TimeUnit.SECONDS);

                Timer.Context context = responseTimer.time();

                pendingRequests.put(txId, new PendingRequest<>(future, timeout, context));

                ch.writeAndFlush(new ModbusRtuPayload(String.valueOf(txId), (short) unitId, request)).addListener(f -> {
                    if (!f.isSuccess()) {
                        PendingRequest<?> p = pendingRequests.remove(txId);
                        if (p != null) {
                            p.promise.completeExceptionally(f.cause());
                            p.timeout.cancel();
                        }
                    }
                });

                requestCounter.inc();
            } else {
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    private void onChannelRead(ChannelHandlerContext ctx, ModbusRtuPayload payload) throws Exception {
        ModbusPdu modbusPdu = payload.getModbusPdu();

        if (modbusPdu instanceof ModbusResponse) {
            config.getExecutor().submit(() -> handleResponse(payload.getTransactionId(), payload.getUnitId(), (ModbusResponse) modbusPdu));
        } else {
            logger.error("Unexpected ModbusPdu: {}", modbusPdu);
        }
    }

    private void handleResponse(String transactionId, short unitId, ModbusResponse response) {
        PendingRequest<?> pending = pendingRequests.remove(transactionId);

        if (pending != null) {
            responseCounter.inc();

            pending.context.stop();
            pending.timeout.cancel();

            if (response instanceof ExceptionResponse) {
                pending.promise.completeExceptionally(new ModbusResponseException((ExceptionResponse) response));
            } else {
                pending.promise.complete(response);
            }
        } else {
            lateResponseCounter.inc();
            ReferenceCountUtil.release(response);

            logger.debug("Received response for unknown transactionId: {}", transactionId);
        }
    }

    private void onExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught: {}", cause.getMessage(), cause);

        failPendingRequests(cause);

        ctx.close();
    }

    private void failPendingRequests(Throwable cause) {
        List<PendingRequest<?>> pending = new ArrayList<>(pendingRequests.values());
        pending.forEach(p -> p.promise.completeExceptionally(cause));
        pendingRequests.clear();
    }

    public MetricSet getMetricSet() {
        return () -> metrics;
    }

    public Counter getRequestCounter() {
        return requestCounter;
    }

    public Counter getResponseCounter() {
        return responseCounter;
    }

    public Counter getLateResponseCounter() {
        return lateResponseCounter;
    }

    public Counter getTimeoutCounter() {
        return timeoutCounter;
    }

    public Timer getResponseTimer() {
        return responseTimer;
    }

    private String metricName(String name) {
        String instanceId = config.getInstanceId().orElse(null);
        return MetricRegistry.name(ModbusRtuMaster.class, instanceId, name);
    }

    public static CompletableFuture<Channel> bootstrap(ModbusRtuMaster master, ModbusRtuMasterConfig config) {
        CompletableFuture<Channel> future = new CompletableFuture<>();

        Bootstrap bootstrap = new Bootstrap();

        config.getBootstrapConsumer().accept(bootstrap);

        bootstrap.group(config.getEventLoop())
            .channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.getTimeout().toMillis())
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    //ch.pipeline().addLast(new ModbusRtuCodec(new ModbusRequestEncoder(), new ModbusResponseDecoder()));
                    ch.pipeline().addLast(new ModbusRtuMasterHandler(master));
                }
            })
            .connect(config.getAddress(), config.getPort())
            .addListener((ChannelFuture f) -> {
                if (f.isSuccess()) {
                    future.complete(f.channel());
                } else {
                    future.completeExceptionally(f.cause());
                }
            });

        return future;
    }

    private static class ModbusRtuMasterHandler extends SimpleChannelInboundHandler<ModbusRtuPayload> {

        private final ModbusRtuMaster master;

        private ModbusRtuMasterHandler(ModbusRtuMaster master) {
            this.master = master;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ModbusRtuPayload msg) throws Exception {
            master.onChannelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            master.onExceptionCaught(ctx, cause);
        }

    }

    private static class PendingRequest<T> {

        private final CompletableFuture<ModbusResponse> promise = new CompletableFuture<>();

        private final Timeout timeout;
        private final Timer.Context context;

        @SuppressWarnings("unchecked")
        private PendingRequest(CompletableFuture<T> future, Timeout timeout, Timer.Context context) {
            this.timeout = timeout;
            this.context = context;

            promise.whenComplete((r, ex) -> {
                if (r != null) {
                    try {
                        future.complete((T) r);
                    } catch (ClassCastException e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    future.completeExceptionally(ex);
                }
            });
        }

    }

}
