package com.sk.asset.auth;

import com.sk.asset.auth.dto.SystemAuthDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthPort 缓存/判定逻辑测试：以契约 JSON 快照（按 comm_public_basic origin/main 核实，
 * 2026-08-19）桩替换 HTTP 交换层，验证解析、缓存、403/503 语义。
 */
class CommPublicBasicAuthPortTest {

    private static final String CHECK_TOKEN_OK = """
            {"code":200,"message":"检查通过","data":true,"timestamp":1755561600000}
            """;
    private static final String CHECK_TOKEN_INVALID = """
            {"code":200,"message":"检查未通过","data":false,"timestamp":1755561600000}
            """;
    private static final String GET_AUTH_OK = """
            {"code":200,"message":"操作成功","data":{"userId":1,"username":"admin","name":"管理员",
            "sex":"男","dept":"信息部","job":"工程师","email":"a@sk.com","phone":"13800000000",
            "avatar":"","token":"tok-1","expireTime":7200,"roles":["超级管理员"],
            "permissions":["sys:user:list"],"menus":[],"loginTime":"2026-08-19 10:00:00",
            "loginIp":"127.0.0.1","mustChangePassword":false},"timestamp":1755561600000}
            """;
    /** 实际无效 token 走 HTTP 401（见 translateResponseError 测试）；本快照覆盖"HTTP 200 + code!=200"兜底路径。 */
    private static final String GET_AUTH_TOKEN_INVALID = """
            {"code":500,"message":"Token无效或已过期","data":null,"timestamp":1755561600000}
            """;
    private static final String USER_SYSTEM_AUTH_OK = """
            {"code":200,"message":"操作成功","data":{"systemCode":"asset",
            "roles":[{"id":1,"name":"资产管理员","sysFlag":1,"remark":null,"userCount":2,"menuCount":10}],
            "menus":[{"id":11,"name":"资产管理","code":"asset","path":"/asset","icon":"box","type":0,
            "sortOrder":1,"pid":0,"level":1,"children":[{"id":12,"name":"资产台账","code":"asset:list",
            "path":"/asset/list","icon":null,"type":0,"sortOrder":1,"pid":11,"level":2,"children":[]}]}],
            "buttons":[{"id":21,"name":"新增资产","code":"asset:create","path":null,"type":1,"sortOrder":1}]
            },"timestamp":1755561600000}
            """;
    private static final String USER_SYSTEM_AUTH_NO_ROLES = """
            {"code":200,"message":"操作成功","data":{"systemCode":"asset","roles":[],"menus":[],"buttons":[]},
            "timestamp":1755561600000}
            """;
    private static final String USER_SYSTEM_AUTH_FAIL = """
            {"code":500,"message":"当前用户未登录","data":null,"timestamp":1755561600000}
            """;

    /** 桩实现：按 path 返回契约快照，计数供缓存断言。 */
    static class StubPort extends CommPublicBasicAuthPort {
        int checkTokenCalls, getAuthCalls, systemAuthCalls;
        String checkTokenBody = CHECK_TOKEN_OK;
        String getAuthBody = GET_AUTH_OK;
        String systemAuthBody = USER_SYSTEM_AUTH_OK;

        StubPort() {
            super("http://127.0.0.1:6002", "asset", 30, 2000, 5000);
        }

        @Override
        protected String get(String path, String token) {
            return path.endsWith("checkToken") ? checkTokenBody : getAuthBody;
        }

        @Override
        protected String post(String path, String token) {
            return systemAuthBody;
        }

        @Override
        protected Boolean checkToken(String token) {
            checkTokenCalls++;
            return super.checkToken(token);
        }

        @Override
        protected com.sk.asset.auth.dto.AuthUserDto getAuth(String token) {
            getAuthCalls++;
            return super.getAuth(token);
        }

        @Override
        protected SystemAuthDto getUserSystemAuth(String token) {
            systemAuthCalls++;
            return super.getUserSystemAuth(token);
        }
    }

    @Test
    void 有效token_三接口全通过_返回上下文并写缓存() {
        StubPort port = new StubPort();
        AuthContext ctx = port.authenticate("tok-1");

        assertThat(ctx).isNotNull();
        assertThat(ctx.getUserId()).isEqualTo(1);
        assertThat(ctx.getUsername()).isEqualTo("admin");
        assertThat(ctx.getUser().getDept()).isEqualTo("信息部");
        assertThat(ctx.assetRoleNames()).containsExactly("资产管理员");
        assertThat(ctx.buttonPermissionCodes()).containsExactly("asset:create");
        assertThat(ctx.getSystemAuth().getMenus()).hasSize(1);
        assertThat(ctx.getSystemAuth().getMenus().get(0).getChildren()).hasSize(1);

        // 命中缓存：第二次认证不再回源
        AuthContext cached = port.authenticate("tok-1");
        assertThat(cached).isSameAs(ctx);
        assertThat(port.checkTokenCalls).isEqualTo(1);
        assertThat(port.getAuthCalls).isEqualTo(1);
        assertThat(port.systemAuthCalls).isEqualTo(1);
    }

    @Test
    void 无效token_返回null且不缓存_下次请求重新回源() {
        StubPort port = new StubPort();
        port.checkTokenBody = CHECK_TOKEN_INVALID;

        assertThat(port.authenticate("bad")).isNull();
        assertThat(port.authenticate("bad")).isNull();
        assertThat(port.checkTokenCalls).isEqualTo(2);
    }

    @Test
    void checkToken通过但getAuth失败_按无效token处理() {
        StubPort port = new StubPort();
        port.getAuthBody = GET_AUTH_TOKEN_INVALID;

        assertThat(port.authenticate("tok-1")).isNull();
        assertThat(port.getAuthCalls).isEqualTo(1);
    }

    @Test
    void 已登录但无asset角色_403() {
        StubPort port = new StubPort();
        port.systemAuthBody = USER_SYSTEM_AUTH_NO_ROLES;

        assertThatThrownBy(() -> port.authenticate("tok-1"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus())
                .isEqualTo(403);
    }

    @Test
    void userSystemAuth业务失败_403() {
        StubPort port = new StubPort();
        port.systemAuthBody = USER_SYSTEM_AUTH_FAIL;

        assertThatThrownBy(() -> port.authenticate("tok-1"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus())
                .isEqualTo(403);
    }

    @Test
    void 回源HTTP异常_failClosed_503且不缓存() {
        StubPort port = new StubPort() {
            @Override
            protected String post(String path, String token) {
                throw new AuthException(503, "鉴权服务不可用，请稍后重试");
            }
        };

        assertThatThrownBy(() -> port.authenticate("tok-1"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus())
                .isEqualTo(503);
        // 503 不缓存：下次请求重试回源
        assertThatThrownBy(() -> port.authenticate("tok-1"))
                .isInstanceOf(AuthException.class);
        assertThat(port.systemAuthCalls).isEqualTo(2);
    }

    @Test
    void 响应体为空_按服务不可用503处理() {
        StubPort port = new StubPort();
        port.checkTokenBody = "  ";

        assertThatThrownBy(() -> port.authenticate("tok-1"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus())
                .isEqualTo(503);
    }

    @Test
    void 响应结构漂移_解析失败_503() {
        StubPort port = new StubPort();
        port.checkTokenBody = "<html>Gateway Timeout</html>";

        assertThatThrownBy(() -> port.authenticate("tok-1"))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus())
                .isEqualTo(503);
    }

    @Test
    void 未配置baseUrl_启动即失败() {
        assertThatThrownBy(() -> new CommPublicBasicAuthPort("", "asset", 30, 2000, 5000))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 回源HTTP401_映射为token无效401而非503() {
        // 契约实测（2026-08-19，工作区与 origin/main 一致）：
        // comm_public_basic 对无效 token 抛 TokenException → 三接口均 HTTP 401 + {code:401}
        StubPort port = new StubPort();
        RestClientResponseException unauthorized = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null);

        AuthException e = port.translateResponseError(unauthorized);

        assertThat(e.getStatus()).isEqualTo(401);
        assertThat(e.getMessage()).isEqualTo("token 无效或已过期");
    }

    @Test
    void 回源HTTP非401错误_仍按服务不可用503() {
        StubPort port = new StubPort();
        RestClientResponseException badGateway = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", null, null, null);

        assertThat(port.translateResponseError(badGateway).getStatus()).isEqualTo(503);
    }
}
