package com.kuyun.datagather.modbus.rtu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.kuyun.common.DeviceUtil;
import com.kuyun.datagather.AbstractSession;

import io.netty.channel.Channel;

public class RtuSession extends AbstractSession<ModbusRtuPayload, ModbusRtuPayload> {

	private static final Logger logger = LoggerFactory.getLogger(RtuSession.class);

	private String id;

	private DeviceUtil deviceUtil;

	/**
	 * init session and do the right things
	 * 
	 * @param id
	 * @param deviceUtil
	 * @param channel
	 */
	public RtuSession(String id, DeviceUtil deviceUtil, Channel channel) {
		super();
		this.id = id;
		this.deviceUtil = deviceUtil;
		// load all equipment.

		// setup equipment request sub task.

		// start gathering.
		bind(channel);
		startGather();
	}

	@Override
	protected boolean isResponseRight(ModbusRtuPayload req, ModbusRtuPayload res) {

		if (req == null || res == null) {
			logger.error("encounter empty request or response. session id [{}]", getSessionId());
			return false;
		}
		if (req.getUnitId() != res.getUnitId())
			return false;

		ModbusPdu resPdu = res.getModbusPdu();
		ModbusPdu reqPdu = req.getModbusPdu();

		if (resPdu instanceof ExceptionResponse) {
			if (((ExceptionResponse) resPdu).getExceptionCode().getCode() - 0x80 != reqPdu.getFunctionCode()
					.getCode()) {
				return false;
			}
		} else if (resPdu.getFunctionCode() != reqPdu.getFunctionCode()) {
			return false;
		}

		return true;
	}

	@Override
	public String getSessionId() {
		return id;
	}

	@Override
	protected void saveRoutionRequestData(ModbusRtuPayload res) {
		// save the response data to the data base

	}

}
