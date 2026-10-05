export interface SupportAgent {
  agentId?: number;
  name: string;
  email: string;
  phone: string;
  expertise: string;
  experience: string;
  status: string;      // 'Available' | 'Unavailable'
  addedDate: Date;
  profile: string;     // Base64 (data URL) of the profile image
  shiftTiming: string;
  remarks: string;
}
