package com.kuyun.modbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.kuyun.datagather.DataGatherNettyServer;

/**
 * Created by user on 2017-06-09.
 */
public class ModbusRtuServiceApplication {

	private static Logger _log = LoggerFactory.getLogger(ModbusRtuServiceApplication.class);

	public static void main(String[] args) {
		_log.info(">>>>> kuyun-modbus-rtu-service 正在启动 <<<<<");
		ApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:/spring/*.xml");
		_log.info(">>>>> kuyun-modbus-rtu-service 启动完成 <<<<<");

		// ModbusRtuSlave slave = ctx.getBean(ModbusRtuSlave.class);
		DataGatherNettyServer slave = ctx.getBean(DataGatherNettyServer.class);

		try {
			slave.run();
		} catch (Exception e) {
			e.printStackTrace();
			_log.error("Modbus Rtu Server Error: " + e.getMessage());
		}

	}

}
