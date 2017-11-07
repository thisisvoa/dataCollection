package com.kuyun.datagather;

import com.kuyun.datagather.modbus.rtu.RtuChannelInitializer;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * 
 * @author youjun
 *
 */
public class DataGatherChannelInitializer extends ChannelInitializer<SocketChannel> {

	// init handler installer here to save resource.
	// Protocol Handler Installer
	ProtocolChannelInitializer rtu = new RtuChannelInitializer();

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast(new LoggingHandler(LogLevel.TRACE));
		pipeline.addLast(new RegisterHandler(this));

	}
}
