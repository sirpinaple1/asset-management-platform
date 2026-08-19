package com.sk.asset.controller;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.auth.dto.AuthUserDto;
import com.sk.asset.auth.dto.SystemAuthDto;
import com.sk.asset.auth.dto.SystemMenuDto;
import com.sk.asset.auth.dto.SystemRoleDto;
import com.sk.asset.common.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/me 接口测试（standalone MockMvc + GlobalExceptionHandler）。
 */
class MeControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void 已认证_返回用户信息与asset权限() throws Exception {
        AuthUserDto user = new AuthUserDto();
        user.setUserId(1);
        user.setUsername("admin");
        user.setName("管理员");
        user.setDept("信息部");
        user.setJob("工程师");

        SystemRoleDto role = new SystemRoleDto();
        role.setId(1);
        role.setName("资产管理员");

        SystemMenuDto menu = new SystemMenuDto();
        menu.setId(11);
        menu.setName("资产管理");
        menu.setCode("asset");
        menu.setPath("/asset");

        SystemMenuDto button = new SystemMenuDto();
        button.setId(21);
        button.setName("新增资产");
        button.setCode("asset:create");

        SystemAuthDto systemAuth = new SystemAuthDto();
        systemAuth.setSystemCode("asset");
        systemAuth.setRoles(List.of(role));
        systemAuth.setMenus(List.of(menu));
        systemAuth.setButtons(List.of(button));

        UserContext.set(new AuthContext(user, systemAuth));

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.dept").value("信息部"))
                .andExpect(jsonPath("$.data.roles[0]").value("资产管理员"))
                .andExpect(jsonPath("$.data.permissions[0]").value("asset:create"))
                .andExpect(jsonPath("$.data.menus[0].code").value("asset"));
    }

    @Test
    void 未认证_业务异常401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未认证"));
    }
}
