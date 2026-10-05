import { apiUrl } from '../../apiconfig';

/** Every backend URL used by the app. Services never hard-code URLs. */
export const API_ENDPOINTS = {
  AUTH: {
    REGISTER: `${apiUrl}/api/register`,
    LOGIN: `${apiUrl}/api/login`
  },
  TICKET: {
    BASE: `${apiUrl}/api/ticket`,
    BY_ID: (ticketId: number) => `${apiUrl}/api/ticket/${ticketId}`,
    BY_USER: (userId: number) => `${apiUrl}/api/ticket/user/${userId}`,
    BY_AGENT: (agentId: number) => `${apiUrl}/api/ticket/agent/${agentId}`
  },
  SUPPORT_AGENT: {
    BASE: `${apiUrl}/api/supportAgent`,
    BY_ID: (agentId: number) => `${apiUrl}/api/supportAgent/${agentId}`
  },
  FEEDBACK: {
    BASE: `${apiUrl}/api/feedback`,
    BY_ID: (feedbackId: number) => `${apiUrl}/api/feedback/${feedbackId}`,
    BY_USER: (userId: number) => `${apiUrl}/api/feedback/user/${userId}`
  }
};

/** Requests that must never carry an Authorization header. */
export const PUBLIC_API_URLS: string[] = [API_ENDPOINTS.AUTH.LOGIN, API_ENDPOINTS.AUTH.REGISTER];

export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER_ROLE: 'userRole',
  USER_ID: 'userId',
  USERNAME: 'username'
};

export const ROLES = {
  MANAGER: 'Manager',
  CLIENT: 'Client'
};

export const PRIORITIES: string[] = ['High', 'Medium', 'Low'];

export const TICKET_STATUS = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed'
};

export const AGENT_STATUS = {
  AVAILABLE: 'Available',
  UNAVAILABLE: 'Unavailable'
};

export const ISSUE_CATEGORIES: string[] = [
  'Connectivity', 'Platform Bug', 'Content Issue', 'Tech Stacks', 'Technical', 'Billing', 'General'
];

export const EXPERTISE_OPTIONS: string[] = [
  'Connectivity Specialist', 'Platform Support Engineer', 'Content Manager', 'Tech Stack Expert',
  'Technical Support', 'Billing Specialist', 'General Support'
];

/** Used by the manager "Assign Agent" popup to suggest agents for an issue category. */
export const CATEGORY_EXPERTISE_MAP: { [category: string]: string[] } = {
  'Connectivity': ['Connectivity Specialist'],
  'Platform Bug': ['Platform Support Engineer'],
  'Content Issue': ['Content Manager'],
  'Tech Stacks': ['Tech Stack Expert'],
  'Technical': ['Technical Support', 'Tech Stack Expert', 'Platform Support Engineer'],
  'Billing': ['Billing Specialist'],
  'General': ['General Support']
};

export const FEEDBACK_CATEGORIES: string[] = [
  'Service Quality', 'Professionalism', 'Response Time', 'Technical Knowledge', 'Communication'
];

export const RATINGS: number[] = [1, 2, 3, 4, 5];

export const VALIDATION_PATTERNS = {
  EMAIL: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
  MOBILE: /^\d{10}$/
};

export const PASSWORD_MIN_LENGTH = 8;
export const PROFILE_MAX_BYTES = 1024 * 1024;
