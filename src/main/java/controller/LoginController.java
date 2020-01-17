package main.java.controller;

import main.java.annotation.QiuRequestParam;
import main.java.service.LoginService;
import main.java.annotation.QiuAutowired;
import main.java.annotation.QiuController;
import main.java.annotation.QiuRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@QiuController
@QiuRequestMapping(name = "/login")
public class LoginController {

    @QiuAutowired
    LoginService loginService;

    /**
     * 登录方法
     * @param request
     * @param response
     * @param name
     * @param password
     */
    @QiuRequestMapping(name = "/toLogin")
    public void toLogin(HttpServletRequest request, HttpServletResponse response, @QiuRequestParam("name") String name, @QiuRequestParam("password")  String password){
        String s = loginService.toLogin(name, password);
        try {
            PrintWriter writer = response.getWriter();
            writer.write(s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
