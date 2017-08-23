package com.kuyun.grm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by user on 2017-08-16.
 */
public class Main {

    private static Logger _log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        _log.info(">>>>> kuyun-grm-service 正在启动 <<<<<");
        new ClassPathXmlApplicationContext("classpath:spring/*.xml");
        _log.info(">>>>> kuyun-grm-service 启动完成 <<<<<");
    }
}
