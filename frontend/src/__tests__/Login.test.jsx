import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import MockAdapter from 'axios-mock-adapter';
import LoginPage from '../pages/Login';
import authReducer from '../store/authSlice';
import api from '../api/axios';

const mock = new MockAdapter(api);

const renderLogin = (initialState = {}) => {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: { user: null, token: null, loading: false, error: null, ...initialState },
    },
  });

  const result = render(
    <Provider store={store}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </Provider>
  );

  return { store, ...result };
};

const getPasswordInput = (container) => container.querySelector('input[type="password"]');

beforeEach(() => {
  mock.reset();
  localStorage.clear();
});

describe('LoginPage', () => {
  describe('render', () => {
    it('renders the login form', () => {
      const { container } = renderLogin();
      expect(screen.getByLabelText(/email address/i)).toBeInTheDocument();
      expect(getPasswordInput(container)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    });

    it('renders forgot password button', () => {
      renderLogin();
      expect(screen.getByRole('button', { name: /forgot password/i })).toBeInTheDocument();
    });

    it('shows error alert when auth error exists', () => {
      renderLogin({ error: 'Invalid credentials' });
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid credentials');
    });
  });

  describe('form interaction', () => {
    it('allows typing in email and password fields', async () => {
      const { container } = renderLogin();
      const emailInput = screen.getByLabelText(/email address/i);
      const passwordInput = getPasswordInput(container);

      await userEvent.type(emailInput, 'test@example.com');
      await userEvent.type(passwordInput, 'mypassword');

      expect(emailInput).toHaveValue('test@example.com');
      expect(passwordInput).toHaveValue('mypassword');
    });

    it('toggles password visibility', async () => {
      const { container } = renderLogin();
      const passwordInput = getPasswordInput(container);
      expect(passwordInput).toHaveAttribute('type', 'password');

      const toggleBtn = screen.getByRole('button', { name: /toggle password visibility/i });
      await userEvent.click(toggleBtn);
      expect(passwordInput).toHaveAttribute('type', 'text');

      await userEvent.click(toggleBtn);
      expect(passwordInput).toHaveAttribute('type', 'password');
    });
  });

  describe('form submission', () => {
    it('dispatches login on form submit', async () => {
      const loginResponse = {
        accessToken: 'tok', refreshToken: 'ref', userId: 1,
        email: 'alice@company.com', role: 'EMPLOYEE', fullName: 'Alice',
      };
      mock.onPost('/auth/login').reply(200, loginResponse);
      mock.onGet('/employees/user/1').reply(200, { id: 5 });
      mock.onPost('/timesheet/check-in').reply(200, {});

      const { container } = renderLogin();
      await userEvent.type(screen.getByLabelText(/email address/i), 'alice@company.com');
      await userEvent.type(getPasswordInput(container), 'password');
      await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(localStorage.getItem('accessToken')).toBe('tok');
      });
    });

    it('shows error message on failed login', async () => {
      mock.onPost('/auth/login').reply(401, { message: 'Invalid email or password' });

      const { container } = renderLogin();
      await userEvent.type(screen.getByLabelText(/email address/i), 'bad@email.com');
      await userEvent.type(getPasswordInput(container), 'wrongpass');
      await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

      await waitFor(() => {
        expect(screen.getByRole('alert')).toBeInTheDocument();
      });
    });
  });

  describe('forgot password dialog', () => {
    it('opens forgot password dialog when clicked', async () => {
      renderLogin();
      await userEvent.click(screen.getByRole('button', { name: /forgot password/i }));
      expect(screen.getByText(/reset password/i)).toBeInTheDocument();
    });

    it('shows error when submitting empty email', async () => {
      renderLogin();
      await userEvent.click(screen.getByRole('button', { name: /forgot password/i }));
      await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));
      expect(screen.getByText(/please enter your email/i)).toBeInTheDocument();
    });

    it('shows success message on valid email submission', async () => {
      mock.onPost('/auth/forgot-password').reply(200, 'Password reset email sent');

      renderLogin();
      await userEvent.click(screen.getByRole('button', { name: /forgot password/i }));

      const dialogEmailInput = screen.getAllByLabelText(/email address/i)[1];
      await userEvent.type(dialogEmailInput, 'alice@company.com');
      await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));

      await waitFor(() => {
        expect(screen.getByText(/check your inbox/i)).toBeInTheDocument();
      });
    });

    it('closes dialog on cancel', async () => {
      renderLogin();
      await userEvent.click(screen.getByRole('button', { name: /forgot password/i }));
      expect(screen.getByText(/reset password/i)).toBeInTheDocument();

      await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
      await waitFor(() => {
        expect(screen.queryByRole('button', { name: /send reset link/i })).not.toBeInTheDocument();
      });
    });
  });
});
