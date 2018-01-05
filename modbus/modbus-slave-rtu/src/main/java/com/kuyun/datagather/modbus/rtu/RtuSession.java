package com.kuyun.datagather.modbus.rtu;

import static io.netty.buffer.Unpooled.buffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import com.digitalpetri.modbus.responses.ReadCoilsResponse;
import com.digitalpetri.modbus.responses.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse;
import com.kuyun.common.DeviceUtil;
import com.kuyun.datagather.AbstractSession;
import com.kuyun.eam.dao.model.EamDtu;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamSensor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

public class RtuSession extends AbstractSession<ModbusRtuPayload, ModbusRtuPayload> {

	private static final Logger logger = LoggerFactory.getLogger(RtuSession.class);

	private String dtuId;

	private DeviceUtil deviceUtil;

	private int MAX_ADDRESS_INTERVAL = 5;

	private Map<ModbusRtuPayload, List<EamSensor>> map = new HashMap<>();

	/**
	 * init session and do the right things
	 * 
	 * @param dtuId
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
		if (req.getUnitId() != res.getUnitId()){
			return false;
		}


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
	protected void saveRoutionRequestData(ModbusRtuPayload req, ModbusRtuPayload res) {

		ByteBuf buffer =  buffer(128);

		if (res.getModbusPdu() instanceof ExceptionResponse){
			int exceptionCode = ((ExceptionResponse) res.getModbusPdu()).getExceptionCode().getCode();
			logger.info("exceptionCode="+ exceptionCode);
		}else {
			switch (res.getModbusPdu().getFunctionCode()) {
				case ReadCoils:
					buffer = ((ReadCoilsResponse)res.getModbusPdu()).getCoilStatus();
					break;

				case ReadDiscreteInputs:
					buffer = ((ReadDiscreteInputsResponse)res.getModbusPdu()).getInputStatus();
					break;

				case ReadHoldingRegisters:
					buffer = ((ReadHoldingRegistersResponse)res.getModbusPdu()).getRegisters();
					break;

				case ReadInputRegisters:
					buffer = ((ReadInputRegistersResponse)res.getModbusPdu()).getRegisters();
					break;

				case WriteSingleCoil:
					break;

				case WriteSingleRegister:
					break;

				case WriteMultipleCoils:
					break;

				case WriteMultipleRegisters:
					break;

				case MaskWriteRegister:
					break;
				default:

					break;
			}
		}

		String data = ByteBufUtil.hexDump(buffer);

		logger.info("dtu Id = [ {} ]", dtuId);
		logger.info("salve Id = [ {} ]", res.getUnitId());
		logger.info("client response = [ {} ]", data);
		List<EamSensor> sensors = map.get(req);


		deviceUtil.persistDB(dtuId, res.getUnitId(), data, sensors);

	}

	@Override
	public boolean startGather() {
		initEquipmentRunners();
		return super.startGather();
	}

	@Override
	public boolean startGather(String deviceId) {
		boolean result = false;
		EamEquipment d = deviceUtil.getDevice(deviceId);
		if (d != null){
			ScheduledFuture<?> future = channel.eventLoop().scheduleAtFixedRate(new EquipmentRequestRunner(d), 0,
					getModbusRtuPeriod(d) * 1000 , TimeUnit.MILLISECONDS);
			mapFuture.put(d.getEquipmentId(), future);
			//deviceUtil.setOnline(d);
			result = true;
		}
		return result;
	}

	@Override
	public boolean stopGather() {
		stopEquipmentRunners();
		return super.stopGather();
	}

	@Override
	public boolean stopGather(String deviceId) {
		boolean result = false;
		ScheduledFuture<?> f = mapFuture.get(deviceId);
		if (f != null){
			f.cancel(false);
			mapFuture.remove(f);
			EamEquipment d = deviceUtil.getDevice(deviceId);
			if ( d != null){
				deviceUtil.setOffline(d);
			}
			result = true;
		}
		return result;
	}

	// private functions:

	private Map<String, java.util.concurrent.ScheduledFuture<?>> mapFuture = new ConcurrentHashMap<>();

	private void stopEquipmentRunners() {
		for ( Map.Entry<String, ScheduledFuture<?>> entry : mapFuture.entrySet()){
			String deviceId = entry.getKey();
			EamEquipment d = deviceUtil.getDevice(deviceId);
			if ( d != null){
				deviceUtil.setOffline(d);
			}
			ScheduledFuture<?> f = entry.getValue();
			f.cancel(false);

		}
		mapFuture.clear();
	}

	private void initEquipmentRunners() {
		List<EamEquipment> devices = deviceUtil.getDevices(dtuId);
		for (EamEquipment d : devices) {
			int period = getModbusRtuPeriod(d) * 1000;
			logger.info("Device ID [{}], period [{}]", d.getEquipmentId(), period);
			ScheduledFuture<?> future = channel.eventLoop().scheduleAtFixedRate(new EquipmentRequestRunner(d), 0,
					period, TimeUnit.MILLISECONDS);
			mapFuture.put(d.getEquipmentId(), future);
		}

	}

	private int getModbusRtuPeriod(EamEquipment d){
		int period = 20;
		if (d.getModbusRtuPeriod() != null){
			period = d.getModbusRtuPeriod();
		}
		return period;
	}

	private class EquipmentRequestRunner implements Runnable {
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
			allPayload = loadAllPayload(device);
			logger.info("all Payload Size [{}]", allPayload.length);
		}

	}

	// load all payload;
	private ModbusRtuPayload[] loadAllPayload(EamEquipment device) {

		List<ModbusRtuPayload> payloads = new ArrayList<>();

		List<EamSensor> allSensors = deviceUtil.getSensors(device.getEquipmentId());
		Map<Integer, List<EamSensor>> groupByFunctionCodeMap = allSensors.stream()
				.collect(Collectors.groupingBy(sensor -> sensor.getFunctionCode()));

		for (Map.Entry<Integer, List<EamSensor>> entry : groupByFunctionCodeMap.entrySet()) {

			FunctionCode functionCode = FunctionCode.fromCode(entry.getKey().intValue()).get();

			List<EamSensor> sortedSensors = entry.getValue().stream().sorted((s1, s2) -> s1.getAddress().compareTo(s2.getAddress()))
					.collect(Collectors.toList());

			logger.info("SalveId=" + device.getSalveId());
			logger.info("FunctionCode=" + entry.getKey());
			logger.info("sensor size=" + sortedSensors.size());

			// combine to read request
			if (functionCode.isRead()) {
				List<List<EamSensor>> groupSensors = groupByAddress(sortedSensors);

				for(List<EamSensor> sensors : groupSensors){
					//print
					sensors.forEach(sensor -> {
						logger.info("sensor {{}}", sensor);
					});

					ModbusRequest request = buildRequet(sensors);
					ModbusRtuPayload payload = new ModbusRtuPayload("", device.getSalveId().shortValue(), request);
					map.put(payload, sensors);
					payloads.add(payload);
				}
			}
		}

		return payloads.toArray(new ModbusRtuPayload[payloads.size()]);
	}



	private List<List<EamSensor>> groupByAddress(List<EamSensor> sensors){
		List<List<EamSensor>> result = new ArrayList<List<EamSensor>>();
		List<EamSensor> group = new ArrayList<>();
		group.add(sensors.get(0));
		result.add(group);

		for(int i = 1; i < sensors.size(); i++){
			EamSensor second = sensors.get(i);
			EamSensor first = result.get(result.size() - 1).get(group.size() - 1);

			if (isTogether(first, second)){
				result.get(result.size() - 1).add(second);
			}else {
				group = new ArrayList<>();
				group.add(second);
				result.add(group);
			}

		}

		return result;
	}

	private boolean isTogether(EamSensor first, EamSensor second){
		boolean result = false;
		if (second.getAddress() - first.getAddress() <= MAX_ADDRESS_INTERVAL){
			result = true;
		}
		return result;
	}


	public ModbusRequest buildRequet(EamSensor sensor) {
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

	private ModbusRequest buildRequet(List<EamSensor> sensors) {
		switch (getFunctionCode(sensors.get(0))) {
			case ReadCoils:
				return buildReadCoils(sensors);

			case ReadDiscreteInputs:
				return buildReadDiscreteInputs(sensors);

			case ReadHoldingRegisters:
				return buildReadHoldingRegisters(sensors);

			case ReadInputRegisters:
				return buildReadInputRegisters(sensors);
			default:
				return null;
		}
	}

	private ModbusRequest buildReadCoils(List<EamSensor> sensors) {
		int address = sensors.get(0).getAddress();
		int quantity = (sensors.get(sensors.size() - 1).getAddress() - address)  + 1;
//		int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));
		return new ReadCoilsRequest(address, quantity);
	}

	private ReadDiscreteInputsRequest buildReadDiscreteInputs(List<EamSensor> sensors) {
		int address = sensors.get(0).getAddress();
		int quantity = (sensors.get(sensors.size() - 1).getAddress() - address)  + 1;
//		int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));

		return new ReadDiscreteInputsRequest(address, quantity);
	}

	private ReadHoldingRegistersRequest buildReadHoldingRegisters(List<EamSensor> sensors) {
		int address = sensors.get(0).getAddress();
		int quantity = (sensors.get(sensors.size() - 1).getAddress() - address)  + 1;
//		int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));

		return new ReadHoldingRegistersRequest(address, quantity);
	}

	private ReadInputRegistersRequest buildReadInputRegisters(List<EamSensor> sensors) {
		int address = sensors.get(0).getAddress();
		int quantity = (sensors.get(sensors.size() - 1).getAddress() - address) + 1;
//		int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));

		return new ReadInputRegistersRequest(address, quantity);
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
