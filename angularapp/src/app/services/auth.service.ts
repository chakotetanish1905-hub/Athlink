import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { API_ENDPOINTS, STORAGE_KEYS } from '../constants/constant';
import { Login, LoginResponse } from '../models/login.model';
import { User } from '../models/user.model';
import { TokenService } from './token.service';

@Injectable({ providedIn: 'root' })
export class AuthService {

  public apiUrl: string = apiUrl;

  private readonly loggedInSubject = new BehaviorSubject<boolean>(false);
  private readonly userRoleSubject = new BehaviorSubject<string | null>(null);
  private readonly userIdSubject = new BehaviorSubject<number | null>(null);
  private readonly usernameSubject = new BehaviorSubject<string | null>(null);

  readonly loggedIn$ = this.loggedInSubject.asObservable();
  readonly userRole$ = this.userRoleSubject.asObservable();
  readonly userId$ = this.userIdSubject.asObservable();
  readonly username$ = this.usernameSubject.asObservable();

  constructor(private readonly http: HttpClient, private readonly tokenService: TokenService) {
    this.restoreSession();
  }

  register(user: User): Observable<any> {
    return this.http.post<User>(API_ENDPOINTS.AUTH.REGISTER, user);
  }

  /** POST /api/login; on success stores the JWT and publishes the user's role/id via BehaviorSubjects. */
  login(login: Login): Observable<any> {
    return this.http.post<LoginResponse>(API_ENDPOINTS.AUTH.LOGIN, login).pipe(
      tap(response => {
        this.tokenService.saveToken(response.token);
        localStorage.setItem(STORAGE_KEYS.USER_ROLE, response.userRole);
        localStorage.setItem(STORAGE_KEYS.USER_ID, String(response.userId));
        localStorage.setItem(STORAGE_KEYS.USERNAME, response.username);
        this.publish(true, response.userRole, response.userId, response.username);
      })
    );
  }

  logout(): void {
    this.tokenService.clear();
    this.publish(false, null, null, null);
  }

  isLoggedIn(): boolean {
    return this.tokenService.hasValidToken();
  }

  getToken(): string | null {
    return this.tokenService.getToken();
  }

  /** Role comes from the signed token claims, not from editable storage. */
  getUserRole(): string | null {
    return this.isLoggedIn() ? this.tokenService.decode()?.role ?? null : null;
  }

  getUserId(): number | null {
    return this.isLoggedIn() ? this.tokenService.decode()?.userId ?? null : null;
  }

  getUsername(): string | null {
    return this.isLoggedIn() ? this.tokenService.decode()?.username ?? null : null;
  }

  isManager(): boolean {
    return this.getUserRole() === 'Manager';
  }

  isClient(): boolean {
    return this.getUserRole() === 'Client';
  }

  private restoreSession(): void {
    if (this.tokenService.hasValidToken()) {
      const payload = this.tokenService.decode();
      this.publish(true, payload?.role ?? null, payload?.userId ?? null, payload?.username ?? null);
    } else {
      this.tokenService.clear();
    }
  }

  private publish(loggedIn: boolean, role: string | null, userId: number | null, username: string | null): void {
    this.loggedInSubject.next(loggedIn);
    this.userRoleSubject.next(role);
    this.userIdSubject.next(userId);
    this.usernameSubject.next(username);
  }
}
