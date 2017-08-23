package com.kuyun.grm.common;

/**
 * Created by user on 2017-07-19.
 */
public enum Action {

    READ("R", "Read"),
    WRITE("W", "Write");

    private String code;
    private String name;

    Action(String code, String name){
        this.code = code;
        this.name = name;
    }



    public static String getName(String code) {
        for (Action c : Action.values()) {
            if (c.getCode().equals(code) ) {
                return c.name;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}

