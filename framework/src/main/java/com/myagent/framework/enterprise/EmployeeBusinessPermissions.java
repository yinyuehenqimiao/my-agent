package com.myagent.framework.enterprise;

import java.util.List;

/** 面向员工展示的业务权限名称，不包含内部权限码。 */
public record EmployeeBusinessPermissions(List<String> permissions) {
    public EmployeeBusinessPermissions {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
