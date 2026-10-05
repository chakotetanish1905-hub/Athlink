import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { ChatRequest, ChatResponse } from '../models/chat.model';

// FAQ chatbot API (public - no login needed)
@Injectable({ providedIn: 'root' })
export class ChatService {

  public apiUrl = apiUrl;

  constructor(private http: HttpClient) {}

  sendMessage(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.apiUrl}/api/chat`, request);
  }

  clearMemory(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/chat/memory/${sessionId}`);
  }
}
