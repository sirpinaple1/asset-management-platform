package com.sk.asset.dingtalk.client;

import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.sk.asset.dingtalk.config.DingtalkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * 钉钉 access_token 获取与本地缓存（M10）。
 *
 * <p>POST /v1.0/oauth2/accessToken（appKey/appSecret 换取，expireIn 默认 7200s），
 * Caffeine 本地缓存（项目无 Redis，ADR-0005 单实例；提前 5 分钟过期刷新）。
 * 凭据错误/网络失败直接抛 {@link DingTalkApiException}，由调用方降级（outbox 记 FAILED）。</p>
 */
@Slf4j
@Component
public class DingTalkTokenClient {

    /** token 提前过期余量（秒）：expireIn - 300s 作为缓存 TTL */
    private static final long EXPIRE_MARGIN_SECONDS = 300;

    private static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/accessToken";

    private final DingtalkProperties props;
    private final RestClient restClient;
    private final com.github.benmanes.caffeine.cache.Cache<String, String> cache;

    public DingTalkTokenClient(DingtalkProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
        // TTL 按最小 expireIn - 余量；未拿到 token 前不缓存（失败不缓存，下次重试）
        this.cache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(7200 - EXPIRE_MARGIN_SECONDS))
                .build();
    }

    /** 获取 access_token（缓存命中不重复请求；过期自动重新获取） */
    public String getAccessToken() {
        return cache.get("access_token", k -> fetch());
    }

    private String fetch() {
        try {
            Map<String, Object> resp = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("appKey", props.getAppKey(), "appSecret", props.getAppSecret()))
                    .retrieve()
                    .body(Map.class);
            if (resp == null || resp.get("accessToken") == null) {
                throw new DingTalkApiException("获取钉钉 access_token 失败：响应为空或缺 accessToken");
            }
            log.debug("获取钉钉 access_token 成功（expireIn={}）", resp.get("expireIn"));
            return (String) resp.get("accessToken");
        } catch (DingTalkApiException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkApiException("获取钉钉 access_token 失败：" + e.getMessage(), e);
        }
    }

    /** 供 Stream SDK 建连复用同一套应用凭证 */
    public AuthClientCredential credential() {
        return new AuthClientCredential(props.getAppKey(), props.getAppSecret());
    }
}
