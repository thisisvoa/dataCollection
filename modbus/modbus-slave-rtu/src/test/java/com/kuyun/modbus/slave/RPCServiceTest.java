package com.kuyun.modbus.slave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Created by user on 2017-06-15.
 */
@Component
public class RPCServiceTest {
    private final Logger logger = LoggerFactory.getLogger(RPCServiceTest.class);

//    @Autowired
//    private DeviceUtil deviceUtil = null;

    public static void main(String[] args) throws Exception {
        ApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:/spring/*.xml");
        RPCServiceTest service = ctx.getBean(RPCServiceTest.class);
        service.getDeviceTest();
    }

    public void getDeviceTest(){
//        EamEquipment eamEquipment = deviceUtil.getDevice("30");
//        logger.info("device ID:{}", eamEquipment.getEquipmentId());

    }
}
