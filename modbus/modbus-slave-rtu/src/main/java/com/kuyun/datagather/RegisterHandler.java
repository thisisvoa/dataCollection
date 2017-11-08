package com.kuyun.datagather;

import static io.netty.util.CharsetUtil.UTF_8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kuyun.common.DeviceUtil;
import com.kuyun.common.util.SpringContextUtil;
import com.kuyun.datagather.modbus.rtu.RtuServiceImpl;
import com.kuyun.datagather.modbus.rtu.RtuSession;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public class RegisterHandler extends ChannelInboundHandlerAdapter {

	private static final int DTU_ID_LENGTH = 16;

	private String dtuId = "";

	private DeviceUtil deviceUtil = SpringContextUtil.getBean(DeviceUtil.class);
	private RtuServiceImpl rpcService = SpringContextUtil.getBean(RtuServiceImpl.class);

	private DataGatherChannelInitializer initializer;

	private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

		logger.info("New DTU is registering...");

		ByteBuf buffer = (ByteBuf) msg;

		if (buffer.isReadable(DTU_ID_LENGTH)) {
			ByteBuf dtuIdBuf = buffer.readBytes(DTU_ID_LENGTH);
			dtuId = dtuIdBuf.toString(UTF_8);
			ReferenceCountUtil.release(dtuIdBuf);
			ReferenceCountUtil.release(msg);// more data will be discard, if it comes with register message
		}

		// message will not populate to other channel handler, before device is
		// registered. no need to call ctx.fireChannelRead(msg);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		// unregister itself from the channel pipeline after the device is registered.
		if (!dtuId.isEmpty() && deviceUtil.isDtuId(dtuId)) {
			// if (deviceUtil.isDevice(dtuId)) {
			// logger.info("DTU {} is recognized.", dtuId);
			// initSession(ctx, dtuId);
			// logger.info("DTU {} is online now.", dtuId);
			// } else {
			//
			// logger.info("DTU {} cannot be recognized.", dtuId);
			// logger.info("connection is closing");
			//
			// // close connection to save resource;
			// ctx.close();
			// }

			// as per the dtu protocol type
			logger.info("DTU {} is recognized.", dtuId);
			// install handlers
			initializer.rtu.initChannel(ctx.channel());
			// init session
			new RtuSession(dtuId, deviceUtil, ctx.channel());
			// register to rpc service
			rpcService.registerDtuChannel(dtuId, ctx.channel());
			logger.info("DTU {} is online now.", dtuId);

			ctx.pipeline().remove(this);
		}
	}

	public RegisterHandler(DataGatherChannelInitializer initializer) {
		super();
		this.initializer = initializer;
	}
}
