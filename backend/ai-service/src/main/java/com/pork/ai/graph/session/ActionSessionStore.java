package com.pork.ai.graph.session;

import com.pork.ai.config.ActionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话存储。
 *
 * <p>为什么放在内存而不是 Redis：</p>
 * <ul>
 *   <li>会话只是「补参数」的临时状态，TTL 10 分钟，丢了用户重说一句就好；</li>
 *   <li>放 Redis 意味着 ai-service 要引一个 redis 依赖、多一份连接池、多一处故障点，
 *       换来的只是「多实例共享会话」——而这套功能目前是单实例的；</li>
 *   <li>真要水平扩容时，把本类换成 Redis 实现即可，接口就下面这几个方法。</li>
 * </ul>
 *
 * <p>容量上限是必须的：conversationId 由客户端持有，攻击者可以每次请求都造一个新的，
 * 不设上限就是一条内存耗尽的路。满了之后淘汰最久未活跃的，而不是拒绝新请求 ——
 * 拒绝新请求会让正常用户被攻击者连累。</p>
 */
@Slf4j
@Component
public class ActionSessionStore {

    private final ActionProperties props;
    private final Map<String, ActionSession> sessions = new ConcurrentHashMap<>();

    public ActionSessionStore(ActionProperties props) {
        this.props = props;
    }

    public ActionSession get(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        ActionSession s = sessions.get(conversationId);
        if (s == null) {
            return null;
        }
        if (expired(s)) {
            sessions.remove(conversationId);
            return null;
        }
        return s;
    }

    public ActionSession createOrGet(String conversationId, String userId) {
        ActionSession existing = get(conversationId);
        if (existing != null) {
            return existing;
        }
        String id = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;
        ActionSession s = new ActionSession(id, userId);
        sessions.put(id, s);
        evictIfNeeded();
        return s;
    }

    public void remove(String conversationId) {
        if (conversationId != null) {
            sessions.remove(conversationId);
        }
    }

    public int size() {
        return sessions.size();
    }

    private boolean expired(ActionSession s) {
        long ttl = props.getSessionTtlMinutes() * 60_000L;
        return System.currentTimeMillis() - s.getUpdatedAt() > ttl;
    }

    private void evictIfNeeded() {
        int max = props.getSessionMax();
        if (max <= 0 || sessions.size() <= max) {
            return;
        }
        // 先清过期，仍然超限再按最后活跃时间淘汰
        List<String> expiredKeys = sessions.entrySet().stream()
                .filter(e -> expired(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        expiredKeys.forEach(sessions::remove);

        if (sessions.size() > max) {
            sessions.entrySet().stream()
                    .sorted(Comparator.comparingLong(e -> e.getValue().getUpdatedAt()))
                    .limit(sessions.size() - max)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList())
                    .forEach(sessions::remove);
            log.debug("会话已满，淘汰最久未活跃的");
        }
    }
}
