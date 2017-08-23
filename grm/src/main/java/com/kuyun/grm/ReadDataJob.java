package com.kuyun.grm;

import com.kuyun.common.util.SpringContextUtil;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

/**
 * Created by user on 2017-07-20.
 */
public class ReadDataJob implements Job {
    private static Logger _logger = LoggerFactory.getLogger(ReadDataJob.class);


    public static final String DEVICE_ID = "DEVICE_ID";

    private GrmUtil grmUtil = SpringContextUtil.getBean(GrmUtil.class);

    public ReadDataJob(){
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap data = context.getJobDetail().getJobDataMap();
            String deviceId = data.getString(DEVICE_ID);

            grmUtil.readData(deviceId);
        } catch (IOException e) {
            _logger.error("GRM Read Data Error : " + e.getMessage());
        }
    }
}
