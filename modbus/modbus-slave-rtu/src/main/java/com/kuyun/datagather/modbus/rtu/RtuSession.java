package com.kuyun.datagather.modbus.rtu;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;
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
import com.kuyun.datagather.AbstractSession;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamSensor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

public class RtuSession extends AbstractSession<ModbusRtuPayload, ModbusRtuPayload> {

	private static final Logger logger = LoggerFactory.getLogger(RtuSession.class);

	private String dtuId;

	private DeviceUtil deviceUtil;

	/**
	 * init session and do the right things
	 * 
	 * @param dtuid
	 * @param deviceUtil
	 * @param channel
	 */
	public RtuSession(String dtuId, DeviceUtil deviceUtil, Channel channel) {
		super();
		this.dtuId = dtuId;
		this.deviceUtil = deviceUtil;

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
		return dtuId;
	}

	@Override
	protected void saveRoutionRequestData(ModbusRtuPayload res) {

		// TODO: save the data to db
		// deviceUtil.persistDB(deviceId, unitId, allData);

	}

	@Override
	public boolean startGather() {
		initEquipmentRunners();
		return super.startGather();
	}

	@Override
	public boolean stopGather() {
		stopEquipmentRunners();
		return super.stopGather();
	}

	// private functions:

	private List<EamEquipment> devices;
	private List<java.util.concurrent.ScheduledFuture<?>> futures;

	private void stopEquipmentRunners() {
		if (futures != null) {
			for (ScheduledFuture<?> f : futures) {
				f.cancel(false);
			}
			futures = null;
			devices = null;
		}
	}

	private void initEquipmentRunners() {

		devices = deviceUtil.getDevices(dtuId);
		futures = new ArrayList<>();
		for (EamEquipment d : devices) {
			ScheduledFuture<?> future = channel.eventLoop().scheduleAtFixedRate(new EquipmentRequestRunner(d), 0,
					d.getModbusRtuPeriod(), TimeUnit.MILLISECONDS);
			futures.add(future);
		}

	}

	private class EquipmentRequestRunner implements Runnable {

		// private EamEquipment device;
		// TODO: this payload should merge the same operation code into one request?
		private ModbusRtuPayload[] allPayload;
		private int currentPosition = 0;

		@Override
		public void run() {
			sendRoutineRequest(allPayload[currentPosition]);
			currentPosition++;
			if (currentPosition >= allPayload.length) {
				currentPosition = 0;
			}

		}

		public EquipmentRequestRunner(EamEquipment device) {
			super();
			// this.device = device;
			allPayload = loadAllPayload(device);
		}

	}

	// load all payload;
	private ModbusRtuPayload[] loadAllPayload(EamEquipment device) {

		List<EamSensor> sensors = deviceUtil.getSensors(device.getEquipmentId());
		ModbusRtuPayload[] allPayload = new ModbusRtuPayload[sensors.size()];
		int index = 0;
		for (EamSensor sensor : sensors) {
			if (getFunctionCode(sensor).isRead()) {
				ModbusRequest request = buildRequet(sensor);
				allPayload[index++] = new ModbusRtuPayload("", device.getSalveId().shortValue(), request);
			}
		}

		return allPayload;
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

}
