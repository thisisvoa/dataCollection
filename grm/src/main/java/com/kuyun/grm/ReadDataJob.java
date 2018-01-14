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

    public static final String PRODUCT_LINE_ID = "PRODUCT_LINE_ID";

    @Autowired
    private GrmUtil grmUtil = null;

    public ReadDataJob(){
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        buildGrmUtil();
        if (grmUtil != null){
            JobDataMap data = context.getJobDetail().getJobDataMap();
            String productLineId = data.getString(PRODUCT_LINE_ID);
            try {
                grmUtil.readData(productLineId);
            } catch (IOException e) {
                _logger.error("GRM Read Data Error : " + e.getMessage());
                grmUtil.setOffline(productLineId);
            }
        }
    }

    private void buildGrmUtil(){
        if (grmUtil == null){
            try {
                grmUtil = SpringContextUtil.getBean(GrmUtil.class);
            }catch (Exception e){
                _logger.error("build Grm Util Error : " + e.getMessage());
            }
        }
    }
}
