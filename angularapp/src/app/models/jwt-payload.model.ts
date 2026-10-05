/** Claims placed in the JWT by the backend (JwtUtils). */
export interface JwtPayload {
  sub: string;       // email
  userId: number;
  role: string;      // 'Manager' | 'Client'
  username: string;
  iat: number;
  exp: number;
}
