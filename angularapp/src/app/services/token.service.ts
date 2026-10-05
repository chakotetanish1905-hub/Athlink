import { Injectable } from '@angular/core';
import { jwtDecode } from 'jwt-decode';
import { STORAGE_KEYS } from '../constants/constant';
import { JwtPayload } from '../models/jwt-payload.model';

/**
 * The single place that stores, reads and decodes the JWT.
 * Components and other services never touch localStorage or split the token themselves.
 */
@Injectable({ providedIn: 'root' })
export class TokenService {

  saveToken(token: string): void {
    localStorage.setItem(STORAGE_KEYS.TOKEN, token);
  }

  getToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.TOKEN);
  }

  clear(): void {
    Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key));
  }

  /** Decoded claims, or null when there is no token or it cannot be decoded. */
  decode(token: string | null = this.getToken()): JwtPayload | null {
    if (!token) {
      return null;
    }
    try {
      return jwtDecode<JwtPayload>(token);
    } catch {
      return null;
    }
  }

  isExpired(token: string | null = this.getToken()): boolean {
    const payload = this.decode(token);
    if (!payload || !payload.exp) {
      return true;
    }
    return payload.exp * 1000 <= Date.now();
  }

  hasValidToken(): boolean {
    return !this.isExpired();
  }
}
