package com.kuyun.grm;

import com.kuyun.eam.vo.EamGrmVariableVO;
import javafx.util.Pair;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Created by user on 2017-07-21.
 */
public class GrmAplication {
    private final Logger _logger = LoggerFactory.getLogger(GrmAplication.class);
    @Autowired
    private GrmUtil grmUtil = null;

    private final SchedulerFactory sf = new StdSchedulerFactory();

    public GrmAplication(){
        start();
    }

    private Scheduler getScheduler() throws SchedulerException {
        return sf.getScheduler();
    }

    private void start() {
        try {
            if (!getScheduler().isStarted()){
                getScheduler().start();
            }
        } catch (SchedulerException e) {
            _logger.error("GRM Scheduler Error : " + e.getMessage());
        }
    }

    private Pair<JobDetail, SimpleTrigger> buildJobAndTrigger(String productLineId){
        JobDetail job = newJob(ReadDataJob.class).withIdentity(productLineId, productLineId).requestRecovery().build();
        job.getJobDataMap().put(ReadDataJob.PRODUCT_LINE_ID, productLineId);

        int period = getPeriod(productLineId);

        SimpleTrigger trigger = newTrigger().withIdentity(productLineId, productLineId).startNow()
                .withSchedule(simpleSchedule().withIntervalInSeconds(period).repeatForever()).build();

        return new Pair<JobDetail, SimpleTrigger>(job, trigger);
    }

    public void run(String productLineId) throws SchedulerException {
        _logger.info("GRM Scheduler Starting for product line [{}] : ", productLineId);
        Pair<JobDetail, SimpleTrigger> pair = buildJobAndTrigger(productLineId);
        getScheduler().scheduleJob(pair.getKey(), pair.getValue());
        grmUtil.setOnline(productLineId);
    }

    public void pauseJob(String productLineId) throws SchedulerException {
        _logger.info("GRM Scheduler Stopping for product line [{}] : ", productLineId);
        JobKey jobKey = new JobKey(productLineId, productLineId);
        if (getScheduler().checkExists(jobKey)){
            getScheduler().unscheduleJob(TriggerKey.triggerKey(productLineId, productLineId));
            grmUtil.deviceUtil.remove(productLineId);

            grmUtil.setOffline(productLineId);
            _logger.info("GRM Scheduler Stopped for product line [{}] : ", productLineId);
        }
    }

    public void resumeJob(String productLineId) throws SchedulerException {
        JobKey jobKey = new JobKey(productLineId, productLineId);
        getScheduler().resumeJob(jobKey);
    }

    public void deleteJob(String productLineId) throws SchedulerException{
        JobKey jobKey = new JobKey(productLineId, productLineId);
        getScheduler().deleteJob(jobKey);
    }

    public String [] writeData(final String productLineId, final String requestData) throws IOException{
        return grmUtil.writeData(productLineId, requestData);
    }

    private int getPeriod(String productLineId){
        return grmUtil.getGrmPeriod(productLineId);
    }


    public List<EamGrmVariableVO> getAllVariable(String productLineId) throws IOException{
        return grmUtil.getAllVariable(productLineId);
    }

}
