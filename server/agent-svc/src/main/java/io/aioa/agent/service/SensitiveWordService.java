package io.aioa.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.aioa.agent.repo.entity.SensitiveWordEntity;
import io.aioa.agent.repo.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词检测服务
 * - 启动时加载敏感词到内存
 * - 检测用户输入/AI输出中的敏感词
 * - action=1 触发审批，action=2 直接拦截
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;

    // 内存敏感词缓存
    private volatile List<SensitiveWordEntity> wordCache = new ArrayList<>();
    private volatile boolean loaded = false;

    /**
     * 加载敏感词到内存
     */
    public void loadWords() {
        var q = new LambdaQueryWrapper<SensitiveWordEntity>()
                .eq(SensitiveWordEntity::getStatus, 1);
        wordCache = sensitiveWordMapper.selectList(q);
        loaded = true;
        log.info("[敏感词] 加载 {} 个敏感词", wordCache.size());
    }

    /**
     * 检测文本中的敏感词
     * @param text 待检测文本
     * @return 命中的敏感词列表（空表示无命中）
     */
    public List<SensitiveWordEntity> detect(String text) {
        if (!loaded) loadWords();
        if (text == null || text.isEmpty()) return List.of();

        List<SensitiveWordEntity> hits = new ArrayList<>();
        for (SensitiveWordEntity sw : wordCache) {
            if (text.contains(sw.getWord())) {
                hits.add(sw);
            }
        }
        return hits;
    }

    /**
     * 检测并返回最严重的 action
     * @return 0=无命中 1=需审批 2=直接拦截
     */
    public int detectAction(String text) {
        List<SensitiveWordEntity> hits = detect(text);
        int maxAction = 0;
        for (SensitiveWordEntity sw : hits) {
            if (sw.getAction() > maxAction) {
                maxAction = sw.getAction();
            }
        }
        return maxAction;
    }

    /**
     * 获取命中的敏感词详情（用于前端展示）
     */
    public List<HitResult> detectWithDetail(String text) {
        List<SensitiveWordEntity> hits = detect(text);
        return hits.stream().map(sw -> new HitResult(
                sw.getWord(), sw.getCategory(), sw.getAction())).toList();
    }

    /**
     * 命中结果
     */
    public record HitResult(String word, String category, int action) {}
}
