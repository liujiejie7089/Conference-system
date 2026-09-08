package io.aioa.common.init;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库初始化工具
 *
 * <p>用法（独立运行）：
 * <pre>
 *   java -cp aioa-common.jar:mysql-connector-j.jar io.aioa.common.init.SqlInit
 * </pre>
 *
 * <p>或者通过 Spring Boot 的 spring-boot:run goal 触发，但更推荐独立运行一次。
 *
 * <p>JDBC URL 不带库名 + allowMultiQueries=true，逐条执行 SQL。
 * 建库建表均使用 IF NOT EXISTS，幂等可重复执行。
 */
public final class SqlInit {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/?"
            + "useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Singapore"
            + "&allowPublicKeyRetrieval=true&useSSL=false&allowMultiQueries=true";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PWD = "";
    private static final String SCHEMA_RESOURCE = "/sql/aioa-schema.sql";

    public static void main(String[] args) throws Exception {
        String url = System.getenv().getOrDefault("AIOA_DB_URL", DEFAULT_URL);
        String user = System.getenv().getOrDefault("AIOA_DB_USER", DEFAULT_USER);
        String pwd = System.getenv().getOrDefault("AIOA_DB_PWD", DEFAULT_PWD);

        System.out.println("[SqlInit] connecting to " + url + " as " + user);

        String sqlText;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(SqlInit.class.getResourceAsStream(SCHEMA_RESOURCE), StandardCharsets.UTF_8))) {
            sqlText = reader.lines().collect(Collectors.joining("\n"));
        }

        List<String> statements = splitStatements(sqlText);
        System.out.println("[SqlInit] parsed " + statements.size() + " statements");

        try (Connection conn = DriverManager.getConnection(url, user, pwd);
             Statement stmt = conn.createStatement()) {
            int success = 0;
            int failed = 0;
            for (String sql : statements) {
                try {
                    stmt.execute(sql);
                    success++;
                } catch (Exception e) {
                    failed++;
                    System.err.println("[SqlInit] failed: " + sql.substring(0, Math.min(80, sql.length())).replace('\n', ' ') + " → " + e.getMessage());
                }
            }
            System.out.println("[SqlInit] done. success=" + success + " failed=" + failed);
        }
    }

    /**
     * 简易 SQL 切分：按 ; 切，跳过注释行。
     * 注意：不处理字符串内的 ;（CREATE TABLE 内一般没有，安全）
     */
    private static List<String> splitStatements(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#")) {
                continue;
            }
            cur.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s.substring(0, s.length() - 1)); // 去掉尾分号
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            String s = cur.toString().trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private SqlInit() {
    }
}
