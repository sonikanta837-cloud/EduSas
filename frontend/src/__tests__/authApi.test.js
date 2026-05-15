import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { authApi } from '../api/authApi';
import api from '../api/axios';

const mock = new MockAdapter(api);

afterEach(() => {
  mock.reset();
  localStorage.clear();
});

describe('authApi', () => {
  const loginResponse = {
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    userId: 1,
    email: 'alice@company.com',
    role: 'EMPLOYEE',
    fullName: 'Alice Smith',
  };

  describe('login', () => {
    it('sends POST /auth/login with credentials', async () => {
      mock.onPost('/auth/login').reply(200, loginResponse);

      const result = await authApi.login({ email: 'alice@company.com', password: 'pass' });

      expect(result).toEqual(loginResponse);
    });

    it('throws on 401 bad credentials', async () => {
      mock.onPost('/auth/login').reply(401, { message: 'Invalid credentials' });

      await expect(authApi.login({ email: 'bad@email.com', password: 'wrong' }))
        .rejects.toThrow();
    });

    it('throws on network error', async () => {
      mock.onPost('/auth/login').networkError();

      await expect(authApi.login({ email: 'a@b.com', password: 'p' }))
        .rejects.toThrow();
    });
  });

  describe('logout', () => {
    it('sends POST /auth/logout', async () => {
      mock.onPost('/auth/logout').reply(200, 'Logged out successfully');

      const result = await authApi.logout();
      expect(result).toBe('Logged out successfully');
    });
  });

  describe('register', () => {
    it('sends POST /auth/register', async () => {
      mock.onPost('/auth/register').reply(200, 'Employee registered successfully');

      const result = await authApi.register({
        email: 'new@company.com',
        password: 'pass',
        firstName: 'New',
        lastName: 'User',
        role: 'EMPLOYEE',
      });

      expect(result).toBe('Employee registered successfully');
    });

    it('throws 400 on duplicate email', async () => {
      mock.onPost('/auth/register').reply(400, { message: 'Email already registered' });

      await expect(authApi.register({ email: 'dup@company.com', password: 'p', firstName: 'D', lastName: 'U', role: 'EMPLOYEE' }))
        .rejects.toThrow();
    });
  });

  describe('refresh', () => {
    it('sends POST /auth/refresh with token as param', async () => {
      mock.onPost('/auth/refresh').reply(200, loginResponse);

      const result = await authApi.refresh('old-refresh-token');
      expect(result).toEqual(loginResponse);
    });

    it('throws on invalid refresh token', async () => {
      mock.onPost('/auth/refresh').reply(400, { message: 'Invalid refresh token' });

      await expect(authApi.refresh('bad-token')).rejects.toThrow();
    });
  });
});
