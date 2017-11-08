package com.kuyun.datagather.modbus.rtu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.kuyun.datagather.AbstractSession;
import com.kuyun.datagather.Session;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * a protocol pipeline consist with below Netty Handler:
 * 
 * Message Codec //each protocol will have its own message codec, cannot be
 * shared with channel Message Handler // each protocol will share the logic to
 * process message, can be shared with the same protocol channel. due to the
 * generic error we need extends the SimpleChannelInboundHandler directly
 * 
 * @author youjun
 *
 * @param <I>
 * 
 *            since SimpleChannelInboundHandler use I as the typeparameter and
 *            doing some magic things, we follow up.
 */
@ChannelHandler.Sharable
public class RtuMessageHandler extends SimpleChannelInboundHandler<ModbusRtuPayload> {
	private static final Logger logger = LoggerFactory.getLogger(RtuMessageHandler.class);

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		Session<?, ?> session = ctx.channel().attr(AbstractSession.SERVER_SESSION_KEY).get();
		if (session != null) {
			logger.info("[{}] channelInactive", session.getSessionId());
			session.stopGather();
		}

	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, ModbusRtuPayload payload) throws Exception {
		@SuppressWarnings("unchecked")
		Session<?, ModbusRtuPayload> session = ctx.channel().attr(AbstractSession.SERVER_SESSION_KEY).get();
		logger.info("get response payload [{}] from channel [{}]", payload.toString(), session.getSessionId());
		session.saveData(payload);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}
}
