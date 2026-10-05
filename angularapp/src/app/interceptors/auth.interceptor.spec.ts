import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AuthInterceptor } from './auth.interceptor';

@Component({ template: '' })
class DummyComponent {}

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule.withRoutes([{ path: 'login', component: DummyComponent }])],
      providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }]
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds the Bearer token to API calls', () => {
    localStorage.setItem('token', 'abc');
    http.get('/api/ticket').subscribe();
    expect(controller.expectOne('/api/ticket').request.headers.get('Authorization')).toBe('Bearer abc');
  });

  it('does not add a token to login', () => {
    localStorage.setItem('token', 'abc');
    http.post('/api/login', {}).subscribe();
    expect(controller.expectOne('/api/login').request.headers.has('Authorization')).toBeFalse();
  });

  it('logs out on 401', () => {
    localStorage.setItem('token', 'abc');
    http.get('/api/ticket').subscribe({ error: () => {} });
    controller.expectOne('/api/ticket').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem('token')).toBeNull();
  });
});
