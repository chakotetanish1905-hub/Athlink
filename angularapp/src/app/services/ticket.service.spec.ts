import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
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

  it('uses the SRS endpoints', () => {
    service.getAllTickets().subscribe(list => expect(list).toEqual([]));
    http.expectOne(service.apiUrl + '/api/ticket').flush(null);   // 204 No Content -> []

    service.getTicketById(5).subscribe();
    http.expectOne(service.apiUrl + '/api/ticket/5').flush({});

    service.getTicketsByUserId(2).subscribe();
    http.expectOne(service.apiUrl + '/api/ticket/user/2').flush([]);

    service.getTicketsByAgentId(3).subscribe();
    http.expectOne(service.apiUrl + '/api/ticket/agent/3').flush([]);

    service.deleteTicket(5).subscribe();
    expect(http.expectOne(service.apiUrl + '/api/ticket/5').request.method).toBe('DELETE');
  });

  it('addTicket() and updateTicket() send the ticket', () => {
    const ticket = { title: 'VPN down', description: 'It keeps dropping every hour', priority: 'High', issueCategory: 'Technical' };
    service.addTicket(ticket).subscribe();
    const post = http.expectOne(service.apiUrl + '/api/ticket');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual(ticket);
    post.flush({});

    service.updateTicket(9, ticket).subscribe();
    expect(http.expectOne(service.apiUrl + '/api/ticket/9').request.method).toBe('PUT');
  });
});
