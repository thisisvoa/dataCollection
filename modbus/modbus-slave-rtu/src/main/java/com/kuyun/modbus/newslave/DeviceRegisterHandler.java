package com.kuyun.modbus.newslave;

import static com.kuyun.common.util.CommonUtil.DEVICE_ID_LENGTH;
import static io.netty.util.CharsetUtil.UTF_8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kuyun.common.DeviceUtil;
import com.kuyun.common.util.SpringContextUtil;
import com.kuyun.eam.dao.model.EamEquipment;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public class DeviceRegisterHandler extends ChannelInboundHandlerAdapter {

	private String deviceId = "";

	private DeviceUtil deviceUtil = SpringContextUtil.getBean(DeviceUtil.class);

	private static final Logger logger = LoggerFactory.getLogger(DeviceRegisterHandler.class);

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

		logger.info("New Device is registering...");

		ByteBuf buffer = (ByteBuf) msg;

		if (buffer.isReadable(DEVICE_ID_LENGTH)) {
			ByteBuf deviceIdBuf = buffer.readBytes(DEVICE_ID_LENGTH);
			deviceId = deviceIdBuf.toString(UTF_8);
			ReferenceCountUtil.release(deviceIdBuf);
			ReferenceCountUtil.release(msg);// more data will be discard, if it comes with register message.

			if (deviceUtil.isDevice(deviceId)) {
				initSession(ctx, deviceId);
				logger.info("Device {} is online now.", deviceId);
			} else {

				logger.info("Device {} cannot be recognized.", deviceId);
				logger.info("connection is closing");

				// close connection to save resource;
				ctx.close();
			}
		}

		// message will not populate to other channel handler, before device is
		// registered. no need to call ctx.fireChannelRead(msg);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		// unregister itself from the channel pipeline after the device is registered.
		if (!deviceId.isEmpty()) {
			ctx.pipeline().remove(this);
		}
	}

	// load related
	private void initSession(ChannelHandlerContext ctx, String deviceId) {
		logger.info("Device {} is recognized.", deviceId);

		EamEquipment device = deviceUtil.getDevice(deviceId);

		logger.info("device Id from DB = [ {} ]", device.getEquipmentId());

		deviceUtil.setOnline(device);
		
		// ChannelJob job = new ChannelJob(ctx.channel(), device);
		//
		// Pair<ChannelId, ChannelJob> myPair = new Pair<>(ctx.channel().id(), job);
		// channelMap.put(deviceId, myPair);
		//
		// logger.info("device Id from DB = [ {} ]", job.getDevice().getEquipmentId());
		//
		// if (WORKING.getCode().equalsIgnoreCase(device.getCollectStatus())) {
		// job.run();
		// }

	}
}
