package com.myagent.system.auth.service;

import com.myagent.framework.context.LoginUser;

/**
 * 执行身份企业属性富化 SPI。
 * <p>
 * system 模块负责装配平台角色（RBAC），但企业业务属性（部门、企业角色、主管、人事）
 * 来自 enterprise 模块的 ent_employee 表。system 不能依赖 enterprise，故定义此 SPI，
 * 由 enterprise 实现并通过 Spring 注入。无 enterprise 时返回原身份不变。
 */
public interface ExecutionIdentityEnricher {

    /**
     * 在已有平台角色的 LoginUser 上填充企业业务属性。
     *
     * @param user 已设置 userId/username/role/platformRoles 的快照
     * @return 富化后的 LoginUser（可就地修改并返回同一对象）
     */
    LoginUser enrich(LoginUser user);
}
