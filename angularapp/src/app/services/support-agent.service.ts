import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { SupportAgent } from '../models/support-agent.model';

@Injectable({ providedIn: 'root' })
export class SupportAgentService {

  public apiUrl = apiUrl;

  constructor(private http: HttpClient) {}

  getAllAgents(): Observable<SupportAgent[]> {
    return this.http.get<SupportAgent[]>(`${this.apiUrl}/api/supportAgent`).pipe(map(list => list || []));
  }

  getAgentById(agentId: number): Observable<SupportAgent> {
    return this.http.get<SupportAgent>(`${this.apiUrl}/api/supportAgent/${agentId}`);
  }

  addAgent(agent: SupportAgent): Observable<SupportAgent> {
    return this.http.post<SupportAgent>(`${this.apiUrl}/api/supportAgent`, agent);
  }

  updateAgent(agentId: number, agent: SupportAgent): Observable<SupportAgent> {
    return this.http.put<SupportAgent>(`${this.apiUrl}/api/supportAgent/${agentId}`, agent);
  }

  deleteAgent(agentId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/supportAgent/${agentId}`);
  }
}
