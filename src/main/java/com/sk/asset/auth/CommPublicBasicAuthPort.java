package com.sk.asset.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.auth.dto.CommResult;
import com.sk.asset.auth.dto.SystemAuthDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * AuthPort 的 comm_public_basic HTTP 实现（ADR-0004）。
 * <p>
 * 回源链路（缓存未命中时）：GET /login/checkToken → GET /login/getAuth →
 * POST /login/userSystemAuth（header systemCode），全部通过后组装 AuthContext 并写入本地缓存。
 * <ul>
 *   <li>契约（2026-08-19 按 comm_public_basic origin/main 核实并实测，本地私改不影响契约层）：
 *       响应统一包装 {code, message, data}，code=200 成功；token 一律放 Authorization: Bearer 头；
 *       token 无效/缺失时三接口均经 TokenException 全局处理返回 <b>HTTP 401</b> + {code:401}。</li>
 *   <li>缓存：Caffeine，key=token，TTL 由 app.auth.cache-ttl-seconds 控制（默认 30s），
 *       仅缓存成功结果——无效 token 不缓存，避免登录后仍被旧失败状态拒绝。</li>
 *   <li>降级：回源不可达/超时/5xx/解析失败 → AuthException(503)，fail-closed 宁拒勿放。</li>
 * </ul>
 */
@Slf4j
@Component
public class CommPublicBasicAuthPort implements AuthPort {

    private static final String TOKEN_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PATH_CHECK_TOKEN = "/login/checkToken";
    private static final String PATH_GET_AUTH = "/login/getAuth";
    private static final String PATH_USER_SYSTEM_AUTH = "/login/userSystemAuth";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String systemCode;
    private final Cache<String, AuthContext> cache;

    public CommPublicBasicAuthPort(
            @Value("${app.auth.base-url}") String baseUrl,
            @Value("${app.auth.system-code:asset}") String systemCode,
            @Value("${app.auth.cache-ttl-seconds:30}") long cacheTtlSeconds,
            @Value("${app.auth.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.auth.read-timeout-ms:5000}") int readTimeoutMs) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.auth.base-url 未配置（R3：真实地址只放 application-local.yml 或环境变量）");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        // 独立 ObjectMapper：外部契约演进新增字段不应导致解析失败
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.systemCode = systemCode;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .build();
    }

    @Override
    public AuthContext authenticate(String token) {
        AuthContext cached = cache.getIfPresent(token);
        if (cached != null) {
            return cached;
        }
        AuthContext context = loadRemote(token);
        if (context != null) {
            cache.put(token, context);
        }
        return context;
    }

    /**
     * 回源三连（ADR-0004 原文链路）。protected 便于单测以桩替换 HTTP 调用，只测缓存与判定逻辑。
     *
     * @return null=token 无效；AuthException=403（无 asset 权限）/503（服务不可用）
     */
    protected AuthContext loadRemote(String token) {
        if (!Boolean.TRUE.equals(checkToken(token))) {
            return null;
        }
        AuthUserDto user = getAuth(token);
        if (user == null) {
            return null;
        }
        SystemAuthDto systemAuth = getUserSystemAuth(token);
        log.info("鉴权成功: userId={}, username={}, assetRoles={}",
                user.getUserId(), user.getUsername(), systemAuth.getRoles().size());
        return new AuthContext(user, systemAuth);
    }

    /** GET /login/checkToken → data:boolean。code!=200 视为无效。 */
    protected Boolean checkToken(String token) {
        CommResult<Boolean> result = parse(get(PATH_CHECK_TOKEN, token), new TypeReference<CommResult<Boolean>>() {
        });
        return result.isSuccess() ? result.getData() : null;
    }

    /** GET /login/getAuth → data:LoginUserVo。code!=200（含 TokenException）视为无效。 */
    protected AuthUserDto getAuth(String token) {
        CommResult<AuthUserDto> result = parse(get(PATH_GET_AUTH, token), new TypeReference<CommResult<AuthUserDto>>() {
        });
        return result.isSuccess() ? result.getData() : null;
    }

    /**
     * POST /login/userSystemAuth（header systemCode）→ data:UserSystemAuthVo。
     * code!=200 或用户在 asset 系统无任何角色 → 403（已登录但无权访问本系统）。
     */
    protected SystemAuthDto getUserSystemAuth(String token) {
        String body = post(PATH_USER_SYSTEM_AUTH, token);
        CommResult<SystemAuthDto> result = parse(body, new TypeReference<CommResult<SystemAuthDto>>() {
        });
        if (!result.isSuccess() || result.getData() == null) {
            throw new AuthException(403, "无 asset 系统访问权限（" + result.getMessage() + "）");
        }
        if (result.getData().getRoles() == null || result.getData().getRoles().isEmpty()) {
            throw new AuthException(403, "尚未分配 asset 系统角色，请联系管理员");
        }
        return result.getData();
    }

    /** GET 交换（protected：单测以契约 JSON 快照桩替换，见 CommPublicBasicAuthPortTest）。 */
    protected String get(String path, String token) {
        try {
            return restClient.get()
                    .uri(path)
                    .header(TOKEN_HEADER, BEARER_PREFIX + token)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw translateResponseError(e);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    /** POST 交换（userSystemAuth 需额外携带 systemCode 请求头）。 */
    protected String post(String path, String token) {
        try {
            return restClient.post()
                    .uri(path)
                    .header(TOKEN_HEADER, BEARER_PREFIX + token)
                    .header("systemCode", systemCode)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw translateResponseError(e);
        } catch (ResourceAccessException e) {
            throw unavailable(e);
        }
    }

    /**
     * HTTP 交换异常 → 语义映射（2026-08-19 实测，工作区与 origin/main 契约一致）：
     * comm_public_basic 对无效/缺失 token 抛 TokenException → HTTP 401 + {code:401}，
     * 映射为 AuthException(401)（客户端得到 401"token 无效或已过期"）；其余状态码按服务不可用 fail-closed。
     */
    protected AuthException translateResponseError(RestClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
            log.info("comm_public_basic 判定 token 无效（HTTP 401）");
            return new AuthException(401, "token 无效或已过期");
        }
        return unavailable(e);
    }

    /** JSON 解析失败 = 契约漂移，按服务不可用处理（fail-closed），不猜测语义。 */
    private <T> CommResult<T> parse(String body, TypeReference<CommResult<T>> type) {
        if (body == null || body.isBlank()) {
            throw new AuthException(503, "鉴权服务响应为空");
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            log.error("comm_public_basic 响应解析失败: {}", e.getMessage());
            throw new AuthException(503, "鉴权服务响应解析失败");
        }
    }

    private AuthException unavailable(Exception e) {
        log.error("comm_public_basic 不可用: {}", e.getMessage());
        return new AuthException(503, "鉴权服务不可用，请稍后重试");
    }
}
