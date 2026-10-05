// Sent to POST /api/chat
export interface ChatRequest {
  message: string;
  sessionId?: string | null;
}

// Returned by POST /api/chat
export interface ChatResponse {
  reply: string;
  matched: boolean;
  matchedQuestion: string | null;
  category: string | null;
  confidence: number;
  source: string;             // 'semantic' (Gemini) or 'lexical' (offline word matching)
  sessionId: string;
  resolvedQuestion: string;
}

// One message bubble in the chat window
export interface ChatBubble {
  from: 'user' | 'bot';
  text: string;
  response?: ChatResponse;    // bot answers: matched FAQ, confidence and source
  failed?: boolean;           // the request could not reach the server
  retryText?: string;         // the user message to send again
}
