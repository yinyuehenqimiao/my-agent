package com.myagent.erp.leave;

public class InsufficientLeaveBalanceException extends RuntimeException {
    public InsufficientLeaveBalanceException() { super("可用年假余额不足"); }
}
