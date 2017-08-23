package com.kuyun.modbus.rpc.api;

/**
 * Created by user on 2017-08-18.
 */
public interface ModbusSlaveRtuApiService {

    public void startJob(String deviceId);

    public void pauseJob(String deviceId);
}
