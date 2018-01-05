package com.kuyun.grm;

import cn.jiguang.common.utils.StringUtils;
import com.kuyun.eam.vo.EamGrmEquipmentVariableVO;
import com.kuyun.grm.common.Session;

import java.io.IOException;
import java.util.List;

/**
 * Created by user on 2018-01-01.
 */
public class GrmTest {

    public static void main(String[] args) {
        String id = "50106082654";
        String password = "cs123456";

        GrmApi api = new GrmApi();

        try {
            Session session = api.getSessionId(id, password);

            if (StringUtils.isNotEmpty(session.getSessionId())){
                List<EamGrmEquipmentVariableVO> data = api.getAllVariable(session.getSessionId());
                System.out.println(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
