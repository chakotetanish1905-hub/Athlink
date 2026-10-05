/** Credentials sent to POST /api/login. */
export interface Login {
  email: string;
  password: string;
}

/** Body returned by POST /api/login (backend LoginDTO). */
export interface LoginResponse {
  token: string;
  username: string;
  userRole: string;
  userId: number;
}
