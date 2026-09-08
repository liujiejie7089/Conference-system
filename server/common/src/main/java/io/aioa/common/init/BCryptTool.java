package io.aioa.common.init;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 生成 BCrypt 哈希工具
 * 用法：mvn -pl tenant-svc exec:java -Dexec.mainClass=io.aioa.common.init.BCryptTool -Dexec.args="admin123"
 */
public final class BCryptTool {

    public static void main(String[] args) {
        String pwd = args.length > 0 ? args[0] : "admin123";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(pwd);
        System.out.println("plain=" + pwd);
        System.out.println("hash=" + hash);
        System.out.println("verify=" + encoder.matches(pwd, hash));
    }
}
