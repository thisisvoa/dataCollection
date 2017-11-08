package com.kuyun.datagather.modbus.rtu;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import com.digitalpetri.modbus.requests.ModbusRequest;
import com.kuyun.common.DeviceUtil;
import com.kuyun.common.util.SpringContextUtil;
import com.kuyun.eam.dao.model.EamEquipment;
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
import org.springframework.util.StringUtils;

/**
 * 
 * 
 * @author youjun
 *
 */
public class RtuServiceImpl implements ModbusSlaveRtuApiService {
	private static Logger _log = LoggerFactory.getLogger(RtuServiceImpl.class);
	private DeviceUtil deviceUtil = null;

	private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
	private ConcurrentMap<String, ChannelId> dtuChannels = new ConcurrentHashMap<String, ChannelId>();

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
		init();
		Session<?, ?> result = null;
		String dtuId = deviceUtil.getDtuId(deviceId);
		if (!StringUtils.isEmpty(dtuId)){
			ChannelId channelId = dtuChannels.get(dtuId);
			if (channelId != null) {
				Channel channel = channels.find(channelId);
				if (channel != null) {
					result = channel.attr(AbstractSession.SERVER_SESSION_KEY).get();
				}
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
				ModbusRtuPayload payload = buildModbusRtuPayload(deviceId, sensor, session);
				if (payload != null){
					result = (session.sendRequest(payload).get() != null);
				}
			} catch (InterruptedException | ExecutionException e) {
				_log.error("Device Id [ {} ] Write Data [ {} ] Error [ {} ]", deviceId, sensor, e.getMessage());
			}
		}

		_log.info("Write Data Result [ {} ]", result);
		return result;
	}

	private ModbusRtuPayload buildModbusRtuPayload(String deviceId, EamSensor sensor, Session<ModbusRtuPayload, ?> session){
		ModbusRtuPayload payload = null;
		if (session instanceof RtuSession){
			ModbusRequest request = ((RtuSession)session).buildRequet(sensor);
			EamEquipment device = deviceUtil.getDevice(deviceId);
			if (device != null){
				payload = new ModbusRtuPayload("", device.getSalveId().shortValue(), request);
			}
		}
		return payload;
	}

	public void registerDtuChannel(String dtuId, Channel channel) {
		channels.add(channel);
		dtuChannels.put(dtuId, channel.id());
	}

	private void init(){
		if(deviceUtil == null){
			deviceUtil = SpringContextUtil.getBean(DeviceUtil.class);
		}
	}
}
