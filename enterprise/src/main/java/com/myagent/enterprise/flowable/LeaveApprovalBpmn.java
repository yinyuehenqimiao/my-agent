package com.myagent.enterprise.flowable;

/** leave-approval 流程 BPMN 定义（首版）。 */
public final class LeaveApprovalBpmn {
    private LeaveApprovalBpmn() {
    }

    /**
     * 流程编码 leave-approval，首版版本 1。
     * 服务任务经白名单中央委托 workflowTaskDelegate 分发到 WorkflowTaskHandler SPI。
     * 人工任务由 Flowable 管理；业务事实保留在 ent_* 表。
     * 条件表达式仅使用白名单字段（decision）。
     */
    public static final String BPMN = """
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns:flowable="http://flowable.org/bpmn" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://myagent.com/workflow">
  <process id="leave-approval" isExecutable="true">
    <startEvent id="start" name="发起申请"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="validate_identity"/>

    <serviceTask id="validate_identity" name="校验身份与审批关系" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f2" sourceRef="validate_identity" targetRef="validate_dates"/>

    <serviceTask id="validate_dates" name="校验工作日与日期冲突" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f3" sourceRef="validate_dates" targetRef="check_balance"/>

    <serviceTask id="check_balance" name="校验年假余额" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f4" sourceRef="check_balance" targetRef="freeze_balance"/>

    <serviceTask id="freeze_balance" name="冻结年假余额" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f5" sourceRef="freeze_balance" targetRef="manager_approval"/>

    <userTask id="manager_approval" name="主管审批" flowable:assignee="${managerId}"/>
    <sequenceFlow id="f6" sourceRef="manager_approval" targetRef="manager_decision"/>

    <exclusiveGateway id="manager_decision" name="主管决策"/>
    <sequenceFlow id="f7" sourceRef="manager_decision" targetRef="hr_approval">
      <conditionExpression xsi:type="tFormalExpression">${decision == 'APPROVE'}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="f8" sourceRef="manager_decision" targetRef="release_reject">
      <conditionExpression xsi:type="tFormalExpression">${decision == 'REJECT'}</conditionExpression>
    </sequenceFlow>

    <serviceTask id="release_reject" name="释放冻结并驳回" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f9" sourceRef="release_reject" targetRef="rejected_end"/>

    <userTask id="hr_approval" name="人事审批" flowable:assignee="${hrId}"/>
    <sequenceFlow id="f10" sourceRef="hr_approval" targetRef="hr_decision"/>

    <exclusiveGateway id="hr_decision" name="人事决策"/>
    <sequenceFlow id="f11" sourceRef="hr_decision" targetRef="settle_balance">
      <conditionExpression xsi:type="tFormalExpression">${decision == 'APPROVE'}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="f12" sourceRef="hr_decision" targetRef="release_reject">
      <conditionExpression xsi:type="tFormalExpression">${decision == 'REJECT'}</conditionExpression>
    </sequenceFlow>

    <serviceTask id="settle_balance" name="结算年假余额" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f13" sourceRef="settle_balance" targetRef="audit"/>

    <serviceTask id="audit" name="审计记录" flowable:delegateExpression="${workflowTaskDelegate}"/>
    <sequenceFlow id="f14" sourceRef="audit" targetRef="approved_end"/>

    <endEvent id="approved_end" name="已批准"/>
    <endEvent id="rejected_end" name="已驳回"/>
  </process>
</definitions>
""";
}
