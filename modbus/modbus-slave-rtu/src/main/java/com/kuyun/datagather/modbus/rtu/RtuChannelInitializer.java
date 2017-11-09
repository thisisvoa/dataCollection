package com.kuyun.datagather.modbus.rtu;

import com.digitalpetri.modbus.codec.ModbusRequestEncoder;
import com.digitalpetri.modbus.codec.ModbusResponseDecoder;
import com.kuyun.datagather.ProtocolChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

public class RtuChannelInitializer implements ProtocolChannelInitializer {

	private ModbusRequestEncoder encoder = new ModbusRequestEncoder();
	private ModbusResponseDecoder decoder = new ModbusResponseDecoder();
	private RtuMessageHandler messageHandler = new RtuMessageHandler();

	@Override
	public void initChannel(Channel ch) {

		ChannelPipeline pipeline = ch.pipeline();
		pipeline.addLast(new RtuCodec(encoder, decoder));
		pipeline.addLast(messageHandler);
	}

}
