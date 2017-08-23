package com.kuyun.modbus;

import com.kuyun.modbus.slave.ModbusRtuSlave;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Created by user on 2017-06-09.
 */
public class ModbusRtuServiceApplication {

    private static Logger _log = LoggerFactory.getLogger(ModbusRtuServiceApplication.class);

    public static void main(String[] args) {
        _log.info(">>>>> kuyun-modbus-rtu-service 正在启动 <<<<<");
        ApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:/spring/*.xml");
        _log.info(">>>>> kuyun-modbus-rtu-service 启动完成 <<<<<");

        ModbusRtuSlave slave = ctx.getBean(ModbusRtuSlave.class);

        try {
            slave.run();
        } catch (Exception e) {
            _log.error("Modbus Rtu Server Error: "+ e.getMessage());
        }

    }


}
