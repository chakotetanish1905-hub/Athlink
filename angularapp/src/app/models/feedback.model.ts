import { SupportAgent } from './support-agent.model';
import { Ticket } from './ticket.model';
import { User } from './user.model';

// Same fields as the backend Feedback entity (user, supportAgent and ticket are nested objects).
export interface Feedback {
  feedbackId?: number;
  feedbackText: string;
  date?: string;
  category: string;
  rating: number;                // 1 - 5
  user?: User;
  supportAgent?: SupportAgent | null;
  ticket?: Ticket;
}
