package com.kuyun.grm.rpc.api;

import com.kuyun.eam.vo.EamGrmVariableVO;
import org.quartz.SchedulerException;

import java.io.IOException;
import java.util.List;

/**
 * Created by user on 2017-08-16.
 */
public interface GrmApiService {

    public void startJob(String deviceId) throws SchedulerException;

    public void pauseJob(String deviceId) throws SchedulerException;

    public String [] writeData(final String deviceId, final String requestData) throws IOException;


    public List<EamGrmVariableVO> getAllVariable(String productLineId) throws IOException;

}
