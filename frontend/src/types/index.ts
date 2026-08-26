export interface User {
  userId: string;
  username?: string;
  role: string;
  token: string;
  nickname?: string;
}

export type CurrentUser = Omit<User, "token">;
