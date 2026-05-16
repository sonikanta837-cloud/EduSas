import MockAdapter from 'axios-mock-adapter';
import { courseApi } from '../api/courseApi';
import api from '../api/axios';

const mock = new MockAdapter(api);

afterEach(() => {
  mock.reset();
  localStorage.clear();
});

// ── Fixtures ───────────────────────────────────────────────────────────────

const sampleCourse = {
  id: 1,
  title: 'Spring Boot Fundamentals',
  description: 'Learn Spring Boot from scratch',
  durationHours: 8,
  active: true,
};

const sampleCertificate = {
  id: 1,
  courseTitle: 'Spring Boot Fundamentals',
  certificateNumber: 'CERT-2024-001',
  issuedAt: '2024-06-15T10:00:00',
  employeeName: 'John Doe',
  employeeId: 1,
  score: 85,
};

// ── downloadCertificatePdf ─────────────────────────────────────────────────

describe('courseApi.downloadCertificatePdf', () => {
  it('calls GET /certificates/download with certNo param and responseType blob', async () => {
    // axios-mock-adapter matches any blob response
    const fakeBlob = new Blob(['%PDF fake'], { type: 'application/pdf' });
    mock.onGet('/certificates/download', { params: { certNo: 'CERT-2024-001' } })
      .reply(200, fakeBlob, { 'Content-Type': 'application/pdf' });

    const result = await courseApi.downloadCertificatePdf('CERT-2024-001');

    // The axios response interceptor returns response.data directly
    expect(result).toBeDefined();
    expect(mock.history.get.length).toBe(1);
    expect(mock.history.get[0].url).toBe('/certificates/download');
    expect(mock.history.get[0].params).toEqual({ certNo: 'CERT-2024-001' });
    expect(mock.history.get[0].responseType).toBe('blob');
  });

  it('sends request with responseType set to blob', async () => {
    mock.onGet('/certificates/download').reply(200, new Blob(['%PDF']));

    await courseApi.downloadCertificatePdf('CERT-2024-001');

    const requestConfig = mock.history.get[0];
    expect(requestConfig.responseType).toBe('blob');
  });

  it('passes different certNo values correctly', async () => {
    const certNo = 'EMP-CERT-XYZ-999';
    mock.onGet('/certificates/download', { params: { certNo } })
      .reply(200, new Blob(['%PDF']));

    await courseApi.downloadCertificatePdf(certNo);

    expect(mock.history.get[0].params.certNo).toBe(certNo);
  });

  it('throws on 404 when certNo not found', async () => {
    mock.onGet('/certificates/download').reply(404, { message: 'Certificate not found: UNKNOWN' });

    await expect(courseApi.downloadCertificatePdf('UNKNOWN')).rejects.toThrow();
  });

  it('throws on 401 when unauthenticated', async () => {
    mock.onGet('/certificates/download').reply(401, { message: 'Unauthorized' });

    await expect(courseApi.downloadCertificatePdf('CERT-2024-001')).rejects.toThrow();
  });

  it('throws on network error', async () => {
    mock.onGet('/certificates/download').networkError();

    await expect(courseApi.downloadCertificatePdf('CERT-2024-001')).rejects.toThrow();
  });

  it('throws on 500 server error', async () => {
    mock.onGet('/certificates/download').reply(500, { message: 'Internal server error' });

    await expect(courseApi.downloadCertificatePdf('CERT-2024-001')).rejects.toThrow();
  });

  it('handles certNo with special characters', async () => {
    const specialCertNo = 'CERT/2024@#001';
    mock.onGet('/certificates/download', { params: { certNo: specialCertNo } })
      .reply(200, new Blob(['%PDF']));

    await courseApi.downloadCertificatePdf(specialCertNo);

    expect(mock.history.get[0].params.certNo).toBe(specialCertNo);
  });

  it('handles empty string certNo (passes it through, server handles validation)', async () => {
    mock.onGet('/certificates/download', { params: { certNo: '' } })
      .reply(400, { message: 'Certificate not found' });

    await expect(courseApi.downloadCertificatePdf('')).rejects.toThrow();
  });
});

// ── getMyCertificates ──────────────────────────────────────────────────────

describe('courseApi.getMyCertificates', () => {
  it('fetches current user certificates from GET /certificates/my', async () => {
    mock.onGet('/certificates/my').reply(200, [sampleCertificate]);

    const result = await courseApi.getMyCertificates();

    expect(result).toHaveLength(1);
    expect(result[0].certificateNumber).toBe('CERT-2024-001');
    expect(result[0].courseTitle).toBe('Spring Boot Fundamentals');
    expect(result[0].score).toBe(85);
  });

  it('returns empty array when employee has no certificates', async () => {
    mock.onGet('/certificates/my').reply(200, []);

    const result = await courseApi.getMyCertificates();

    expect(result).toHaveLength(0);
  });

  it('throws on 401 unauthenticated request', async () => {
    mock.onGet('/certificates/my').reply(401);

    await expect(courseApi.getMyCertificates()).rejects.toThrow();
  });

  it('throws on 404 when employee profile not found', async () => {
    mock.onGet('/certificates/my').reply(404, { message: 'Employee not found for email' });

    await expect(courseApi.getMyCertificates()).rejects.toThrow();
  });
});

// ── getAllCertificates ─────────────────────────────────────────────────────

describe('courseApi.getAllCertificates', () => {
  it('fetches all certificates from GET /certificates', async () => {
    mock.onGet('/certificates').reply(200, [sampleCertificate]);

    const result = await courseApi.getAllCertificates();

    expect(result).toHaveLength(1);
    expect(result[0].certificateNumber).toBe('CERT-2024-001');
  });

  it('throws on 403 for non-admin users', async () => {
    mock.onGet('/certificates').reply(403, { message: 'Access denied' });

    await expect(courseApi.getAllCertificates()).rejects.toThrow();
  });

  it('returns empty array when no certificates exist', async () => {
    mock.onGet('/certificates').reply(200, []);

    const result = await courseApi.getAllCertificates();

    expect(result).toHaveLength(0);
  });
});

// ── getAll (courses) ───────────────────────────────────────────────────────

describe('courseApi.getAll', () => {
  it('fetches all courses from GET /courses', async () => {
    mock.onGet('/courses').reply(200, [sampleCourse]);

    const result = await courseApi.getAll();

    expect(result).toHaveLength(1);
    expect(result[0].title).toBe('Spring Boot Fundamentals');
  });

  it('throws on 401 for unauthenticated request', async () => {
    mock.onGet('/courses').reply(401);

    await expect(courseApi.getAll()).rejects.toThrow();
  });

  it('returns empty array when no courses exist', async () => {
    mock.onGet('/courses').reply(200, []);

    const result = await courseApi.getAll();

    expect(result).toHaveLength(0);
  });
});

// ── getById ────────────────────────────────────────────────────────────────

describe('courseApi.getById', () => {
  it('fetches course by id from GET /courses/:id', async () => {
    mock.onGet('/courses/1').reply(200, sampleCourse);

    const result = await courseApi.getById(1);

    expect(result.id).toBe(1);
    expect(result.title).toBe('Spring Boot Fundamentals');
  });

  it('throws 404 for non-existing course', async () => {
    mock.onGet('/courses/999').reply(404, { message: 'Course not found' });

    await expect(courseApi.getById(999)).rejects.toThrow();
  });
});

// ── enroll ─────────────────────────────────────────────────────────────────

describe('courseApi.enroll', () => {
  it('sends POST /courses/:courseId/enroll/:empId', async () => {
    mock.onPost('/courses/1/enroll/2').reply(200, 'Enrolled successfully');

    const result = await courseApi.enroll(1, 2);

    expect(result).toBe('Enrolled successfully');
    expect(mock.history.post[0].url).toBe('/courses/1/enroll/2');
  });

  it('throws 409 if already enrolled', async () => {
    mock.onPost('/courses/1/enroll/2').reply(409, { message: 'Already enrolled' });

    await expect(courseApi.enroll(1, 2)).rejects.toThrow();
  });
});

// ── submitExamAnswers ──────────────────────────────────────────────────────

describe('courseApi.submitExamAnswers', () => {
  const answers = [0, 1, 2, 3];

  it('posts exam answers to /courses/:courseId/exam/:empId', async () => {
    const examResult = { passed: true, score: 85, certificateNumber: 'CERT-2024-001' };
    mock.onPost('/courses/1/exam/2').reply(200, examResult);

    const result = await courseApi.submitExamAnswers(1, 2, answers);

    expect(result.passed).toBe(true);
    expect(result.score).toBe(85);
    expect(result.certificateNumber).toBe('CERT-2024-001');
  });

  it('throws on 400 bad request (invalid answers)', async () => {
    mock.onPost('/courses/1/exam/2').reply(400, { message: 'Invalid answer format' });

    await expect(courseApi.submitExamAnswers(1, 2, [])).rejects.toThrow();
  });
});

// ── delete course ──────────────────────────────────────────────────────────

describe('courseApi.delete', () => {
  it('sends DELETE /courses/:id', async () => {
    mock.onDelete('/courses/1').reply(200, 'Course deleted');

    const result = await courseApi.delete(1);

    expect(result).toBe('Course deleted');
  });

  it('throws 403 for non-admin users', async () => {
    mock.onDelete('/courses/1').reply(403);

    await expect(courseApi.delete(1)).rejects.toThrow();
  });

  it('throws 404 for non-existing course', async () => {
    mock.onDelete('/courses/999').reply(404);

    await expect(courseApi.delete(999)).rejects.toThrow();
  });
});
