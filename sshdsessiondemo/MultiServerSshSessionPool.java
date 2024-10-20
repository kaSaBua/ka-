package com.yhw.sshdsessiondemo;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.scp.client.ScpClient;
import org.apache.sshd.scp.client.ScpClientCreator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class MultiServerSshSessionPool {
    private final Map<String, Queue<ClientSession>> sessionPools; // 每台服务器的连接池
    private final ExecutorService executor;                       // 线程池
    private final SshClient sshClient;                            // SSH客户端
    private ScheduledExecutorService statusScheduler;


    public MultiServerSshSessionPool(Map<String, String> serverCredentials, int maxConnectionsPerServer) throws IOException {
        this.sshClient = SshClient.setUpDefaultClient();  // 初始化SSH客户端
        this.sshClient.start();
        this.sessionPools = new ConcurrentHashMap<>();    // 用于存储每个服务器的会话池
        this.executor = Executors.newCachedThreadPool();  // 用于执行异步任务

        // 初始化每个服务器的连接池
        for (Map.Entry<String, String> entry : serverCredentials.entrySet()) {
            String server = entry.getKey();
            String password = entry.getValue();
            Queue<ClientSession> pool = new LinkedList<>();

            for (int i = 0; i < maxConnectionsPerServer; i++) {
                pool.offer(createSession(server, password));
            }

            sessionPools.put(server, pool);  // 将连接池放入映射表
            this.statusScheduler = Executors.newScheduledThreadPool(1); // 创建调度器

            // 启动状态打印任务
            startStatusLoggingTask();
        }
    }
    private void startStatusLoggingTask() {
        statusScheduler.scheduleAtFixedRate(() -> {
            for (String server : sessionPools.keySet()) {
                Queue<ClientSession> pool = sessionPools.get(server);
                synchronized (pool) {
                    System.out.println("Server: " + server + ", Active Sessions: " + pool.size());
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    // 创建SSH会话
    private ClientSession createSession(String server, String password) throws IOException {
        ClientSession session = sshClient.connect("root", server, 22).verify(30,TimeUnit.SECONDS).getSession();
        session.addPasswordIdentity(password);
        boolean success = session.auth().verify(10, TimeUnit.SECONDS).isSuccess();// 认证
        return session;


    }

    // 从连接池中获取一个SSH会话
    public ClientSession getSession(String server) throws InterruptedException {
        Queue<ClientSession> pool = sessionPools.get(server);
        synchronized (pool) {
            while (pool.isEmpty()) {
                pool.wait();  // 如果没有可用的会话，等待
            }

            ClientSession session;
            // 循环检查，直到获取到有效会话
            while (true) {
                session = pool.poll();
                if (session == null || !session.isAuthenticated() || session.isClosed()) {
                    // 如果会话无效，重新创建会话
                    try {
                        session = createSession(server, "password");  // 假设你存储了密码
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new IllegalStateException("Unable to create a new session for server: " + server);
                    }
                }

                // 如果会话有效，跳出循环并返回
                if (session != null && session.isAuthenticated() && !session.isClosed()) {
                    return session;
                }
            }
        }
    }

    // 释放会话，归还到连接池
    public void releaseSession(String server, ClientSession session) {
        Queue<ClientSession> pool = sessionPools.get(server);
        synchronized (pool) {
            if (session != null && session.isAuthenticated() && !session.isClosed()) {
                pool.offer(session);  // 如果会话有效，归还到池中
            } else {
                try {
                    pool.offer(createSession(server, "password"));  // 重新创建一个新的会话
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            pool.notifyAll();  // 通知其他等待线程
        }
    }

    // 执行远程命令
    public void executeCommand(String server, String command) {
        try {
            ClientSession session = getSession(server);  // 获取会话
            executor.submit(() -> {
                ChannelExec channel = null;
                try {
                    channel = session.createExecChannel(command);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ByteArrayOutputStream outputErr = new ByteArrayOutputStream();
                    channel.setOut(output);  // 输出到控制台
                    channel.setErr(outputErr);  // 错误输出
                    channel.open().verify();      // 打开通道并执行命令

//                    InputStream in = channel.getInvertedOut();
//                    byte[] buffer = new byte[1024];
//                    int len;
//                    while ((len = in.read(buffer)) != -1) {
//                        System.out.write(buffer, 0, len);
//                    }

                    // 等待通道关闭
                    channel.waitFor(Arrays.asList(ClientChannelEvent.CLOSED), TimeUnit.MICROSECONDS.toMicros(1000));
                    System.out.println(output.toString());
                    System.out.println(System.currentTimeMillis());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (channel != null) {
                        channel.close(false);
                    }
                    releaseSession(server, session);  // 释放会话
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 使用SCP上传文件
    public void uploadFile(String server, String localFilePath, String remotePath) {
        try {
            ClientSession session = getSession(server);
            ScpClient scpClient = ScpClientCreator.instance().createScpClient(session);
            executor.submit(() -> {
                try {
                    scpClient.upload(Paths.get(localFilePath), remotePath, ScpClient.Option.Recursive);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    releaseSession(server, session);
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 使用SCP下载文件
    public void downloadFile(String server, String remoteFilePath, String localDownloadPath) {
        try {
            ClientSession session = getSession(server);
            ScpClient scpClient = ScpClientCreator.instance().createScpClient(session);
            executor.submit(() -> {
                try {
                    scpClient.download(remoteFilePath, Paths.get(localDownloadPath));
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    releaseSession(server, session);
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // 关闭连接池
    public void shutdown() {
        executor.shutdown();
        statusScheduler.shutdown(); // 关闭调度器
        for (Queue<ClientSession> pool : sessionPools.values()) {
            synchronized (pool) {
                for (ClientSession session : pool) {
                    if (session != null && session.isAuthenticated()) {
                        session.close(false);  // 关闭每个会话
                    }
                }
            }
        }
        sshClient.stop();  // 停止SSH客户端
    }
}
