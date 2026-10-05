import { User } from './user.model';

describe('User model', () => {
  it('should allow creating a typed User object', () => {
    const value = {} as User;
    expect(value).toBeTruthy();
  });
});
