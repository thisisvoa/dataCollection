package com.kuyun.grm.rpc.api;

import org.quartz.SchedulerException;

/**
 * Created by user on 2017-08-16.
 */
public interface GrmApiService {

    public void startJob(String deviceId) throws SchedulerException;

    public void pauseJob(String deviceId) throws SchedulerException;


}
