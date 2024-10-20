package com.yhw.sshdsessiondemo;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class SshdSessionDemoApplication {

    public static void main(String[] args) throws IOException {
        SpringApplication.run(SshdSessionDemoApplication.class, args);
        // 示例：创建并启动一个线程
        Thread myThread = new Thread(() -> {
            // 异步任务逻辑
            try {
                dsd();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        myThread.start();


//        // 上传文件
//        pool.uploadFile("server1.example.com", "/local/path/to/file", "/remote/path");
//
//        // 下载文件
//        pool.downloadFile("server2.example.com", "/remote/path/to/file", "/local/download/destination");


    }
    static void  dsd() throws IOException, InterruptedException {
        Map<String, String> serverCredentials = new HashMap<>();
//        serverCredentials.put("192.168.106.128", "123456");

        int maxConnectionsPerServer = 20; // 每台服务器的最大连接数
        MultiServerSshSessionPool pool = new MultiServerSshSessionPool(serverCredentials, maxConnectionsPerServer);

        // 执行远程命令
        for (int i = 0; i < 50; i++) {
            pool.executeCommand("192.168.106.130", "echo sd"+i);

        }
//        pool.executeCommand("192.168.106.130", "echo sd");
//        pool.executeCommand("192.168.106.128", "top");
    }

}
