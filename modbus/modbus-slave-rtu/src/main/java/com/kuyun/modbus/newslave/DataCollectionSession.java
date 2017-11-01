package com.kuyun.modbus.newslave;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.print.CancelablePrintJob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.requests.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.requests.ModbusRequest;
import com.digitalpetri.modbus.requests.ReadCoilsRequest;
import com.digitalpetri.modbus.requests.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.requests.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.requests.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.requests.WriteSingleCoilRequest;
import com.digitalpetri.modbus.requests.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.kuyun.common.DeviceUtil;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamSensor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;

public class DataCollectionSession implements Runnable {

	public static final AttributeKey<DataCollectionSession> SERVER_SESSION_KEY = AttributeKey.valueOf("session");

	private static final Logger logger = LoggerFactory.getLogger(DataCollectionSession.class);

	// below properties should saved with Equipment in DB.
	private final static int MIN_RUN_INTERVAL = 1000; // ms
	private final static int TIME_OUT_INTERVAL = 5000; // ms

	private final static int MAX_RETRY_TIMES = 5;

	private DeviceUtil deviceUtil;
	private String deviceId;

	private EamEquipment device;
	private List<EamSensor> sensors;

	private int runInterval;
	// used to identify the heartBeat message
	private byte[] heartBeat; // UTF-8 encoded string.
	private String heartBeatStr;

	private int retryCount = 0;
	private int currentPayloadPosition = 0; // used to check the response is match to the request.
	private int nextPayloadPosition = -1; // used to manually send request
	private long lastRequestTime; // milliseconds
	private ModbusRtuPayload[] allPayload;

	private SessionState currentState = SessionState.IDEL;

	@SuppressWarnings("rawtypes")
	private ScheduledFuture scheduledTask;

	private Channel channel;

	@Override
	public void run() {
		switch (currentState) {
		case IDEL:
			// send request.

			sendRequest(currentPayloadPosition);

			// change state to RECEIVEING_PENDING, since they are running in the same
			// eventloop, no need to do the sync.
			break;

		case RECEIVEING_PENDING:
			// check the timeout setting.
			// if time out change to TIME_OUT.
			// else skip this run loop.

			if (System.currentTimeMillis() - lastRequestTime > TIME_OUT_INTERVAL) {
				currentState = SessionState.TIME_OUT;
			}

			break;

		case TIME_OUT:

			retryCount++;

			if (retryCount > MAX_RETRY_TIMES) {
				logger.error("exceed max retry times, connection closed. device ID [{}]", deviceId);
				channel.close();
			}

			sendRequest(currentPayloadPosition);
			// retry

			break;

		}
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

	public byte[] getHeartBeat() {
		return heartBeat;
	}

	public void setHeartBeat(byte[] heartBeat) {
		this.heartBeat = heartBeat;
	}

	public String getHeartBeatStr() {
		return heartBeatStr;
	}

	public void setHeartBeatStr(String heartBeatStr) {
		this.heartBeatStr = heartBeatStr;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public DeviceUtil getDeviceUtil() {
		return deviceUtil;
	}

	public void setDeviceUtil(DeviceUtil deviceUtil) {
		this.deviceUtil = deviceUtil;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	public DataCollectionSession(DeviceUtil deviceUtil, String deviceId, Channel channel) {
		super();
		this.deviceUtil = deviceUtil;
		this.deviceId = deviceId;
		this.channel = channel;

		startJob();
	}

	public void destory() {
		logger.info("Session destory, device ID {}", deviceId);
		cancelJob();
	}

	public void saveData(ModbusRtuPayload payload, String data) {

		logger.info("device Id = [ {} ]", deviceId);
		logger.info("unit Id = [ {} ]", payload.getUnitId());
		logger.info("client response = [ {} ]", data);

		int code = allPayload[currentPayloadPosition].getModbusPdu().getFunctionCode().getCode();
		int returnCode = payload.getModbusPdu().getFunctionCode().getCode();

		if (payload.getModbusPdu() instanceof ExceptionResponse) {
			returnCode = ((ExceptionResponse) payload.getModbusPdu()).getExceptionCode().getCode() - 0x80;
		}

		if (code == returnCode) {
			// save data
			if (!(payload.getModbusPdu() instanceof ExceptionResponse)) {
				deviceUtil.persistDB(deviceId, payload.getUnitId(), data);
			}

			// reset current state
			currentState = SessionState.IDEL;
			
			// move to next request
			if (nextPayloadPosition == -1) {
				currentPayloadPosition = (currentPayloadPosition + 1) % allPayload.length;
			} else {
				currentPayloadPosition = nextPayloadPosition;
				nextPayloadPosition = -1;
			}

		}

	}

	// load all payload;
	private void loadAllPayload() {

		allPayload = new ModbusRtuPayload[sensors.size()];
		int index = 0;
		for (EamSensor sensor : sensors) {
			ModbusRequest request = buildRequet(sensor);
			allPayload[index++] = new ModbusRtuPayload("", sensor.getSalveId().shortValue(), request);
		}

	}

	private ModbusRequest buildRequet(EamSensor sensor) {
		switch (getFunctionCode(sensor)) {
		case ReadCoils:
			return buildReadCoils(sensor);

		case ReadDiscreteInputs:
			return buildReadDiscreteInputs(sensor);

		case ReadHoldingRegisters:
			return buildReadHoldingRegisters(sensor);

		case ReadInputRegisters:
			return buildReadInputRegisters(sensor);

		case WriteSingleCoil:
			return buildWriteSingleCoil(sensor);

		case WriteSingleRegister:
			return buildWriteSingleRegister(sensor);

		case WriteMultipleCoils:
			return buildWriteMultipleCoils(sensor);

		case WriteMultipleRegisters:
			return buildWriteMultipleRegisters(sensor);

		case MaskWriteRegister:
			return buildMaskWriteRegister(sensor);

		default:
			return null;
		}

	}

	private FunctionCode getFunctionCode(EamSensor sensor) {
		return FunctionCode.fromCode(sensor.getFunctionCode().intValue()).get();
	}

	private ModbusRequest buildReadCoils(EamSensor sensor) {
		int address = sensor.getAddress();
		int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

		return new ReadCoilsRequest(address, quantity);
	}

	private ReadDiscreteInputsRequest buildReadDiscreteInputs(EamSensor sensor) {
		int address = sensor.getAddress();
		int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

		return new ReadDiscreteInputsRequest(address, quantity);
	}

	private ReadHoldingRegistersRequest buildReadHoldingRegisters(EamSensor sensor) {
		int address = sensor.getAddress();
		int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

		return new ReadHoldingRegistersRequest(address, quantity);
	}

	private ReadInputRegistersRequest buildReadInputRegisters(EamSensor sensor) {
		int address = sensor.getAddress();
		int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

		return new ReadInputRegistersRequest(address, quantity);
	}

	private WriteSingleCoilRequest buildWriteSingleCoil(EamSensor sensor) {
		int address = sensor.getAddress();
		boolean value = sensor.getWriteNumber() == 0xFF00;

		return new WriteSingleCoilRequest(address, value);
	}

	private WriteSingleRegisterRequest buildWriteSingleRegister(EamSensor sensor) {
		int address = sensor.getAddress();
		int value = sensor.getWriteNumber();

		return new WriteSingleRegisterRequest(address, value);
	}

	private WriteMultipleCoilsRequest buildWriteMultipleCoils(EamSensor sensor) {
		int address = sensor.getAddress();
		int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;
		int byteCount = sensor.getWriteNumber();
		ByteBuf values = Unpooled.copyInt(byteCount).retain();

		return new WriteMultipleCoilsRequest(address, quantity, values);
	}

	private WriteMultipleRegistersRequest buildWriteMultipleRegisters(EamSensor sensor) {
		int address = sensor.getAddress();
		int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;
		int byteCount = sensor.getWriteNumber();
		ByteBuf values = Unpooled.copyInt(byteCount).retain();

		return new WriteMultipleRegistersRequest(address, quantity, values);
	}

	private MaskWriteRegisterRequest buildMaskWriteRegister(EamSensor sensor) {
		int address = sensor.getAddress();
		int andMask = 1;
		int orMask = 1;

		return new MaskWriteRegisterRequest(address, andMask, orMask);
	}

	// synchronized is needed, start job button may be clicked multiply times.
	synchronized public void startJob() {
		if (scheduledTask == null) {
			device = deviceUtil.getDevice(deviceId);
			sensors = deviceUtil.getSensors(deviceId);

			loadAllPayload();

			heartBeatStr = device.getHeartData();
			if (heartBeatStr != null && !heartBeatStr.isEmpty()) {
				heartBeat = heartBeatStr.getBytes(StandardCharsets.UTF_8);
			}

			runInterval = MIN_RUN_INTERVAL;
			if (device.getModbusRtuPeriod() * 1000 > MIN_RUN_INTERVAL) {
				runInterval = device.getModbusRtuPeriod() * 1000;
			}

			logger.info("Device Modbus Rtu Period=" + device.getModbusRtuPeriod());
			logger.info("runInterval=" + runInterval);

			deviceUtil.setOnline(device);

			// schedule the task running one the same event loop
			scheduledTask = channel.eventLoop().scheduleAtFixedRate(this, 0, runInterval, TimeUnit.MILLISECONDS);

		}
	}

	synchronized public void cancelJob() {

		if (scheduledTask != null) {
			deviceUtil.remove(deviceId);
			deviceUtil.setOffline(device);
			scheduledTask.cancel(false);
			scheduledTask = null;
		}
	}

	private void sendRequest(int position) {
		channel.writeAndFlush(allPayload[position]);
		currentState = SessionState.RECEIVEING_PENDING;
		lastRequestTime = System.currentTimeMillis();
	}

	public boolean sendRequest(EamSensor sensor) {

		for (int i = 0; i < sensors.size(); i++) {
			if (sensor.getSensorId() == sensors.get(i).getSensorId()) {
				nextPayloadPosition = i;
				return true;
			}
		}

		return false;
	}
}
