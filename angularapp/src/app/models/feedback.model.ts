import { SupportAgent } from './support-agent.model';
import { Ticket } from './ticket.model';
import { User } from './user.model';

export interface Feedback {
  feedbackId?: number;
  feedbackText: string;
  date: Date;
  userId: number;
  agentId?: number;
  ticketId: number;
  category: string;
  rating: number;
  /** Read-only details the API returns alongside the ids. */
  user?: Partial<User>;
  supportAgent?: SupportAgent;
  ticket?: Ticket;
}
