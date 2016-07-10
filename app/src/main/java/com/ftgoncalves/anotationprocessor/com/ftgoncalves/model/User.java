package com.ftgoncalves.anotationprocessor.com.ftgoncalves.model;

import com.ftgoncalves.api.annotation.StaticStringUtil;

@StaticStringUtil
public class User {
    String name;
    String email;

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    @Override
    public String toString() {
        return StringUtil.createString(this);
    }
}