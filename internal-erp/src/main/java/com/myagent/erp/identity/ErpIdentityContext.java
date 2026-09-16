package com.myagent.erp.identity;

public final class ErpIdentityContext {
    private static final ThreadLocal<ErpIdentity> CURRENT = new ThreadLocal<>();

    private ErpIdentityContext() {
    }

    public static void set(ErpIdentity identity) { CURRENT.set(identity); }

    public static ErpIdentity require() {
        ErpIdentity identity = CURRENT.get();
        if (identity == null) throw new UnauthorizedException("缺少 ERP 调用身份");
        return identity;
    }

    public static void clear() { CURRENT.remove(); }
}
