import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { Ticket } from '../models/ticket.model';

// The "Authorization: Bearer <token>" header is added by AuthInterceptor for every request.
@Injectable({ providedIn: 'root' })
export class TicketService {

  public apiUrl = apiUrl;

  constructor(private http: HttpClient) {}

  getAllTickets(): Observable<Ticket[]> {
    // 204 No Content has an empty body, so turn null into an empty list
    return this.http.get<Ticket[]>(`${this.apiUrl}/api/ticket`).pipe(map(list => list || []));
  }

  getTicketById(ticketId: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.apiUrl}/api/ticket/${ticketId}`);
  }

  addTicket(ticket: Ticket): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.apiUrl}/api/ticket`, ticket);
  }

  updateTicket(ticketId: number, ticket: Ticket): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.apiUrl}/api/ticket/${ticketId}`, ticket);
  }

  deleteTicket(ticketId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/api/ticket/${ticketId}`);
  }

  getTicketsByAgentId(agentId: number): Observable<Ticket[]> {
    return this.http.get<Ticket[]>(`${this.apiUrl}/api/ticket/agent/${agentId}`).pipe(map(list => list || []));
  }

  getTicketsByUserId(userId: number): Observable<Ticket[]> {
    return this.http.get<Ticket[]>(`${this.apiUrl}/api/ticket/user/${userId}`).pipe(map(list => list || []));
  }
}
