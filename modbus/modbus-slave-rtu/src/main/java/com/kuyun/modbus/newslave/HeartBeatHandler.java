package com.kuyun.modbus.newslave;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * this handler filter out  the heart beat message.
 * @author youjun
 *
 */
public class HeartBeatHandler extends ChannelInboundHandlerAdapter {
	
	private static final Logger logger = LoggerFactory.getLogger(HeartBeatHandler.class);

 
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf buffer = (ByteBuf) msg;
		DataCollectionSession session = ctx.channel().attr(DataCollectionSession.SERVER_SESSION_KEY).get();
		if (session == null) {
			//should never happen.
			logger.error("no session attached when deal heart beat");
			ctx.close();
		}
		 String heartData = session.getDevice().getHeartData();
		 byte[] heartDataBytes = heartData.getBytes(StandardCharsets.UTF_8);
		
	}

}
