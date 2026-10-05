import { SupportAgent } from './support-agent.model';
import { User } from './user.model';

// Same fields as the backend Ticket entity.
// The API returns the client (user) and the assigned agent (supportAgent) as nested objects.
export interface Ticket {
  ticketId?: number;
  title: string;
  description: string;
  priority: string;              // 'High' | 'Medium' | 'Low'
  status?: string;               // 'Open' | 'In Progress' | 'Resolved' | 'Closed' (new tickets start Open)
  createdDate?: string;
  resolutionDate?: string | null;
  issueCategory: string;         // 'Technical' | 'Billing' | 'General'
  resolutionSummary?: string | null;
  satisfied?: boolean | null;
  user?: User;
  supportAgent?: SupportAgent | null;
}
