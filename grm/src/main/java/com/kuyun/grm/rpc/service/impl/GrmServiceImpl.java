package com.kuyun.grm.rpc.service.impl;

import com.kuyun.eam.vo.EamGrmVariableVO;
import com.kuyun.grm.GrmAplication;
import com.kuyun.grm.rpc.api.GrmApiService;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

/**
 * Created by user on 2017-08-16.
 */
public class GrmServiceImpl implements GrmApiService{

    @Autowired
    private GrmAplication grmAplication = new GrmAplication();


    @Override
    public void startJob(String deviceId) throws SchedulerException {
        grmAplication.run(deviceId);
    }

    @Override
    public void pauseJob(String deviceId) throws SchedulerException {
        grmAplication.pauseJob(deviceId);
    }

    @Override
    public String[] writeData(String deviceId, String requestData) throws IOException {
        return grmAplication.writeData(deviceId, requestData);
    }

    @Override
    public List<EamGrmVariableVO> getAllVariable(String productLineId) throws IOException {
        return grmAplication.getAllVariable(productLineId);
    }


}
