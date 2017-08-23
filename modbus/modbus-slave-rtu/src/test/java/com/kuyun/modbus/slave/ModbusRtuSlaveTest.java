package com.kuyun.modbus.slave;

/**
 * Created by user on 2017-06-01.
 */
public class ModbusRtuSlaveTest {

    public static void main(String[] args) throws Exception {
        ModbusRtuSlave slave = new ModbusRtuSlave();
        slave.run();
    }
}
