import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SupportAgentService } from './support-agent.service';

describe('SupportAgentService', () => {
  let service: SupportAgentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(SupportAgentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the SRS endpoints', () => {
    service.getAllAgents().subscribe(list => expect(list).toEqual([]));
    http.expectOne(service.apiUrl + '/api/supportAgent').flush(null);

    service.getAgentById(4).subscribe();
    http.expectOne(service.apiUrl + '/api/supportAgent/4').flush({});

    service.deleteAgent(4).subscribe();
    expect(http.expectOne(service.apiUrl + '/api/supportAgent/4').request.method).toBe('DELETE');
  });
});
