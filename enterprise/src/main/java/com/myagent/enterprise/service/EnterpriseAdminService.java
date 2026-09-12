package com.myagent.enterprise.service;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnterpriseAdminService {
    private final EnterpriseMapper db;
    private final KnowledgeAccessService access;
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
