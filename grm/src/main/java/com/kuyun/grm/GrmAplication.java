package com.kuyun.grm;

import com.kuyun.eam.vo.EamGrmEquipmentVariableVO;
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

    private Pair<JobDetail, SimpleTrigger> buildJobAndTrigger(String deviceId){
        JobDetail job = newJob(ReadDataJob.class).withIdentity(deviceId, deviceId).requestRecovery().build();
        job.getJobDataMap().put(ReadDataJob.DEVICE_ID, deviceId);

        int period = getPeriod(deviceId);

        SimpleTrigger trigger = newTrigger().withIdentity(deviceId, deviceId).startNow()
                .withSchedule(simpleSchedule().withIntervalInSeconds(period).repeatForever()).build();

        return new Pair<JobDetail, SimpleTrigger>(job, trigger);
    }

    public void run(String deviceId) throws SchedulerException {
        _logger.info("GRM Scheduler Starting for device [{}] : ", deviceId);
        Pair<JobDetail, SimpleTrigger> pair = buildJobAndTrigger(deviceId);
        getScheduler().scheduleJob(pair.getKey(), pair.getValue());
        grmUtil.setOnline(deviceId);
    }

    public void pauseJob(String deviceId) throws SchedulerException {
        _logger.info("GRM Scheduler Stopping for device [{}] : ", deviceId);
        JobKey jobKey = new JobKey(deviceId, deviceId);
        if (getScheduler().checkExists(jobKey)){
            getScheduler().unscheduleJob(TriggerKey.triggerKey(deviceId, deviceId));
            grmUtil.deviceUtil.remove(deviceId);

            grmUtil.setOffline(deviceId);
            _logger.info("GRM Scheduler Stopped for device [{}] : ", deviceId);
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

    public String [] writeData(final String deviceId, final String requestData) throws IOException{
        return grmUtil.writeData(deviceId, requestData);
    }

    private int getPeriod(String deviceId){
        return grmUtil.getGrmPeriod(deviceId);
    }


    public List<EamGrmEquipmentVariableVO> getAllVariable(String deviceId) throws IOException{
        return grmUtil.getAllVariable(deviceId);
    }

}
