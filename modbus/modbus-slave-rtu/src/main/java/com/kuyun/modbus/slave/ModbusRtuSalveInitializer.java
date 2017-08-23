package com.kuyun.modbus.slave;

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
public class ModbusRtuSalveInitializer extends ChannelInitializer<SocketChannel>{

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new LoggingHandler(LogLevel.TRACE));
        pipeline.addLast(new ModbusRtuCodec(new ModbusRequestEncoder(), new ModbusResponseDecoder()));

        // and then business logic.
        pipeline.addLast(new ModbusRutSlaveHandler());
    }
}
