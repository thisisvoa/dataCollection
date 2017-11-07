package com.kuyun.datagather.modbus.rtu;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.kuyun.datagather.AbstractSession;
import com.kuyun.datagather.Session;
import com.kuyun.eam.dao.model.EamSensor;
import com.kuyun.modbus.rpc.api.ModbusSlaveRtuApiService;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * 
 * 
 * @author youjun
 *
 */
public class RtuServiceImpl implements ModbusSlaveRtuApiService {
	private static Logger _log = LoggerFactory.getLogger(RtuServiceImpl.class);

	private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	private ConcurrentMap<String, ChannelId> deviceChannels = new ConcurrentHashMap<String, ChannelId>();

	@Override
	public void startJob(String deviceId) {
		Session<?, ?> session = getSession(deviceId);
		if (session != null) {
			session.startGather();
		}
	}

	@Override
	public void pauseJob(String deviceId) {
		Session<?, ?> session = getSession(deviceId);
		if (session != null) {
			session.stopGather();
		}
	}

	private Session<?, ?> getSession(String deviceId) {
		Session<?, ?> result = null;
		ChannelId channelId = deviceChannels.get(deviceId);
		if (channelId != null) {
			Channel channel = channels.find(channelId);
			if (channel != null) {
				result = channel.attr(AbstractSession.SERVER_SESSION_KEY).get();
			}
		}
		return result;
	}

	@Override
	public boolean writeData(String deviceId, EamSensor sensor) {

		_log.info("Device Id [ {} ] Write Data [ {} ]", deviceId, sensor);

		boolean result = false;

		@SuppressWarnings("unchecked")
		Session<ModbusRtuPayload, ?> session = (Session<ModbusRtuPayload, ?>) getSession(deviceId);
		if (session != null) {
			try {
				// TODO: here should construct the right payload
				ModbusRtuPayload payload = new ModbusRtuPayload("", (short) 1, null);
				result = (session.sendRequest(payload).get() != null);
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
