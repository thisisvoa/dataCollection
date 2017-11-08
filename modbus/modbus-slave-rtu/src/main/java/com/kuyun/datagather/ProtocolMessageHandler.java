package com.kuyun.datagather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * a protocol pipeline consist with below Netty Handler:
 * 
 *    Message Codec //each protocol will have its own message codec, cannot be shared with channel
 *    Message Handler // each protocol will share the logic to process message, can be shared with the same protocol channel
 *    
 * @author youjun
 *
 * @param <Res>
 */
@ChannelHandler.Sharable
public class ProtocolMessageHandler<Res> extends SimpleChannelInboundHandler<Res> {
	private static final Logger logger = LoggerFactory.getLogger(ProtocolMessageHandler.class);

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Session<?, ?> session = ctx.channel().attr(AbstractSession.SERVER_SESSION_KEY).get();
		if (session != null) {
			logger.info("[{}] channelInactive", session.getSessionId());
			session.stopGather();
		}

	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Res payload) throws Exception {
		@SuppressWarnings("unchecked")
		Session<?, Res> session = ctx.channel().attr(AbstractSession.SERVER_SESSION_KEY).get();
		logger.info("get response payload [{}] from channel [{}]", payload.toString(), session.getSessionId());
		session.saveData(payload);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}
}
