package com.kuyun.grm;

import javafx.util.Pair;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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

    private Pair<JobDetail, SimpleTrigger> buildJobAndTrigger(String deviceId){
        JobDetail job = newJob(ReadDataJob.class).withIdentity(deviceId, deviceId).build();
        job.getJobDataMap().put(ReadDataJob.DEVICE_ID, deviceId);

        int period = getPeriod(deviceId);

        SimpleTrigger trigger = newTrigger().withIdentity(deviceId, deviceId).startNow()
                .withSchedule(simpleSchedule().withIntervalInSeconds(period).repeatForever()).build();

        return new Pair<JobDetail, SimpleTrigger>(job, trigger);
    }

    public void run(String deviceId) throws SchedulerException {
        JobKey jobKey = new JobKey(deviceId, deviceId);

        if (getScheduler().checkExists(jobKey)){
            resumeJob(deviceId);
        }else {
            Pair<JobDetail, SimpleTrigger> pair = buildJobAndTrigger(deviceId);
            getScheduler().scheduleJob(pair.getKey(), pair.getValue());
        }
    }

    public void pauseJob(String deviceId) throws SchedulerException {
        JobKey jobKey = new JobKey(deviceId, deviceId);
        if (getScheduler().checkExists(jobKey)){
            //getScheduler().pauseJob(jobKey);
            getScheduler().shutdown();
            grmUtil.deviceUtil.remove(deviceId);
        }
    }

    public void resumeJob(String deviceId) throws SchedulerException {
        JobKey jobKey = new JobKey(deviceId, deviceId);
        getScheduler().resumeJob(jobKey);
    }

    public void deleteJob(String deviceId) throws SchedulerException{
        JobKey jobKey = new JobKey(deviceId, deviceId);
        getScheduler().deleteJob(jobKey);
    }

    private int getPeriod(String deviceId){
        return grmUtil.getGrmPeriod(deviceId);
    }

}
