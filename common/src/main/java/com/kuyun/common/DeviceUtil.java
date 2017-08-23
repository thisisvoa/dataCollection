package com.kuyun.common;

import com.kuyun.eam.dao.model.*;
import com.kuyun.eam.rpc.api.EamEquipmentService;
import com.kuyun.eam.rpc.api.EamSensorDataService;
import com.kuyun.eam.rpc.api.EamSensorService;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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


    Map<String, EamEquipment> deviceMap = new ConcurrentHashMap<>(1000);


    public EamSensorDataService getEamSensorDataService() {
        return this.eamSensorDataService;
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

    public void remove(String deviceId){
        deviceMap.remove(deviceId);
    }

    private EamEquipment getDeviceFromDB(String deviceId) {
        EamEquipmentExample example = new EamEquipmentExample();
        example.createCriteria().andEquipmentIdEqualTo(deviceId);
        EamEquipment result = eamEquipmentService.selectFirstByExample(example);

        if (result != null) {
            EamSensorExample sensorExample = new EamSensorExample();
            sensorExample.createCriteria().andEquipmentIdEqualTo(result.getEquipmentId());
            result.setSensors(eamSensorService.selectByExample(sensorExample));
        }
        return result;
    }

    public void setOffline(EamEquipment device) {
        device.setIsOnline(Boolean.FALSE);
        updateDevice(device);
    }

    public void setOnline(EamEquipment device) {
        device.setIsOnline(Boolean.TRUE);
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

    public void persistDB(String deviceId, int unitId, String allData) {
        List<EamSensor> sensors = getSensors(deviceId, unitId);
        for(EamSensor sensor : sensors){
            String dataFormat = sensor.getDataFormat();
            String bitOrder = sensor.getBitOrder();
            Pair<String, String> pair = CommonUtil.covenrtHexToNumber(allData, dataFormat, bitOrder);
            allData = pair.getValue();
            String data = pair.getKey();
            EamSensorData sensorData = createSensorData(sensor.getSensorId(), data);
            eamSensorDataService.insertSelective(sensorData);
        }
    }

    public List<EamSensor> getSensors(String deviceId) {
        EamSensorExample example = new EamSensorExample();
        example.createCriteria().andEquipmentIdEqualTo(deviceId);
        return eamSensorService.selectByExample(example);
    }

    private List<EamSensor> getSensors(String deviceId, int unitId) {
        EamSensorExample example = new EamSensorExample();
        example.createCriteria().andEquipmentIdEqualTo(deviceId)
                .andSalveIdEqualTo(unitId);
        example.setOrderByClause("address asc");
        return eamSensorService.selectByExample(example);
    }

    private EamSensor getSensor(String deviceId, int unitId) {
        EamSensorExample example = new EamSensorExample();
        example.createCriteria().andEquipmentIdEqualTo(deviceId)
                .andSalveIdEqualTo(unitId);
        return eamSensorService.selectFirstByExample(example);
    }

    private EamSensorData buildSensorData(String deviceId, int unitId, String data) {
        EamSensorData result = null;
        EamSensor sensor = getSensor(deviceId, unitId);
        if (sensor != null) {
            result = createSensorData(sensor.getSensorId(), data);
        }
        return result;
    }

    public EamSensorData createSensorData(Integer sensorId, String data) {
        EamSensorData result = new EamSensorData();
        result.setSensorId(sensorId);
        result.setStringValue(data);
        result.setCreateTime(getCurrentDateTime());
        result.setUpdateTime(getCurrentDateTime());
        result.setDeleteFlag(Boolean.FALSE);
        return result;
    }

    public Date getCurrentDateTime() {
        LocalDateTime localDateTime = LocalDateTime.now();
        ZoneId zone = ZoneId.systemDefault();
        Instant instant = localDateTime.atZone(zone).toInstant();
        return Date.from(instant);
    }

}
