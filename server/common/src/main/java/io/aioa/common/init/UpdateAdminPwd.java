package io.aioa.common.init;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/**
 * 一次性更新 admin 密码哈希（开发期辅助）
 * 用法：java -cp ... io.aioa.common.init.UpdateAdminPwd <plain>
 */
public final class UpdateAdminPwd {

    private static final String URL = "jdbc:mysql://localhost:3306/aioa_tenant?"
            + "useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Singapore"
            + "&allowPublicKeyRetrieval=true&useSSL=false";

    public static void main(String[] args) throws Exception {
        String plain = args.length > 0 ? args[0] : "admin123";
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(plain);
        try (Connection conn = DriverManager.getConnection(URL, "root", "");
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET password_hash = ? WHERE username = 'admin' AND tenant_id = 1")) {
            ps.setString(1, hash);
            int n = ps.executeUpdate();
            System.out.println("updated rows=" + n + " hash=" + hash);
        }
    }
}
