import MockAdapter from 'axios-mock-adapter';
import { employeeApi } from '../api/employeeApi';
import api from '../api/axios';

const mock = new MockAdapter(api);

afterEach(() => {
  mock.reset();
  localStorage.clear();
});

const sampleEmployee = {
  id: 1,
  email: 'alice@company.com',
  firstName: 'Alice',
  lastName: 'Smith',
  role: 'EMPLOYEE',
  active: true,
};

describe('employeeApi', () => {
  describe('getAll', () => {
    it('fetches all active employees', async () => {
      mock.onGet('/employees').reply(200, [sampleEmployee]);

      const result = await employeeApi.getAll();
      expect(result).toHaveLength(1);
      expect(result[0].email).toBe('alice@company.com');
    });

    it('searches employees when search param provided', async () => {
      mock.onGet('/employees', { params: { search: 'Alice' } }).reply(200, [sampleEmployee]);

      const result = await employeeApi.getAll('Alice');
      expect(result[0].firstName).toBe('Alice');
    });

    it('throws on unauthorized access', async () => {
      mock.onGet('/employees').reply(401);

      await expect(employeeApi.getAll()).rejects.toThrow();
    });
  });

  describe('getById', () => {
    it('fetches single employee by id', async () => {
      mock.onGet('/employees/1').reply(200, sampleEmployee);

      const result = await employeeApi.getById(1);
      expect(result.id).toBe(1);
    });

    it('throws 404 for non-existing employee', async () => {
      mock.onGet('/employees/99').reply(404, { message: 'Employee not found' });

      await expect(employeeApi.getById(99)).rejects.toThrow();
    });
  });

  describe('create', () => {
    it('creates a new employee', async () => {
      const newEmpData = { email: 'new@co.com', password: 'pass', firstName: 'New', lastName: 'Hire', role: 'EMPLOYEE' };
      mock.onPost('/employees').reply(200, { ...sampleEmployee, id: 2, email: 'new@co.com' });

      const result = await employeeApi.create(newEmpData);
      expect(result.email).toBe('new@co.com');
    });

    it('throws 403 when non-admin tries to create', async () => {
      mock.onPost('/employees').reply(403);

      await expect(employeeApi.create({})).rejects.toThrow();
    });

    it('throws 400 on duplicate email', async () => {
      mock.onPost('/employees').reply(400, { message: 'Email already registered' });

      await expect(employeeApi.create({ email: 'dup@co.com' })).rejects.toThrow();
    });
  });

  describe('update', () => {
    it('updates employee details', async () => {
      const updated = { ...sampleEmployee, firstName: 'Updated' };
      mock.onPut('/employees/1').reply(200, updated);

      const result = await employeeApi.update(1, { firstName: 'Updated' });
      expect(result.firstName).toBe('Updated');
    });
  });

  describe('toggleStatus', () => {
    it('toggles employee active status', async () => {
      mock.onPatch('/employees/1/toggle-status').reply(200, 'Status updated');

      const result = await employeeApi.toggleStatus(1);
      expect(result).toBe('Status updated');
    });

    it('throws 403 when non-admin tries to toggle', async () => {
      mock.onPatch('/employees/1/toggle-status').reply(403);

      await expect(employeeApi.toggleStatus(1)).rejects.toThrow();
    });
  });

  describe('delete', () => {
    it('deletes employee', async () => {
      mock.onDelete('/employees/1').reply(200, 'Employee deactivated');

      const result = await employeeApi.delete(1);
      expect(result).toBe('Employee deactivated');
    });

    it('throws 404 when deleting non-existing employee', async () => {
      mock.onDelete('/employees/99').reply(404);

      await expect(employeeApi.delete(99)).rejects.toThrow();
    });
  });

  describe('getExEmployees', () => {
    it('fetches inactive employees', async () => {
      const inactiveEmp = { ...sampleEmployee, active: false };
      mock.onGet('/employees/ex').reply(200, [inactiveEmp]);

      const result = await employeeApi.getExEmployees();
      expect(result[0].active).toBe(false);
    });
  });

  describe('getOrgChart', () => {
    it('fetches org chart roots', async () => {
      mock.onGet('/employees/org-chart').reply(200, [sampleEmployee]);

      const result = await employeeApi.getOrgChart();
      expect(result).toHaveLength(1);
    });
  });
});
