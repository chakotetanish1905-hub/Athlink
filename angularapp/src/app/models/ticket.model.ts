import { SupportAgent } from './support-agent.model';
import { User } from './user.model';

export interface Ticket {
  ticketId?: number;
  title: string;
  description: string;
  priority: string;            // 'High' | 'Medium' | 'Low'
  status: string;              // 'Open' | 'In Progress' | 'Resolved' | 'Closed'
  createdDate: Date;
  resolutionDate?: Date;
  issueCategory: string;
  resolutionSummary?: string;  // required before status becomes 'Resolved'
  userId: number;
  agentId?: number;
  satisfied?: boolean;
  /** Read-only details the API returns alongside the ids. */
  user?: Partial<User>;
  supportAgent?: SupportAgent;
}
