package main.java.service.impl;

import main.java.annotation.QiuService;
import main.java.service.LoginService;

@QiuService
public class LoginServiceImpl implements LoginService {


    @Override
    public String toLogin(String name, String password) {
        System.out.println( " 登录用户： " + name + " 密码： " + password);
        return "登录成功";
    }
}
