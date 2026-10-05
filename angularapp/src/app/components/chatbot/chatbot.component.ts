import { Component } from '@angular/core';
import { ChatBubble, ChatResponse } from '../../models/chat.model';
import { ChatService } from '../../services/chat.service';

const SESSION_KEY = 'chatSessionId';

// Floating SupportSphere AI widget, mounted once in app.component.html so it is on every page.
// It only answers questions from the FAQs - it never creates, edits or deletes anything.
@Component({
  selector: 'app-chatbot',
  templateUrl: './chatbot.component.html',
  styleUrls: ['./chatbot.component.css']
})
export class ChatbotComponent {

  open = false;
  message = '';
  typing = false;
  bubbles: ChatBubble[] = [this.greeting()];

  // Example questions shown before the first message
  suggestions = [
    'How do I raise a new ticket?',
    'How do I mark my ticket as resolved?',
    'How are support agents assigned to tickets?',
    'How do I leave feedback for an agent?'
  ];

  constructor(private chatService: ChatService) {}

  // The session id is kept in sessionStorage so follow-up questions keep their conversation memory
  get sessionId(): string | null {
    try {
      return sessionStorage.getItem(SESSION_KEY);
    } catch {
      return null;
    }
  }

  get showSuggestions(): boolean {
    return this.bubbles.length === 1 && !this.typing;
  }

  toggle(): void {
    this.open = !this.open;
  }

  send(text?: string): void {
    const question = (text !== undefined ? text : this.message).trim();
    if (!question || this.typing) {
      return;
    }
    this.message = '';
    this.bubbles.push({ from: 'user', text: question });
    this.typing = true;

    this.chatService.sendMessage({ message: question, sessionId: this.sessionId }).subscribe({
      next: (response) => {
        this.typing = false;
        this.saveSessionId(response.sessionId);
        this.bubbles.push({ from: 'bot', text: response.reply, response: response });
      },
      error: () => {
        this.typing = false;
        this.bubbles.push({
          from: 'bot', failed: true, retryText: question,
          text: 'I couldn’t reach SupportSphere just now. Check your connection and try again.'
        });
      }
    });
  }

  retry(bubble: ChatBubble): void {
    this.bubbles = this.bubbles.filter(b => b !== bubble);
    if (bubble.retryText) {
      // Remove the user's message too; send() adds it again
      const last = this.bubbles[this.bubbles.length - 1];
      if (last && last.from === 'user' && last.text === bubble.retryText) {
        this.bubbles.pop();
      }
      this.send(bubble.retryText);
    }
  }

  // Clears the server-side memory (DELETE /api/chat/memory/{sessionId}) and starts a new conversation
  clear(): void {
    const sessionId = this.sessionId;
    if (sessionId) {
      this.chatService.clearMemory(sessionId).subscribe({ error: () => {} });
    }
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      // sessionStorage not available - nothing to remove
    }
    this.bubbles = [this.greeting()];
    this.message = '';
  }

  confidencePercent(response: ChatResponse): string {
    return Math.round(response.confidence * 100) + '%';
  }

  sourceLabel(response: ChatResponse): string {
    return response.source === 'semantic' ? 'Semantic FAQ' : 'Offline FAQ';
  }

  private saveSessionId(sessionId: string): void {
    try {
      sessionStorage.setItem(SESSION_KEY, sessionId);
    } catch {
      // sessionStorage not available - the conversation still works for this message
    }
  }

  private greeting(): ChatBubble {
    return {
      from: 'bot',
      text: 'Hi! I’m SupportSphere AI. Ask me about tickets, support agents, feedback or your account.'
    };
  }
}
