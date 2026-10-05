import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../constants/constant';
import { AuthService } from './auth.service';

/** Builds an unsigned JWT-shaped token; jwt-decode only reads the payload. */
function fakeToken(payload: object): string {
  const encode = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('should be created and logged out by default', () => {
    expect(service).toBeTruthy();
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.getUserRole()).toBeNull();
  });

  it('register() should POST to /api/register', () => {
    service.register({ email: 'a@b.com', password: 'Password1', username: 'abc', mobileNumber: '9876543210', userRole: 'Client' })
      .subscribe();
    const req = http.expectOne(API_ENDPOINTS.AUTH.REGISTER);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('login() should store the token and expose role / id from the JWT', () => {
    const exp = Math.floor(Date.now() / 1000) + 3600;
    const token = fakeToken({ sub: 'm@b.com', userId: 7, role: 'Manager', username: 'boss', exp });
    let role: string | null = null;
    service.userRole$.subscribe(r => (role = r));

    service.login({ email: 'm@b.com', password: 'Password1' }).subscribe();
    const req = http.expectOne(API_ENDPOINTS.AUTH.LOGIN);
    expect(req.request.method).toBe('POST');
    req.flush({ token, username: 'boss', userRole: 'Manager', userId: 7 });

    expect(service.getToken()).toBe(token);
    expect(service.isLoggedIn()).toBeTrue();
    expect(service.getUserRole()).toBe('Manager');
    expect(service.getUserId()).toBe(7);
    expect(role as unknown as string).toBe('Manager');
  });

  it('should treat an expired token as logged out', () => {
    const exp = Math.floor(Date.now() / 1000) - 10;
    localStorage.setItem('token', fakeToken({ sub: 'x@y.com', userId: 1, role: 'Client', username: 'x', exp }));
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('logout() should clear the session', () => {
    localStorage.setItem('token', 'abc');
    service.logout();
    expect(service.getToken()).toBeNull();
  });
});
