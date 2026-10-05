// Sent to POST /api/login
export interface Login {
  email: string;
  password: string;
}

// Returned by POST /api/login (backend LoginDTO)
export interface LoginDTO {
  token: string;
  username: string;
  userRole: string;
  userId: number;
}
