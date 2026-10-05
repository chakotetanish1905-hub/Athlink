import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../../services/chat.service';
import { ChatbotComponent } from './chatbot.component';

describe('ChatbotComponent', () => {
  let http: HttpTestingController;
  let apiUrl: string;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule],
      declarations: [ChatbotComponent]
    });
    http = TestBed.inject(HttpTestingController);
    apiUrl = TestBed.inject(ChatService).apiUrl;
  });

  afterEach(() => http.verify());

  const reply = { reply: 'Use Add Ticket.', matched: true, matchedQuestion: 'How do I raise a new ticket?', category: 'Tickets',
    confidence: 0.87, source: 'lexical', sessionId: 's-123', resolvedQuestion: 'How do I raise a ticket?' };

  it('stores the session id and sends it with follow-up questions', () => {
    const chat = TestBed.createComponent(ChatbotComponent).componentInstance;

    chat.send('How do I raise a ticket?');
    const first = http.expectOne(apiUrl + '/api/chat');
    expect(first.request.body.sessionId).toBeNull();
    first.flush(reply);
    expect(sessionStorage.getItem('chatSessionId')).toBe('s-123');
    expect(chat.bubbles.length).toBe(3);
    expect(chat.confidencePercent(reply)).toBe('87%');

    chat.send('What about editing it?');
    const second = http.expectOne(apiUrl + '/api/chat');
    expect(second.request.body.sessionId).toBe('s-123');
    second.flush(reply);
  });

  it('clear() calls the memory endpoint and resets the conversation', () => {
    sessionStorage.setItem('chatSessionId', 's-123');
    const chat = TestBed.createComponent(ChatbotComponent).componentInstance;
    chat.bubbles.push({ from: 'user', text: 'hello' });

    chat.clear();
    http.expectOne(apiUrl + '/api/chat/memory/s-123').flush(null);
    expect(sessionStorage.getItem('chatSessionId')).toBeNull();
    expect(chat.bubbles.length).toBe(1);
  });

  it('shows a retry bubble when the server cannot be reached', () => {
    const chat = TestBed.createComponent(ChatbotComponent).componentInstance;
    chat.send('How do I log out?');
    http.expectOne(apiUrl + '/api/chat').error(new ProgressEvent('error'));
    expect(chat.bubbles[chat.bubbles.length - 1].failed).toBeTrue();
  });
});
