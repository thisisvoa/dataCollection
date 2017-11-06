package com.kuyun.datagather;

import java.util.concurrent.CompletableFuture;

import io.netty.channel.Channel;

/**
 * Session that hold context for specified Request & Response
 * 
 * @author youjun
 *
 * @param <Req>
 *            Request Data Type
 * @param <Res>
 *            Response Data Type
 */
public interface Session<Req, Res> {

	/**
	 * bind channel
	 * 
	 */
	public void bind(Channel channel);

	/**
	 * start gather routine which will run periodically to gather data from DTU
	 * 
	 * @return
	 */
	public boolean startGather();

	/**
	 * stop gather routine
	 * 
	 * @return
	 */
	public boolean stopGather();

	/**
	 * send adhoc request.
	 * 
	 * @param req
	 * @return the response which returned by DTU
	 */
	public CompletableFuture<Res> sendRequest(Req req);

	/**
	 * save the received data. when implement this method, we need the logic to deal
	 * with adhoc request and routine request. once the data is saved, the request
	 * is processed successfully.
	 * 
	 * @param res
	 */
	public void saveData(Res res);

	/**
	 * return the UUID for this session
	 * 
	 * @return
	 */
	public String getSessionId();
}
