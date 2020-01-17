package main.java.servlet;

import main.java.annotation.QiuAutowired;
import main.java.annotation.QiuController;
import main.java.annotation.QiuRequestMapping;
import main.java.annotation.QiuService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyServlet extends HttpServlet {
    // 默认配置文件中属性
    Properties contextConfig = new Properties();

    // 扫描的到的类文件名称
    List<String> classNameList = new ArrayList<String>();

    // ioc容器
    Map<String, Object> ioc = new HashMap<String, Object>();

    // handlerMapping容器
    Map<String, Method> handlerMapping = new HashMap<String, Method>();
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 初始化spring
        // 1.加载配置文件
        loadConfig(config);
        System.out.println(" 加载配置文件------ ");
        // 2.扫描包
        scanPackage(contextConfig.getProperty("scanPackage"));
        System.out.println(" 扫描包------ ");
        // 3.将带有注解的bean加载到ioc容器
        doInstance();
        System.out.println(" 将带有注解的bean加载到ioc容器------ ");
        // 4.依赖注入
        doAutowired();
        // 5.将请求url与controller中方法建立关系存入handlerMapping中
        initHandlerMapping();
        // 6.初始化完成
        System.out.println(" mini Spring 容器初始化完成...... ");
    }

    /**
     * 将请求url与controller中方法建立关系存入handleMapping中
     */
    private void initHandlerMapping() {
        if (ioc == null || ioc.size() <= 0) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 查看所有属性
            Class<?> clazz = entry.getValue().getClass();
            // 使用QiuController注解的类建立映射关系
            if (!clazz.isAnnotationPresent(QiuController.class)) {
                continue;
            }
            String baseUrl = "";
            QiuRequestMapping annotation = clazz.getAnnotation(QiuRequestMapping.class);
            if (annotation != null) {
                baseUrl = annotation.name();
            }
            // 获取各个方法
            Method[] methods = clazz.getMethods();
            for (Method method : methods){
                if (!method.isAnnotationPresent(QiuRequestMapping.class)) {continue;}
                String name = method.getAnnotation(QiuRequestMapping.class).name();
                String url = ("/" + baseUrl + "/" + name)
                        .replaceAll("/+", "/");
                handlerMapping.put(url,method);
            }
        }
    }

    /**
     * 将bean之间注入关系
     */
    private void doAutowired() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 查看所有属性
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                // 属性自动注入
                if (!field.isAnnotationPresent(QiuAutowired.class)) {
                    continue;
                }
                // 属性带有注入注解
                QiuAutowired annotation = field.getAnnotation(QiuAutowired.class);
                String value = annotation.value();
                // 获取用户自定义注入名称
                if (value != null && "".equals(value.trim())) {
                    value = field.getType().getName();
                }
                // 暴力获取获取
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(value));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化ioc容器
     */
    private void doInstance() {
        if (classNameList == null || classNameList.size() <= 0) {
            return;
        }
        try {
            // 判断类上是否使用注解
            for (String className : classNameList) {
                // 通过反射获取这个类
                Class<?> clazz = Class.forName(className);
                boolean annotationPresent = clazz.isAnnotationPresent(QiuController.class);
                boolean annotationPresent1 = clazz.isAnnotationPresent(QiuService.class);
                // 当前类未添加注解
                if (!annotationPresent && !annotationPresent1) {
                    continue;
                }
                String beanName = toLowerFirstCase(clazz.getSimpleName());
                Object instance = clazz.newInstance();
                if (clazz.getAnnotation(QiuController.class) != null) {
                    // 当前类添加QiuController注解 将该类的默认名称与Class对象建立关系
                    ioc.put(beanName, instance);
                    continue;
                }
                // service 使用有三种方法 依靠类型 依靠默认名称 依靠自定义名称
                // 用户自定义名称顶替默认名称
                String value = clazz.getAnnotation(QiuService.class).value();
                if (value != null && !"".equals(value.trim())) {
                    beanName = value;
                }
                ioc.put(beanName, instance);
                // 将bean类型与Class对象建立关系
                for (Class<?> i : clazz.getInterfaces()) {
                    if (ioc.containsKey(i.getName())) {
                        throw new Exception(" bean名称重复请查看 " + i.getName());
                    }
                    ioc.put(i.getName(), instance);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 將了类名首字母小写当作bean的默认名称
     *
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 扫描 application.properties文件配置包路径
     */
    private void scanPackage(String scanPackageUrl) {
        // 将包地址转换为URL
        URL resource = this.getClass().getClassLoader().getResource(scanPackageUrl.replaceAll("\\.", "/"));
        File file = new File(resource.getFile());
        for (File scanFile : file.listFiles()) {
            // 判断是否为文件夹
            if (scanFile.isDirectory()) {
                scanPackage(scanPackageUrl + "." + scanFile.getName());
            } else {
                // 将文件加载到IOC容器中
                String name = scanFile.getName();
                // 加载.class文件
                if (!name.endsWith(".class")) {
                    continue;
                }
                // 将扫描到的文件装载到集合中
                String className = (scanPackageUrl + "." + scanFile.getName()).replace(".class","");
                classNameList.add(className);
            }
        }
    }

    /**
     * 加载配置文件
     *
     * @param config
     */
    private void loadConfig(ServletConfig config) {
        String initParameter = config.getInitParameter("contextConfigLocation");
        InputStream inputStream = null;
            // 拿到这个对象的类加载器 加载 application.properties文件
            inputStream = this.getClass().getClassLoader().getResourceAsStream(initParameter);
            // 将属性装载到 Properties 中
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 委派模式 分发任务
        try {
            doDispatch(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 分发请求
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {

        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        // 获取相对路径 将多个/ 转为一个/
        String s = requestURI.replaceAll(contextPath, "").replaceAll("/+", "/");
        Method o = handlerMapping.get(s);
        if (o == null) {
            resp.getWriter().write(" NOT FOUNT 404 ");
            return;
        }
        // 通过反射执行该方法
        Map<String, String[]> parameterMap = req.getParameterMap();
        String beanName = toLowerFirstCase(o.getDeclaringClass().getSimpleName());
        o.invoke(ioc.get(beanName), new Object[]{req, resp, parameterMap.get("name")[0], parameterMap.get("password")[0]});
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }
}

