package io.aioa.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aioa.agent.repo.entity.SkillFormEntity;
import io.aioa.agent.repo.entity.ToolEntity;
import io.aioa.agent.repo.mapper.SkillFormMapper;
import io.aioa.agent.repo.mapper.ToolMapper;
import io.aioa.common.id.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能表单服务
 * - 根据 Tool 的 paramSchema 自动生成表单定义
 * - 支持自定义表单 schema 覆盖自动生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillFormService {

    private final SkillFormMapper skillFormMapper;
    private final ToolMapper toolMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 获取 Tool 的表单 schema
     * 优先从 skill_forms 表读取自定义 schema，否则从 paramSchema 自动生成
     */
    public Object getFormSchema(Long toolId) {
        // 1. 先查 skill_forms 表
        SkillFormEntity form = skillFormMapper.selectOne(
                new LambdaQueryWrapper<SkillFormEntity>()
                        .eq(SkillFormEntity::getToolId, toolId)
                        .orderByDesc(SkillFormEntity::getVersion)
                        .last("LIMIT 1"));
        if (form != null && form.getFormSchema() != null) {
            try {
                return mapper.readTree(form.getFormSchema());
            } catch (Exception e) {
                log.warn("解析 form_schema 失败: {}", e.getMessage());
            }
        }

        // 2. 从 Tool 的 paramSchema 自动生成
        ToolEntity tool = toolMapper.selectById(toolId);
        if (tool == null || tool.getParamSchema() == null) {
            return generateEmptyForm(tool);
        }
        return autoGenerateForm(tool);
    }

    /**
     * 根据 Tool 的 paramSchema 自动生成表单定义
     */
    private JsonNode autoGenerateForm(ToolEntity tool) {
        try {
            JsonNode paramSchema = mapper.readTree(tool.getParamSchema());
            ObjectNode form = mapper.createObjectNode();
            form.put("toolId", tool.getId());
            form.put("toolName", tool.getName());
            form.put("description", tool.getDescription());

            ArrayNode fields = mapper.createArrayNode();

            // paramSchema 格式: {"type":"object","properties":{...},"required":[...]}
            JsonNode properties = paramSchema.path("properties");
            JsonNode required = paramSchema.path("required");

            if (properties.isObject()) {
                properties.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode prop = entry.getValue();
                    ObjectNode field = mapper.createObjectNode();
                    field.put("name", name);
                    field.put("label", prop.path("description").asText(name));
                    field.put("type", mapJsonTypeToFieldType(prop.path("type").asText("string")));
                    field.put("required", required.isArray() && required.toString().contains("\"" + name + "\""));
                    if (prop.has("enum")) {
                        ArrayNode options = mapper.createArrayNode();
                        prop.path("enum").forEach(opt -> options.add(opt));
                        field.set("options", options);
                    }
                    if (prop.has("default")) {
                        field.set("default", prop.path("default"));
                    }
                    fields.add(field);
                });
            }

            form.set("fields", fields);
            return form;
        } catch (Exception e) {
            log.warn("自动生成表单失败: {}", e.getMessage());
            return generateEmptyForm(tool);
        }
    }

    /**
     * JSON type → 表单字段类型
     */
    private String mapJsonTypeToFieldType(String jsonType) {
        return switch (jsonType) {
            case "integer", "number" -> "number";
            case "boolean" -> "switch";
            case "array" -> "multi-select";
            default -> "text";
        };
    }

    /**
     * 生成空表单
     */
    private JsonNode generateEmptyForm(ToolEntity tool) {
        ObjectNode form = mapper.createObjectNode();
        form.put("toolId", tool != null ? tool.getId() : 0);
        form.put("toolName", tool != null ? tool.getName() : "unknown");
        form.put("description", tool != null ? tool.getDescription() : "");
        form.set("fields", mapper.createArrayNode());
        return form;
    }

    /**
     * 保存自定义表单 schema
     */
    public Long saveFormSchema(Long toolId, Long tenantId, String formSchemaJson) {
        SkillFormEntity form = new SkillFormEntity();
        form.setIdStr(Snowflake.nextIdStr());
        form.setToolId(toolId);
        form.setTenantId(tenantId);
        form.setFormSchema(formSchemaJson);
        form.setVersion(1);
        skillFormMapper.insert(form);
        return form.getId();
    }

    /**
     * 列出所有 Tool 的表单 schema（用于前端技能表单页面）
     */
    public List<Object> listForms(Long tenantId) {
        List<ToolEntity> tools = toolMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ToolEntity>()
                        .eq(ToolEntity::getTenantId, tenantId)
                        .eq(ToolEntity::getStatus, 1));
        List<Object> forms = new ArrayList<>();
        for (ToolEntity tool : tools) {
            forms.add(getFormSchema(tool.getId()));
        }
        return forms;
    }
}
