package com.kuyun.modbus.rpc.api;

import com.kuyun.eam.dao.model.EamSensor;

/**
 * Created by user on 2017-08-18.
 */
public interface ModbusSlaveRtuApiService {

    public void startJob(String deviceId);

    public void pauseJob(String deviceId);

    public boolean writeData(String deviceId, EamSensor sensor);
}
