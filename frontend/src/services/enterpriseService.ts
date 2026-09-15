import { api } from "./api";

export interface Employee { userId: string; displayName: string; department: string; businessRole: string; managerId?: string; hrId?: string; active: boolean }
export interface LeaveRequest { id: string; userId: string; leaveType: string; startDate: string; endDate: string; days: number; reason: string; status: string; managerId: string; hrId: string; createdAt: string }
export interface Action { id: string; kind: string; requestId: string; payload: string; status: string; result?: string; expiresAt: string }
/** 外部 ERP 写入前的确认卡；它不是 ERP 中已经创建的单据。 */
export interface ErpPendingAction { id: string; actionType: string; payload: string; status: string; result?: string; expiresAt: string }
export interface Task { id: string; requestId: string; stage: string; status: string }
export interface Workday { day: string; working: boolean; description?: string }
export interface Detail { request: LeaveRequest; tasks: Task[]; timeline: { id: string; event: string; comment: string; createdAt: string }[] }
export interface Evidence { docId: string; docName: string; chunkId?: string; chunkIndex?: number; excerpt?: string; version: string; effectiveDate?: string; matchedFragments?: number }
export const labels: Record<string, string> = { DRAFT: "草稿", PENDING_MANAGER: "待主管审批", PENDING_HR: "待人事审批", APPROVED: "已通过", REJECTED: "已拒绝", WITHDRAWN: "已撤回", SUBMIT: "提交申请", APPROVE: "同意审批", REJECT: "拒绝审批", WITHDRAW: "撤回申请", CREATE_LEAVE: "提交请假申请", PENDING: "待确认", DONE: "已执行", CANCELLED: "已取消", EXPIRED: "已过期", MANAGER: "主管", HR: "人事", EMPLOYEE: "员工", ANNUAL: "年假", PERSONAL: "事假", ACTION_CANCELLED: "取消确认" };
export const label = (value: string) => labels[value] || value;
const base = "/api/enterprise";
export const enterprise = {
  get: <T,>(path: string) => api.get<never, T>(base + path),
  post: <T,>(path: string, data?: unknown) => api.post<never, T>(base + path, data),
  put: <T,>(path: string, data?: unknown) => api.put<never, T>(base + path, data),
  delete: <T,>(path: string) => api.delete<never, T>(base + path),
};
