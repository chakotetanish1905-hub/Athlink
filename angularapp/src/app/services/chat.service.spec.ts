import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ChatService } from './chat.service';

describe('ChatService', () => {
  let service: ChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(ChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sendMessage() posts to /api/chat', () => {
    service.sendMessage({ message: 'How do I raise a ticket?', sessionId: 'abc' }).subscribe();
    const req = http.expectOne(service.apiUrl + '/api/chat');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ message: 'How do I raise a ticket?', sessionId: 'abc' });
    req.flush({});
  });

  it('clearMemory() deletes the session memory', () => {
    service.clearMemory('abc').subscribe();
    expect(http.expectOne(service.apiUrl + '/api/chat/memory/abc').request.method).toBe('DELETE');
  });
});
