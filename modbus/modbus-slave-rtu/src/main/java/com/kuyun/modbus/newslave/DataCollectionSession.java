package com.kuyun.modbus.newslave;

import java.util.List;

import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamSensor;

import io.netty.util.AttributeKey;

public class DataCollectionSession implements Runnable {

	public static final AttributeKey<DataCollectionSession> SERVER_SESSION_KEY = AttributeKey.valueOf("session");

	// below properties should saved with Equipment in DB.
	private final static int MIN_INTERVAL = 1000; // ms
	private final static int TIME_OUT_INTERVAL = 20000; // ms

	private EamEquipment device;
	private List<EamSensor> sensors;

	private int retryCount = 0;
	private ModbusRtuPayload lastPayload; // used to check the response is match to the request.

	// private SchedulerFuture job;

	@Override
	public void run() {

	}

	public static enum SessionState {
		IDEL, RECEIVEING_PENDING, TIME_OUT,

	}

	public EamEquipment getDevice() {
		return device;
	}

	public void setDevice(EamEquipment device) {
		this.device = device;
	}

	public List<EamSensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<EamSensor> sensors) {
		this.sensors = sensors;
	}
}
