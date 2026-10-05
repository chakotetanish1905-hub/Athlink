import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { API_ENDPOINTS } from '../constants/constant';
import { AuthInterceptor } from './auth.interceptor';

function fakeToken(): string {
  const encode = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  const exp = Math.floor(Date.now() / 1000) + 3600;
  return `${encode({ alg: 'HS256' })}.${encode({ sub: 'c@x.com', userId: 1, role: 'Client', username: 'c', exp })}.sig`;
}

describe('AuthInterceptor', () => {
  const token = fakeToken();
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule],
      providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }]
    });
    http = TestBed.inject(HttpClient);
    localStorage.setItem('token', token);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
    localStorage.clear();
  });

  it('adds the bearer token to protected API calls', () => {
    http.get(API_ENDPOINTS.TICKET.BASE).subscribe();
    const req = controller.expectOne(API_ENDPOINTS.TICKET.BASE);
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    req.flush([]);
  });

  it('does not add the header to login / register', () => {
    http.post(API_ENDPOINTS.AUTH.LOGIN, {}).subscribe();
    const req = controller.expectOne(API_ENDPOINTS.AUTH.LOGIN);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});
