import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('register() sends POST /api/register', () => {
    service.register({ username: 'Rahul', email: 'r@test.com', password: 'Password@1', mobileNumber: '9876543210', userRole: 'Client' }).subscribe();
    const req = http.expectOne(service.apiUrl + '/api/register');
    expect(req.request.method).toBe('POST');
    req.flush({ userId: 1 });
  });

  it('login() stores the token, role and id', () => {
    service.login({ email: 'r@test.com', password: 'Password@1' }).subscribe();
    const req = http.expectOne(service.apiUrl + '/api/login');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'abc', username: 'Rahul', userRole: 'Client', userId: 7 });

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.getToken()).toBe('abc');
    expect(service.getUserRole()).toBe('Client');
    expect(service.getUserId()).toBe(7);
    expect(service.userRole.value).toBe('Client');
  });

  it('logout() clears the session', () => {
    localStorage.setItem('token', 'abc');
    service.logout();
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.userId.value).toBeNull();
  });
});
