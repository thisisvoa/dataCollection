package com.kuyun.modbus.newslave;

import com.digitalpetri.modbus.codec.ModbusRequestEncoder;
import com.digitalpetri.modbus.codec.ModbusResponseDecoder;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Created by user on 2017-06-01.
 */
public class ModbusRtuSalveInitializer extends ChannelInitializer<SocketChannel> {

	// this handler can be shared, so use only one instance.
	private ModbusRutSlaveHandler rutSlaveHandler = new ModbusRutSlaveHandler();

	// below codec can be shared? suppose to
	private ModbusRequestEncoder encoder = new ModbusRequestEncoder();
	private ModbusResponseDecoder decoder = new ModbusResponseDecoder();

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast(new LoggingHandler(LogLevel.TRACE));
		pipeline.addLast(new DeviceRegisterHandler());
		pipeline.addLast(new ModbusRtuCodec(encoder, decoder));

		// and then business logic.
		pipeline.addLast(rutSlaveHandler);
	}
}
