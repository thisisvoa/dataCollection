package com.kuyun.datagather;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public abstract class AbstractSession<Req, Res> implements Session<Req, Res> {

	@SuppressWarnings("rawtypes")
	public static final AttributeKey<Session> SERVER_SESSION_KEY = AttributeKey.valueOf("session");

	protected int runningInterval = 1000; // default to 1 second
	protected int maxTryCount = 5; // 5 times
	protected int timeoutInterval = 10000; // 10s

	private SessionState state = SessionState.IDEL;
	private int retryCount = 0;
	private long lastRequestTime = 0;

	private static final Logger logger = LoggerFactory.getLogger(AbstractSession.class);

	private ConcurrentLinkedQueue<Decorator<Req>> requestQueue = new ConcurrentLinkedQueue<>();

	private AtomicReference<CompletableFuture<Res>> responseFuture = new AtomicReference<CompletableFuture<Res>>(null);

	private ScheduledFuture<?> gatherRoutine = null;

	protected Channel channel = null;

	@Override
	public void bind(Channel channel) {
		this.channel = channel;
		channel.attr(SERVER_SESSION_KEY).set(this);
	}

	@Override
	public boolean startGather() {
		try {
			gatherRoutine = channel.eventLoop().scheduleAtFixedRate(new GatherRunner(), 0, runningInterval,
					TimeUnit.MILLISECONDS);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public boolean stopGather() {
		try {
			gatherRoutine.cancel(false);
			gatherRoutine = null;
			requestQueue.clear();// clean the request queue
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public CompletableFuture<Res> sendRequest(Req req) {

		if (!responseFuture.compareAndSet(null, new CompletableFuture<Res>())) {
			return CompletableFuture.completedFuture(null);
		}

		requestQueue.add(new Decorator<Req>(req, true));

		return responseFuture.get();
	}

	protected void sendRoutineRequest(Req req) {
		requestQueue.add(new Decorator<Req>(req, false));
	}

	/**
	 * implement by the sub class to save the related data;
	 * 
	 * @param res
	 */
	protected abstract void saveRoutionRequestData(Req req, Res res);

	@Override
	public void saveData(Res res) {

		Decorator<Req> latestRequest = requestQueue.poll(); // remove the request from head

		if (!isResponseRight(latestRequest.request, res)) {
			return;
		}

		if (latestRequest.isAdhoc) {
			responseFuture.get().complete(res);
			responseFuture.set(null);
		} else {
			saveRoutionRequestData(latestRequest.request, res);
		}

		retryCount = 0;
		state = SessionState.IDEL;
	}

	/**
	 * check the response is match to request
	 * 
	 * @param req
	 * @param res
	 * @return
	 */
	protected abstract boolean isResponseRight(Req req, Res res);

	public static enum SessionState {
		IDEL, RECEIVEING_PENDING, TIME_OUT
	}

	/**
	 * the real task runner
	 * 
	 * @author youjun
	 *
	 */
	public class GatherRunner implements Runnable {

		@Override
		public void run() {
			switch (state) {
			case IDEL:
				Decorator<Req> req = requestQueue.peek();
				if (req != null) {
					channel.writeAndFlush(req.request);
					lastRequestTime = System.currentTimeMillis();
					state = SessionState.RECEIVEING_PENDING;
				}
				break;
			case RECEIVEING_PENDING:
				if (System.currentTimeMillis() - lastRequestTime > timeoutInterval) {
					state = SessionState.TIME_OUT;
				}
				break;
			case TIME_OUT:
				if (++retryCount >= maxTryCount) {
					logger.error("exceed max retry times, connection closed. device ID [{}]", getSessionId());
					channel.close();
				}
				state = SessionState.IDEL;
				break;
			}

		}

	}

	/**
	 * helper class to flag the request is adhoc or not.
	 * 
	 * @author youjun
	 *
	 * @param <Req>
	 */
	public static class Decorator<Req> {

		private Req request;
		private boolean isAdhoc;

		public Decorator(Req req, boolean isAdhoc) {
			this.request = req;
			this.isAdhoc = isAdhoc;
		}

	}

}
