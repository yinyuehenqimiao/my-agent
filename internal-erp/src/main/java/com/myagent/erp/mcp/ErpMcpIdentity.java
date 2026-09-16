package com.myagent.erp.mcp;

import com.myagent.erp.identity.ErpIdentity;

record ErpMcpIdentity(ErpIdentity identity, String allowedToolId) {}
