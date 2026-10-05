import { Ticket } from './ticket.model';

describe('Ticket model', () => {
  it('should allow creating a typed Ticket object', () => {
    const value = {} as Ticket;
    expect(value).toBeTruthy();
  });
});
