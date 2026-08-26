import { api } from "@/services/api";
import type { CurrentUser, User } from "@/types";

export interface LoginResponse extends User {}
export interface CurrentUserResponse extends CurrentUser {}

/** AuthController is under /api */
export async function login(username: string, password: string) {
  return api.post<LoginResponse, LoginResponse>("/api/auth/login", { username, password });
}

export async function logout() {
  return api.post<void>("/api/auth/logout");
}

export async function getCurrentUser() {
  return api.get<CurrentUserResponse, CurrentUserResponse>("/api/user/me");
}
