package com.myagent.enterprise.service;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnterpriseAdminService {
    private final EnterpriseMapper db;
    private final KnowledgeAccessService access;
    @Value("${myagent.enterprise.adapter:LOCAL}") private String adapterMode;
    @Value("${myagent.enterprise.erp.base-url:}") private String erpBaseUrl;
    @Value("${myagent.enterprise.erp.service-token:}") private String erpServiceToken;
    public ErpConnectionStatus erpStatus() {
        access.requireAdmin();
        if (!"HTTP_ERP".equalsIgnoreCase(adapterMode)) return new ErpConnectionStatus("本地企业数据", true, "当前使用本地开发数据，不会访问外部 ERP。", "如需切换独立 ERP，请由部署管理员设置 ENTERPRISE_ADAPTER=HTTP_ERP。");
        boolean configured = erpBaseUrl != null && !erpBaseUrl.isBlank() && erpServiceToken != null && !erpServiceToken.isBlank();
        return new ErpConnectionStatus("独立 ERP 服务", configured,
                configured ? "已配置受控 ERP 地址和服务凭证。" : "ERP 地址或服务凭证尚未配置，系统将拒绝 ERP 请求。",
                configured ? "可由部署管理员执行受控健康检查。" : "请在部署环境设置 ERP_BASE_URL 与 ERP_SERVICE_TOKEN，不要在页面输入凭证。");
    }
    public List<Employee> employees() { access.requireAdmin(); return db.list(Employee.class,"SELECT * FROM ent_employee ORDER BY display_name"); }
    @Transactional public void employee(String id,Employee e) {
        access.requireAdmin();access.identity(id);
        if (e.displayName()==null||e.displayName().isBlank()||e.displayName().length()>100||e.department()==null||e.department().isBlank()||e.department().length()>100
                ||e.businessRole()==null||!Set.of("EMPLOYEE","MANAGER","HR").contains(e.businessRole())) throw new ClientException("请填写有效姓名、部门及企业角色");
        if (id.equals(e.managerId())||id.equals(e.hrId())||(e.managerId()!=null&&e.managerId().equals(e.hrId()))) throw new ClientException("禁止自审或同一人两级审批");
        approver(e.managerId(),"MANAGER");approver(e.hrId(),"HR");
        db.employee(id,true);
        if(db.employee(id,false)==null)db.update("INSERT INTO ent_employee(user_id,display_name,department,business_role,manager_id,hr_id,active) VALUES(?,?,?,?,?,?,?)",id,e.displayName(),e.department(),e.businessRole(),e.managerId(),e.hrId(),e.active());
        else db.update("UPDATE ent_employee SET display_name=?,department=?,business_role=?,manager_id=?,hr_id=?,active=? WHERE user_id=?",e.displayName(),e.department(),e.businessRole(),e.managerId(),e.hrId(),e.active(),id);
    }
    private void approver(String id,String role) {
        if(id==null)return;
        Employee e=db.employee(id,false);
        if(e==null||!e.active()||!role.equals(e.businessRole()))throw new ClientException("请选择已启用且角色匹配的审批人");
    }
    public List<Grant> grants(String id) {access.requireAdmin();return db.list(Grant.class,"SELECT audience,subject FROM ent_kb_grant WHERE kb_id=?",id);}
    public List<Workday> calendar() {
        access.requireAdmin();
        return db.list(Workday.class, "SELECT day,working,description FROM ent_workday ORDER BY day");
    }
    @Transactional public void policy(String id,Policy p) {
        access.requireAdmin();access.requireDocument(id);db.update("DELETE FROM ent_document_policy WHERE doc_id=?",id);
        db.update("INSERT INTO ent_document_policy(doc_id,version,effective_date) VALUES(?,?,?)",id,p.version(),p.effectiveDate());
    }
    @Transactional public void calendar(LocalDate day,boolean working) {
        access.requireAdmin();db.update("DELETE FROM ent_workday WHERE day=?",day);
        db.update("INSERT INTO ent_workday(day,working,description) VALUES(?,?,?)",day,working,"管理员配置");
    }
    @Transactional public void resetCalendar(LocalDate day) {
        access.requireAdmin();
        db.update("DELETE FROM ent_workday WHERE day=?", day);
    }
}
