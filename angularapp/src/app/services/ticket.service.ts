import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { apiUrl } from '../../apiconfig';
import { API_ENDPOINTS } from '../constants/constant';
import { Ticket } from '../models/ticket.model';

/** Ticket API. The Authorization header is added by AuthInterceptor, never here. */
@Injectable({ providedIn: 'root' })
export class TicketService {

  public apiUrl: string = apiUrl;

  constructor(private readonly http: HttpClient) {}

  getAllTickets(): Observable<Ticket[]> {
    // 204 No Content arrives as a null body.
    return this.http.get<Ticket[] | null>(API_ENDPOINTS.TICKET.BASE).pipe(map(list => list ?? []));
  }

  getTicketById(ticketId: number): Observable<Ticket> {
    return this.http.get<Ticket>(API_ENDPOINTS.TICKET.BY_ID(ticketId));
  }

  addTicket(ticket: Ticket): Observable<Ticket> {
    return this.http.post<Ticket>(API_ENDPOINTS.TICKET.BASE, this.toPayload(ticket));
  }

  updateTicket(ticketId: number, ticket: Ticket): Observable<Ticket> {
    return this.http.put<Ticket>(API_ENDPOINTS.TICKET.BY_ID(ticketId), this.toPayload(ticket));
  }

  deleteTicket(ticketId: number): Observable<void> {
    return this.http.delete<void>(API_ENDPOINTS.TICKET.BY_ID(ticketId));
  }

  getTicketsByAgentId(agentId: number): Observable<Ticket[]> {
    return this.http.get<Ticket[] | null>(API_ENDPOINTS.TICKET.BY_AGENT(agentId)).pipe(map(list => list ?? []));
  }

  getTicketsByUserId(userId: number): Observable<Ticket[]> {
    return this.http.get<Ticket[] | null>(API_ENDPOINTS.TICKET.BY_USER(userId)).pipe(map(list => list ?? []));
  }

  /**
   * Sends only the SRS Ticket fields. Nested read-only details stay client-side, and dates created in the
   * browser are dropped because the server owns createdDate/resolutionDate (LocalDate, server time zone).
   */
  private toPayload(ticket: Ticket): Ticket {
    const { user, supportAgent, ...payload } = ticket;
    if (payload.createdDate instanceof Date) {
      delete (payload as Partial<Ticket>).createdDate;
    }
    if (payload.resolutionDate instanceof Date) {
      delete payload.resolutionDate;
    }
    return payload;
  }
}
