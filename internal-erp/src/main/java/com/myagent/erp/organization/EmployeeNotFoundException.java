package com.myagent.erp.organization;

public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException() { super("当前身份没有有效员工档案"); }
}
