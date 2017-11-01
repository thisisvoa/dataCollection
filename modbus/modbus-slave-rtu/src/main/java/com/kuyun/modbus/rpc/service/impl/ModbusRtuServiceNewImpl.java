package com.kuyun.modbus.rpc.service.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kuyun.eam.dao.model.EamSensor;
import com.kuyun.modbus.newslave.DataCollectionSession;
import com.kuyun.modbus.rpc.api.ModbusSlaveRtuApiService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Created by user on 2017-08-18.
 */
public class ModbusRtuServiceNewImpl implements ModbusSlaveRtuApiService {
	private static Logger _log = LoggerFactory.getLogger(ModbusRtuServiceNewImpl.class);

	private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	private ConcurrentMap<String, ChannelId> deviceChannels = new ConcurrentHashMap<String, ChannelId>();

	@Override
	public void startJob(String deviceId) {

		Channel channel = channels.find(deviceChannels.get(deviceId));

		if (channel != null) {
			DataCollectionSession session = channel.attr(DataCollectionSession.SERVER_SESSION_KEY).get();
			if (session != null) {
				session.startJob();
			}
		}

	}

	@Override
	public void pauseJob(String deviceId) {
		Channel channel = channels.find(deviceChannels.get(deviceId));

		if (channel != null) {
			DataCollectionSession session = channel.attr(DataCollectionSession.SERVER_SESSION_KEY).get();
			if (session != null) {
				session.cancelJob();
			}
		}

	}

	@Override
	public boolean writeData(String deviceId, EamSensor sensor) {
		boolean result = false;

		Channel channel = channels.find(deviceChannels.get(deviceId));

		if (channel != null) {
			DataCollectionSession session = channel.attr(DataCollectionSession.SERVER_SESSION_KEY).get();
			if (session != null) {
				return session.sendRequest(sensor);
			}
		}

		return result;
	}

	public void registerDeviceChannel(String deviceId, Channel channel) {
		channels.add(channel);
		deviceChannels.put(deviceId, channel.id());
	}
}
