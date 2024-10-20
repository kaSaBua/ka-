package com.yhw.sshdsessiondemo;

import lombok.Getter;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class SshdExecutor {
    SshClient client;
    ClientSession session;

    @Getter
    private String result;
    @Getter
    private String error;

    public SshdExecutor(String ip, Integer port, String user) {
        client = SshClient.setUpDefaultClient();
        client.start();
        try {
            session = client.connect(user, ip, port).verify(10 * 1000).getSession();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //密码方式
    public SshdExecutor(String ip, Integer port, String user, String password) {
        this(ip, port, user);
        session.addPasswordIdentity(password);
    }

    //公钥方式

    //执行命令
    public void execute(String command) {
        try {
            if (!session.auth().verify(10 * 1000).isSuccess()) {
                throw new Exception("auth faild");
            }

            ClientChannel channel = session.createExecChannel(command);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            channel.setOut(out);
            channel.setErr(err);

            if (!channel.open().verify(10 * 1000).isOpened()) {
                throw new Exception("open faild");
            }
            List<ClientChannelEvent> list = new ArrayList<>();
            list.add(ClientChannelEvent.CLOSED);
            channel.waitFor(list, 10 * 1000);
            channel.close();
            result = out.toString();
            error = err.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (client != null) {
                try {
                    client.stop();
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        SshdExecutor root = new SshdExecutor("192.168.106.128", 22, "root", "123456");
        root.execute("top");
    }

}
