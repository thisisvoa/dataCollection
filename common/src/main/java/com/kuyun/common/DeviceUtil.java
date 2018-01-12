package com.kuyun.common;

import com.kuyun.eam.common.constant.CollectStatus;
import com.kuyun.eam.dao.model.*;
import com.kuyun.eam.rpc.api.*;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.kuyun.common.util.CommonUtil.DEVICE_ID_LENGTH;

/**
 * Created by user on 2017-06-09.
 */
public class DeviceUtil {
	private static final Logger logger = LoggerFactory.getLogger(DeviceUtil.class);

	@Autowired
	private EamEquipmentService eamEquipmentService;

	@Autowired
	private EamSensorService eamSensorService;

	@Autowired
	private EamSensorDataService eamSensorDataService;

	@Autowired
	private EamEquipmentModelPropertiesService eamEquipmentModelPropertiesService;

	@Autowired
	private EamApiService eamApiService;

	@Autowired
	private EamDtuService dtuService;

	@Autowired
	private EamDtuEquipmentService dtuEquipmentService;

	@Autowired
	private EamProductLineService eamProductLineService;

	Map<String, EamEquipment> deviceMap = new ConcurrentHashMap<>(1000);

	public EamSensorDataService getEamSensorDataService() {
		return this.eamSensorDataService;
	}

	public EamApiService getEamApiService() {
		return this.eamApiService;
	}

	public EamEquipment getDevice(String deviceId) {
		EamEquipment result = null;

		if (!deviceMap.containsKey(deviceId)) {
			result = getDeviceFromDB(deviceId);
			if (result != null) {
				deviceMap.put(deviceId, result);
			}
		} else {
			result = deviceMap.get(deviceId);
		}
		return result;
	}

	public void remove(String deviceId) {
		deviceMap.remove(deviceId);
	}

	private EamEquipment getDeviceFromDB(String deviceId) {
		EamEquipment result = eamEquipmentService.selectByPrimaryKey(deviceId);
		if (result != null) {
			EamSensorExample sensorExample = new EamSensorExample();
			List<Integer> propertyIds = getEquipmentModelPropertyIds(result);
			sensorExample.createCriteria().andEquipmentModelPropertyIdIn(propertyIds)
					.andDeleteFlagEqualTo(Boolean.FALSE);
			result.setSensors(eamSensorService.selectByExample(sensorExample));
		}
		return result;
	}

	private List<Integer> getEquipmentModelPropertyIds(EamEquipment equipment) {
		List<Integer> result = new ArrayList<>();
		if (equipment.getEquipmentModelId() != null) {
			EamEquipmentModelPropertiesExample example = new EamEquipmentModelPropertiesExample();
			example.createCriteria().andEquipmentModelIdEqualTo(equipment.getEquipmentModelId())
					.andDeleteFlagEqualTo(Boolean.FALSE);
			List<EamEquipmentModelProperties> properties = eamEquipmentModelPropertiesService.selectByExample(example);
			if (properties != null) {
				result = properties.stream().map(EamEquipmentModelProperties::getEquipmentModelPropertyId)
						.collect(Collectors.toList());
			}
		}

		return result;
	}

	public void setOffline(EamEquipment device) {
		device.setIsOnline(Boolean.FALSE);
		device.setCollectStatus(CollectStatus.NO_START.getCode());
		updateDevice(device);
		eamApiService.handleAlarmOffline(device.getEquipmentId());
	}

	public void setOnline(EamEquipment device) {
		device.setIsOnline(Boolean.TRUE);
		device.setCollectStatus(CollectStatus.WORKING.getCode());
		updateDevice(device);
	}

	private void updateDevice(EamEquipment device) {
		eamEquipmentService.updateByPrimaryKeySelective(device);
	}

	public boolean isDevice(String deviceId) {
		boolean result = false;

		if (StringUtils.length(deviceId) == DEVICE_ID_LENGTH) {
			if (getDevice(deviceId) != null) {
				result = true;
			}
		}
		return result;
	}

	public void persistDB(String dtuId, short unitId, String allData, List<EamSensor> sensors) {
		EamEquipment device = getDevice(dtuId, unitId);
		if (device != null){
			setOnline(device);
			int startAddress = sensors.get(0).getAddress();
			for (EamSensor sensor : sensors) {
				int currentAddress = sensor.getAddress();
				String dataFormat = sensor.getDataFormat();
				String bitOrder = sensor.getBitOrder();
				String data = CommonUtil.covenrtHexToNumber(startAddress, currentAddress, allData, dataFormat, bitOrder);
				data = exchangeData(sensor, data);
				logger.info("DtuId [{}], deviceId [{}], address Id [{}], data [{}]", dtuId, device.getEquipmentId(), sensor.getAddress(), data);
				eamApiService.processData(device.getEquipmentId(), sensor.getSensorId(), data);
			}
		}
	}

	public void persistDB(String deviceId, int unitId, String allData) {
		List<EamSensor> sensors = getSensors(deviceId, unitId);
		for (EamSensor sensor : sensors) {
			String dataFormat = sensor.getDataFormat();
			String bitOrder = sensor.getBitOrder();
			Pair<String, String> pair = CommonUtil.covenrtHexToNumber(allData, dataFormat, bitOrder);
			allData = pair.getValue();
			String data = pair.getKey();
			data = exchangeData(sensor, data);
			eamApiService.processData(deviceId, sensor.getSensorId(), data);
		}
	}

	private String exchangeData(EamSensor sensor, String data) {
		String result = data;
		if (sensor.getOsh() != null && sensor.getOsl() != null && sensor.getIsh() != null && sensor.getIsl() != null) {
			// Ov = [(Osh - Osl)*(Iv - Isl)/(Ish - Isl)] + Osl
			BigDecimal m = sensor.getOsh().subtract(sensor.getOsl())
					.multiply(new BigDecimal(data).subtract(sensor.getIsl()));
			BigDecimal n = sensor.getIsh().subtract(sensor.getIsl());
			result = String.valueOf(m.divide(n, 2).add(sensor.getOsl()));
		}
		return result;
	}

	public List<EamSensor> getSensors(String deviceId) {
		List<EamSensor> result = new ArrayList<>();

		EamEquipment equipment = eamEquipmentService.selectByPrimaryKey(deviceId);

		if (equipment != null) {
			EamSensorExample sensorExample = new EamSensorExample();
			List<Integer> propertyIds = getEquipmentModelPropertyIds(equipment);
			sensorExample.createCriteria().andEquipmentModelPropertyIdIn(propertyIds)
					.andDeleteFlagEqualTo(Boolean.FALSE);
			result = eamSensorService.selectByExample(sensorExample);
		}
		return result;
	}

	private List<EamSensor> getSensors(String deviceId, int unitId) {
		List<EamSensor> result = new ArrayList<>();

		EamEquipment equipment = eamEquipmentService.selectByPrimaryKey(deviceId);
		if (equipment != null) {
			EamSensorExample sensorExample = new EamSensorExample();
			List<Integer> propertyIds = getEquipmentModelPropertyIds(equipment);
			sensorExample.createCriteria().andEquipmentModelPropertyIdIn(propertyIds)
					.andDeleteFlagEqualTo(Boolean.FALSE);
			sensorExample.setOrderByClause("address asc");
			result = eamSensorService.selectByExample(sensorExample);
		}

		return result;
	}

	private EamSensor getSensor(String deviceId, int unitId) {
		EamSensor result = null;
		EamEquipment equipment = eamEquipmentService.selectByPrimaryKey(deviceId);
		if (equipment != null) {
			EamSensorExample sensorExample = new EamSensorExample();
			List<Integer> propertyIds = getEquipmentModelPropertyIds(equipment);
			sensorExample.createCriteria().andEquipmentModelPropertyIdIn(propertyIds)
					.andDeleteFlagEqualTo(Boolean.FALSE);
			sensorExample.setOrderByClause("address asc");
			result = eamSensorService.selectFirstByExample(sensorExample);
		}
		return result;
	}

	// private EamSensorData buildSensorData(String deviceId, int unitId, String
	// data) {
	// EamSensorData result = null;
	// EamSensor sensor = getSensor(deviceId, unitId);
	// if (sensor != null) {
	// result = createSensorData(deviceId, sensor.getSensorId(), data);
	// }
	// return result;
	// }

	// public EamSensorData createSensorData(String deviceId, Integer sensorId,
	// String data) {
	// EamSensorData result = new EamSensorData();
	// result.setEquipmentId(deviceId);
	// result.setSensorId(sensorId);
	// result.setStringValue(data);
	// result.setCreateTime(getCurrentDateTime());
	// result.setUpdateTime(getCurrentDateTime());
	// result.setDeleteFlag(Boolean.FALSE);
	// return result;
	// }

	public Date getCurrentDateTime() {
		LocalDateTime localDateTime = LocalDateTime.now();
		ZoneId zone = ZoneId.systemDefault();
		Instant instant = localDateTime.atZone(zone).toInstant();
		return Date.from(instant);
	}

	/************************************************/
	public boolean isDtuId(String dtuId) {
		// check the id is DTU or not.
		boolean result = false;
		EamDtu dtu = dtuService.selectByPrimaryKey(dtuId);
		if (dtu != null){
			result = true;
		}
		return result;
	}

	public List<EamEquipment> getDevices(String dtuId) {
		// need to retrieve from DB
		EamDtuEquipmentExample example = new EamDtuEquipmentExample();
		example.createCriteria().andDtuIdEqualTo(dtuId).andDeleteFlagEqualTo(Boolean.FALSE);
		List<EamDtuEquipment> dtuEquipments = dtuEquipmentService.selectByExample(example);

		List<EamEquipment> result = new ArrayList<>();
		if (dtuEquipments != null && !dtuEquipments.isEmpty()){
			List<String> equipmentIds = dtuEquipments.stream().map(e -> e.getEquipmentId()).collect(Collectors.toList());

			EamEquipmentExample equipmentExample = new EamEquipmentExample();
			equipmentExample.createCriteria().andEquipmentIdIn(equipmentIds).andDeleteFlagEqualTo(Boolean.FALSE);

			result = eamEquipmentService.selectByExample(equipmentExample);
		}

		return result;
	}

	public EamEquipment getDevice(String dtuId, int slaveId){
		EamEquipment result = null;
		List<EamEquipment> devices = getDevices(dtuId);
		if (!devices.isEmpty()){
			for (EamEquipment device : devices){
				if (device.getSalveId() == slaveId){
					result = device;
					break;
				}
			}
		}
		return result;
	}

	public String getDtuId(String deviceId){
		String result = null;
		EamDtuEquipmentExample example = new EamDtuEquipmentExample();
		example.createCriteria().andDeleteFlagEqualTo(Boolean.FALSE).andEquipmentIdEqualTo(deviceId);

		EamDtuEquipment dtuEquipment = dtuEquipmentService.selectFirstByExample(example);
		if (dtuEquipment != null){
			result = dtuEquipment.getDtuId();
		}
		return result;
	}

	public EamDtu getEamDtu(String dtuId){
		return dtuService.selectByPrimaryKey(dtuId);
	}


    public EamProductLine getProductLine(String productLineId) {
		return  eamProductLineService.selectByPrimaryKey(productLineId);
    }
}
