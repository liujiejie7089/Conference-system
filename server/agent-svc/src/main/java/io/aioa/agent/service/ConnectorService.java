package io.aioa.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aioa.agent.api.dto.ToolDefinitionVo;
import io.aioa.agent.api.dto.ToolExecuteResponse;
import io.aioa.agent.repo.entity.ToolEntity;
import io.aioa.agent.repo.mapper.ToolMapper;
import io.aioa.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 通用连接器引擎
 * 职责：解析业务系统 OpenAPI 文档 → 自动生成 Tool → 鉴权注入 → 执行 Tool 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorService {

    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 注册业务系统：解析 OpenAPI 文档，自动生成 Tool
     */
    public int registerFromOpenApi(String openApiUrl, String baseUrl, String authToken,
                                    Long tenantId, String systemName) {
        try {
            // 1. 获取 OpenAPI 文档
            String openApiJson = fetchOpenApiDoc(openApiUrl, authToken);
            JsonNode doc = objectMapper.readTree(openApiJson);

            String apiTitle = systemName != null ? systemName :
                    doc.path("info").path("title").asText("unknown-system");
            JsonNode paths = doc.path("paths");

            int count = 0;
            Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();
            while (pathIter.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIter.next();
                String path = pathEntry.getKey();
                JsonNode pathItem = pathEntry.getValue();

                // 遍历该 path 下的每个 HTTP method
                for (String method : List.of("get", "post", "put", "delete", "patch")) {
                    if (!pathItem.has(method)) continue;
                    JsonNode operation = pathItem.get(method);

                    // 跳过 /health 等非业务接口
                    if (path.equals("/health")) continue;

                    ToolEntity tool = buildTool(path, method, operation, baseUrl,
                            authToken, tenantId, apiTitle);
                    saveOrUpdate(tool);
                    count++;
                }
            }
            log.info("连接器引擎：从 {} 解析出 {} 个 Tool", openApiUrl, count);
            return count;

        } catch (Exception e) {
            log.error("连接器引擎：解析 OpenAPI 失败: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAPI 解析失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Tool 列表（OpenAI Function Calling 格式）
     */
    public List<ToolDefinitionVo> getToolDefinitions(Long tenantId) {
        var query = new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getTenantId, tenantId)
                .eq(ToolEntity::getStatus, 1);
        List<ToolEntity> tools = toolMapper.selectList(query);

        List<ToolDefinitionVo> result = new ArrayList<>();
        for (ToolEntity tool : tools) {
            Map<String, Object> params = parseParamSchema(tool.getParamSchema());
            result.add(ToolDefinitionVo.builder()
                    .type("function")
                    .function(ToolDefinitionVo.FunctionDef.builder()
                            .name(tool.getSlug())
                            .description(tool.getDescription() != null ? tool.getDescription() : tool.getName())
                            .parameters(params)
                            .build())
                    .build());
        }
        return result;
    }

    /**
     * 执行 Tool 调用：注入鉴权 → 发送 HTTP 请求 → 返回结果
     */
    public ToolExecuteResponse executeTool(Long toolId, Map<String, Object> arguments, String authToken) {
        ToolEntity tool = toolMapper.selectById(toolId);
        if (tool == null) {
            return ToolExecuteResponse.builder().success(false).error("Tool 不存在").build();
        }

        try {
            String url = tool.getEndpoint();
            String method = tool.getMethod().toUpperCase();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            // 注入鉴权
            if (tool.getAuthType() != null && tool.getAuthType() == 1 && authToken != null) {
                reqBuilder.header("Authorization", "Bearer " + authToken);
            }
            reqBuilder.header("Content-Type", "application/json");

            // 构造请求体 / 查询参数
            if ("GET".equals(method) || "DELETE".equals(method)) {
                if (arguments != null && !arguments.isEmpty()) {
                    String query = buildQueryString(arguments);
                    reqBuilder.uri(URI.create(url + (url.contains("?") ? "&" : "?") + query));
                }
            } else {
                String body = arguments != null ? objectMapper.writeValueAsString(arguments) : "{}";
                HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
                if ("POST".equals(method)) {
                    reqBuilder.POST(bodyPublisher);
                } else if ("PUT".equals(method)) {
                    reqBuilder.PUT(bodyPublisher);
                } else if ("DELETE".equals(method)) {
                    reqBuilder.DELETE();
                }
            }
            if ("GET".equals(method)) {
                reqBuilder.GET();
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            return ToolExecuteResponse.builder()
                    .success(resp.statusCode() >= 200 && resp.statusCode() < 300)
                    .httpStatus(resp.statusCode())
                    .body(resp.body())
                    .build();

        } catch (Exception e) {
            log.error("连接器引擎：执行 Tool 失败: {}", e.getMessage(), e);
            return ToolExecuteResponse.builder()
                    .success(false)
                    .error("执行失败: " + e.getMessage())
                    .build();
        }
    }

    // ─── 内部方法 ───

    private String fetchOpenApiDoc(String url, String authToken) throws Exception {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (authToken != null && !authToken.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + authToken);
        }
        HttpResponse<String> resp = httpClient.send(reqBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("获取 OpenAPI 文档失败: HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private ToolEntity buildTool(String path, String method, JsonNode operation,
                                  String baseUrl, String authToken, Long tenantId,
                                    String systemName) {
        ToolEntity tool = new ToolEntity();
        tool.setTenantId(tenantId);
        tool.setIdStr(generateIdStr());

        // slug: systemName_method_path（去掉斜杠和花括号）
        String slug = systemName + "_" + method + "_" + path.replaceAll("[/{}]", "_").replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        tool.setSlug(slug);

        // name: operation summary
        String name = operation.path("summary").asText(operation.path("operationId").asText(slug));
        tool.setName(name);

        // description
        String desc = operation.path("description").asText(operation.path("summary").asText(""));
        tool.setDescription(desc);

        // endpoint: baseUrl + path
        tool.setEndpoint(baseUrl + path);

        // method
        tool.setMethod(method.toUpperCase());

        // authType: bearer
        tool.setAuthType(1);

        // paramSchema: 从 parameters + requestBody 提取
        tool.setParamSchema(buildParamSchema(operation));

        // resultSchema
        tool.setResultSchema("{}");

        // isSensitive: DELETE/PUT 默认敏感
        tool.setIsSensitive("DELETE".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) ? 1 : 0);

        tool.setStatus(1);

        return tool;
    }

    private String buildParamSchema(JsonNode operation) {
        try {
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = objectMapper.createObjectNode();
            List<String> required = new ArrayList<>();

            // 解析 query/path parameters
            JsonNode parameters = operation.path("parameters");
            if (parameters.isArray()) {
                for (JsonNode param : parameters) {
                    String name = param.path("name").asText();
                    JsonNode schemaNode = param.path("schema");
                    properties.set(name, schemaNode.isEmpty() ?
                            objectMapper.createObjectNode().put("type", "string") : schemaNode);
                    if (param.path("required").asBoolean(false)) {
                        required.add(name);
                    }
                }
            }

            // 解析 requestBody
            JsonNode requestBody = operation.path("requestBody");
            if (!requestBody.isMissingNode()) {
                JsonNode content = requestBody.path("content").path("application/json").path("schema");
                if (!content.isMissingNode()) {
                    JsonNode props = content.path("properties");
                    if (props.isObject()) {
                        props.fields().forEachRemaining(e -> properties.set(e.getKey(), e.getValue()));
                    }
                    JsonNode reqArr = content.path("required");
                    if (reqArr.isArray()) {
                        reqArr.forEach(n -> required.add(n.asText()));
                    }
                }
            }

            schema.set("properties", properties);
            if (!required.isEmpty()) {
                schema.putArray("required").addAll(required.stream()
                        .map(s -> objectMapper.getNodeFactory().textNode(s)).toList());
            }
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParamSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("type", "object");
            empty.put("properties", new HashMap<>());
            return empty;
        }
        try {
            return objectMapper.readValue(schemaJson, Map.class);
        } catch (Exception e) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("type", "object");
            empty.put("properties", new HashMap<>());
            return empty;
        }
    }

    private String buildQueryString(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    private void saveOrUpdate(ToolEntity tool) {
        var query = new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getTenantId, tool.getTenantId())
                .eq(ToolEntity::getSlug, tool.getSlug());
        ToolEntity existing = toolMapper.selectOne(query);
        if (existing != null) {
            tool.setId(existing.getId());
            toolMapper.updateById(tool);
        } else {
            toolMapper.insert(tool);
        }
    }

    private String generateIdStr() {
        return "tl_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }
}
