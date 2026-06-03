import MockAdapter from 'axios-mock-adapter';
import api from '../api/axios';
import { resourceApi, faqApi } from '../api/resourceApi';

const mock = new MockAdapter(api);

afterEach(() => {
  mock.reset();
  localStorage.clear();
});

// ── resourceApi ───────────────────────────────────────────────────────────────

describe('resourceApi', () => {

  const sampleResource = {
    id: 1,
    title: 'HR Policy',
    originalFileName: 'hr_policy.pdf',
    fileType: 'application/pdf',
    fileSize: 2048,
    category: 'POLICY',
    subCategory: 'Policy',
    active: true,
    uploadedByName: 'admin@company.com',
  };

  describe('getAll', () => {
    it('sends GET /resources and returns list', async () => {
      mock.onGet('/resources').reply(200, [sampleResource]);

      const result = await resourceApi.getAll();
      expect(result).toEqual([sampleResource]);
    });

    it('throws on server error', async () => {
      mock.onGet('/resources').reply(500, { message: 'Server error' });
      await expect(resourceApi.getAll()).rejects.toThrow();
    });
  });

  describe('upload', () => {
    it('sends POST with FormData containing all fields', async () => {
      mock.onPost('/resources/upload').reply(200, sampleResource);

      const file = new File(['content'], 'policy.pdf', { type: 'application/pdf' });
      const result = await resourceApi.upload(file, 'HR Policy', 'POLICY', 'Policy', 'HR doc');

      expect(mock.history.post.length).toBe(1);
      const formData = mock.history.post[0].data;
      expect(formData.get('file')).toEqual(file);
      expect(formData.get('title')).toBe('HR Policy');
      expect(formData.get('category')).toBe('POLICY');
      expect(formData.get('subCategory')).toBe('Policy');
      expect(formData.get('description')).toBe('HR doc');
      expect(result).toEqual(sampleResource);
    });

    it('omits title when null', async () => {
      mock.onPost('/resources/upload').reply(200, sampleResource);

      const file = new File(['c'], 'doc.pdf', { type: 'application/pdf' });
      await resourceApi.upload(file, null, 'POLICY', null, null);

      const formData = mock.history.post[0].data;
      expect(formData.get('title')).toBeNull();
      expect(formData.get('subCategory')).toBeNull();
      expect(formData.get('description')).toBeNull();
    });

    it('omits optional fields when undefined', async () => {
      mock.onPost('/resources/upload').reply(200, sampleResource);

      const file = new File(['c'], 'doc.pdf', { type: 'application/pdf' });
      await resourceApi.upload(file, undefined, undefined, undefined, undefined);

      const formData = mock.history.post[0].data;
      expect(formData.get('title')).toBeNull();
      expect(formData.get('category')).toBeNull();
    });

    it('always appends file', async () => {
      mock.onPost('/resources/upload').reply(200, sampleResource);

      const file = new File(['data'], 'file.pdf', { type: 'application/pdf' });
      await resourceApi.upload(file, 'T', 'POLICY', null, null);

      const formData = mock.history.post[0].data;
      expect(formData.get('file')).toEqual(file);
    });

    it('throws 403 when user lacks permission', async () => {
      mock.onPost('/resources/upload').reply(403, { message: 'Access denied' });

      const file = new File(['c'], 'f.pdf', { type: 'application/pdf' });
      await expect(resourceApi.upload(file, 'T', 'POLICY', null, null)).rejects.toThrow();
    });
  });

  describe('view', () => {
    it('sends GET with responseType blob', async () => {
      const blobData = new Blob(['pdf content'], { type: 'application/pdf' });
      mock.onGet('/resources/view/1').reply(200, blobData);

      await resourceApi.view(1);

      expect(mock.history.get[0].responseType).toBe('blob');
      expect(mock.history.get[0].url).toBe('/resources/view/1');
    });

    it('throws 404 for missing resource', async () => {
      mock.onGet('/resources/view/99').reply(404, { message: 'Not found' });
      await expect(resourceApi.view(99)).rejects.toThrow();
    });
  });

  describe('download', () => {
    it('sends GET with responseType blob', async () => {
      const blobData = new Blob(['pdf content'], { type: 'application/pdf' });
      mock.onGet('/resources/download/1').reply(200, blobData);

      await resourceApi.download(1);

      expect(mock.history.get[0].responseType).toBe('blob');
      expect(mock.history.get[0].url).toBe('/resources/download/1');
    });

    it('throws 404 for missing resource', async () => {
      mock.onGet('/resources/download/99').reply(404, { message: 'Not found' });
      await expect(resourceApi.download(99)).rejects.toThrow();
    });
  });

  describe('update', () => {
    it('sends PATCH with data', async () => {
      mock.onPatch('/resources/1').reply(200, { ...sampleResource, title: 'Updated' });

      const result = await resourceApi.update(1, { title: 'Updated' });

      expect(result.title).toBe('Updated');
      expect(mock.history.patch[0].url).toBe('/resources/1');
    });
  });

  describe('toggle', () => {
    it('sends PATCH to toggle endpoint', async () => {
      mock.onPatch('/resources/1/toggle').reply(200, { ...sampleResource, active: false });

      const result = await resourceApi.toggle(1);

      expect(result.active).toBe(false);
      expect(mock.history.patch[0].url).toBe('/resources/1/toggle');
    });
  });

  describe('delete', () => {
    it('sends DELETE request', async () => {
      mock.onDelete('/resources/1').reply(204);

      await resourceApi.delete(1);

      expect(mock.history.delete[0].url).toBe('/resources/1');
    });

    it('throws on 404', async () => {
      mock.onDelete('/resources/99').reply(404);
      await expect(resourceApi.delete(99)).rejects.toThrow();
    });
  });
});

// ── faqApi ────────────────────────────────────────────────────────────────────

describe('faqApi', () => {

  const sampleFaq = {
    id: 1,
    question: 'What is the leave policy?',
    answer: 'Employees get 21 days.',
    active: true,
    displayOrder: 1,
  };

  describe('getAll', () => {
    it('sends GET /faqs with all=false by default', async () => {
      mock.onGet('/faqs', { params: { all: false } }).reply(200, [sampleFaq]);

      const result = await faqApi.getAll();
      expect(result).toEqual([sampleFaq]);
    });

    it('sends GET /faqs with all=true', async () => {
      mock.onGet('/faqs', { params: { all: true } }).reply(200, [sampleFaq]);

      const result = await faqApi.getAll(true);
      expect(result).toEqual([sampleFaq]);
    });
  });

  describe('create', () => {
    it('sends POST /faqs', async () => {
      mock.onPost('/faqs').reply(200, sampleFaq);

      const result = await faqApi.create({ question: 'Q', answer: 'A' });
      expect(result).toEqual(sampleFaq);
    });

    it('throws 403 for non-admin/HR', async () => {
      mock.onPost('/faqs').reply(403, { message: 'Forbidden' });
      await expect(faqApi.create({ question: 'Q', answer: 'A' })).rejects.toThrow();
    });
  });

  describe('update', () => {
    it('sends PUT /faqs/{id}', async () => {
      mock.onPut('/faqs/1').reply(200, sampleFaq);

      const result = await faqApi.update(1, { question: 'Updated Q' });
      expect(result).toEqual(sampleFaq);
    });
  });

  describe('toggle', () => {
    it('sends PATCH /faqs/{id}/toggle', async () => {
      mock.onPatch('/faqs/1/toggle').reply(200, { ...sampleFaq, active: false });

      const result = await faqApi.toggle(1);
      expect(result.active).toBe(false);
    });
  });

  describe('delete', () => {
    it('sends DELETE /faqs/{id}', async () => {
      mock.onDelete('/faqs/1').reply(204);

      await faqApi.delete(1);
      expect(mock.history.delete[0].url).toBe('/faqs/1');
    });
  });
});
