package com.kuyun.modbus.slave;

import com.digitalpetri.modbus.FunctionCode;
import com.kuyun.eam.dao.model.EamSensor;
import com.kuyun.modbus.rpc.service.impl.ModbusRtuServiceNewImpl;

/**
 * Created by user on 2017-06-01.
 */
public class ModbusRtuSlaveTest {

    public static void main(String[] args) throws Exception {
//        ModbusRtuSlave slave = new ModbusRtuSlave();
//        slave.run();

        ModbusRtuServiceNewImpl service = new ModbusRtuServiceNewImpl();
        EamSensor sensor = mockSensor();
        boolean result = service.writeData("A5TrjBBOgJ89t0ay", sensor);

        System.out.println(result);
    }

    static EamSensor mockSensor(){
        EamSensor sensor = new EamSensor();
        sensor.setSensorId(1);
        sensor.setSalveId(1);
        sensor.setAddress(1);
        sensor.setWriteNumber(3);
        sensor.setFunctionCode(FunctionCode.WriteSingleRegister.getCode());
        return sensor;
    }
}
