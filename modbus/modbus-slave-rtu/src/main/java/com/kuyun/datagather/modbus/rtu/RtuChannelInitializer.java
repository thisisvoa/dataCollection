package com.kuyun.datagather.modbus.rtu;

import com.digitalpetri.modbus.codec.ModbusRequestEncoder;
import com.digitalpetri.modbus.codec.ModbusResponseDecoder;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.kuyun.datagather.ProtocolChannelInitializer;
import com.kuyun.datagather.ProtocolMessageHandler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

public class RtuChannelInitializer implements ProtocolChannelInitializer {

	private ModbusRequestEncoder encoder = new ModbusRequestEncoder();
	private ModbusResponseDecoder decoder = new ModbusResponseDecoder();
	private ProtocolMessageHandler<ModbusRtuPayload> messageHandler = new ProtocolMessageHandler<>();

	@Override
	public void initChannel(Channel ch) {

		ChannelPipeline pipeline = ch.pipeline();
		pipeline.addLast(new RtuCodec(encoder, decoder));
		pipeline.addLast(messageHandler);
	}

}
