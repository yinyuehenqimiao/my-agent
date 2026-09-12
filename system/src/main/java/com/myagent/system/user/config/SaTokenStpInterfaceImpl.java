package com.myagent.system.user.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.StrUtil;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色提供器：从 RBAC 表实时加载。
 * - getPermissionList: 平台权限码（sys_user_role → sys_role_permission）
 * - getRoleList: 平台角色码（sys_user_role）
 * 保留 t_user.role 作为业务角色，不在此返回，避免与平台角色混淆。
 */
@Component
@RequiredArgsConstructor
public class SaTokenStpInterfaceImpl implements StpInterface {

    private final PolicyDecisionService policyDecisionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (loginId == null || !StrUtil.isNumeric(loginId.toString())) {
            return Collections.emptyList();
        }
        return policyDecisionService.permissionsOf(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null || !StrUtil.isNumeric(loginId.toString())) {
            return Collections.emptyList();
        }
        return policyDecisionService.rolesOf(Long.valueOf(loginId.toString()));
    }
}
