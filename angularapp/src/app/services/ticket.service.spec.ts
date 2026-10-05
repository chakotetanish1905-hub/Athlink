import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ENDPOINTS } from '../constants/constant';
import { Ticket } from '../models/ticket.model';
import { TicketService } from './ticket.service';

describe('TicketService', () => {
  let service: TicketService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(TicketService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAllTickets() should map 204 No Content to an empty list', () => {
    let result: Ticket[] | undefined;
    service.getAllTickets().subscribe(list => (result = list));
    http.expectOne(API_ENDPOINTS.TICKET.BASE).flush(null, { status: 204, statusText: 'No Content' });
    expect(result).toEqual([]);
  });

  it('addTicket() should POST without browser-created dates or nested objects', () => {
    const ticket: Ticket = {
      title: 'VPN', description: 'down', priority: 'High', status: 'Open', createdDate: new Date(),
      issueCategory: 'Connectivity', userId: 1, supportAgent: undefined
    };
    service.addTicket(ticket).subscribe();
    const req = http.expectOne(API_ENDPOINTS.TICKET.BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.createdDate).toBeUndefined();
    expect(req.request.body.title).toBe('VPN');
    req.flush({});
  });

  it('should call the user, agent, update and delete endpoints', () => {
    service.getTicketsByUserId(3).subscribe();
    http.expectOne(API_ENDPOINTS.TICKET.BY_USER(3)).flush([]);
    service.getTicketsByAgentId(4).subscribe();
    http.expectOne(API_ENDPOINTS.TICKET.BY_AGENT(4)).flush([]);
    service.updateTicket(5, { title: 't', description: 'd', priority: 'Low', status: 'Closed',
      createdDate: '2025-01-01' as unknown as Date, issueCategory: 'General', userId: 1 }).subscribe();
    const put = http.expectOne(API_ENDPOINTS.TICKET.BY_ID(5));
    expect(put.request.method).toBe('PUT');
    put.flush({});
    service.deleteTicket(5).subscribe();
    expect(http.expectOne(API_ENDPOINTS.TICKET.BY_ID(5)).request.method).toBe('DELETE');
  });
});
