import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { Login, LoginDTO } from '../models/login.model';
import { User } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {

  public apiUrl = apiUrl;

  // Other components can subscribe to these to react to login / logout
  public userRole = new BehaviorSubject<string | null>(localStorage.getItem('userRole'));
  public userId = new BehaviorSubject<number | null>(this.readUserId());

  constructor(private http: HttpClient) {}

  register(user: User): Observable<any> {
    return this.http.post(`${this.apiUrl}/api/register`, user);
  }

  // On success the JWT and the user details are kept in localStorage
  login(login: Login): Observable<any> {
    return this.http.post<LoginDTO>(`${this.apiUrl}/api/login`, login).pipe(
      tap(response => {
        localStorage.setItem('token', response.token);
        localStorage.setItem('userRole', response.userRole);
        localStorage.setItem('userId', String(response.userId));
        localStorage.setItem('username', response.username);
        this.userRole.next(response.userRole);
        this.userId.next(response.userId);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('userRole');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    this.userRole.next(null);
    this.userId.next(null);
  }

  isLoggedIn(): boolean {
    return localStorage.getItem('token') !== null;
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getUserRole(): string | null {
    return localStorage.getItem('userRole');
  }

  getUserId(): number {
    return Number(localStorage.getItem('userId'));
  }

  getUsername(): string {
    return localStorage.getItem('username') || '';
  }

  isManager(): boolean {
    return this.getUserRole() === 'Manager';
  }

  private readUserId(): number | null {
    const id = localStorage.getItem('userId');
    return id ? Number(id) : null;
  }
}
