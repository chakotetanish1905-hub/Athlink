import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { API_ENDPOINTS } from '../constants/constant';
import { SupportAgent } from '../models/support-agent.model';

@Injectable({ providedIn: 'root' })
export class SupportAgentService {

  public apiUrl: string = apiUrl;

  constructor(private readonly http: HttpClient) {}

  getAllAgents(): Observable<SupportAgent[]> {
    return this.http.get<SupportAgent[] | null>(API_ENDPOINTS.SUPPORT_AGENT.BASE).pipe(map(list => list ?? []));
  }

  getAgentById(agentId: number): Observable<SupportAgent> {
    return this.http.get<SupportAgent>(API_ENDPOINTS.SUPPORT_AGENT.BY_ID(agentId));
  }

  addAgent(agent: SupportAgent): Observable<SupportAgent> {
    return this.http.post<SupportAgent>(API_ENDPOINTS.SUPPORT_AGENT.BASE, this.toPayload(agent));
  }

  updateAgent(agentId: number, agent: SupportAgent): Observable<SupportAgent> {
    return this.http.put<SupportAgent>(API_ENDPOINTS.SUPPORT_AGENT.BY_ID(agentId), this.toPayload(agent));
  }

  deleteAgent(agentId: number): Observable<void> {
    return this.http.delete<void>(API_ENDPOINTS.SUPPORT_AGENT.BY_ID(agentId));
  }

  /** A browser-created addedDate is dropped; the server sets it on creation and keeps it on update. */
  private toPayload(agent: SupportAgent): Partial<SupportAgent> {
    const payload: Partial<SupportAgent> = { ...agent };
    if (payload.addedDate instanceof Date) {
      delete payload.addedDate;
    }
    return payload;
  }
}
