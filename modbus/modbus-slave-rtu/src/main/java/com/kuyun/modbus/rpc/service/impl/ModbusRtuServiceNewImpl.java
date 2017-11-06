package com.kuyun.modbus.rpc.service.impl;

import java.util.concurrent.*;

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
		DataCollectionSession session = getCollectionSession(deviceId);
		if (session != null) {
			session.startJob();
		}
	}

	@Override
	public void pauseJob(String deviceId) {
		DataCollectionSession session = getCollectionSession(deviceId);
		if (session != null) {
			session.cancelJob();
		}
	}

	private DataCollectionSession getCollectionSession(String deviceId) {
		DataCollectionSession result = null;
		ChannelId channelId  = deviceChannels.get(deviceId);
		if (channelId != null){
			Channel channel = channels.find(channelId);
			if (channel != null){
				result = channel.attr(DataCollectionSession.SERVER_SESSION_KEY).get();
			}
		}
		return result;
	}

	@Override
	public boolean writeData(String deviceId, EamSensor sensor) {
		_log.info("Device Id [ {} ] Write Data [ {} ]", deviceId, sensor);
		boolean result = false;
		DataCollectionSession session = getCollectionSession(deviceId);
		if (session != null) {
			try {
				result = session.sendAdhocRequest(sensor).get();

			} catch (InterruptedException | ExecutionException e) {
				_log.error("Device Id [ {} ] Write Data [ {} ] Error [ {} ]", deviceId, sensor, e.getMessage());
			}
		}

		_log.info("Write Data Result [ {} ]", result);
		return result;
	}

	public void registerDeviceChannel(String deviceId, Channel channel) {
		channels.add(channel);
		deviceChannels.put(deviceId, channel.id());
	}
}
