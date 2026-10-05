export interface SupportAgent {
  agentId?: number;
  name: string;
  email: string;
  phone: string;
  expertise: string;         // e.g. Networking, Software Support
  experience: string;        // e.g. "3 years"
  status: string;            // 'Available' | 'Unavailable'
  addedDate?: string;        // set by the backend
  profile?: string | null;   // base64 string of the profile / resume (optional)
  shiftTiming: string;       // e.g. "9 AM - 6 PM"
  remarks: string;
}
