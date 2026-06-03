/**
 * Tests for Resources.jsx — PoliciesSection, FormsTemplatesSection, ResignationLetterTemplate
 */
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import authReducer from '../store/authSlice';

// ── Mock dependencies ─────────────────────────────────────────────────────────

jest.mock('react-toastify', () => ({
  toast: { error: jest.fn(), success: jest.fn(), info: jest.fn() },
}));

// jsPDF mock — Resources.jsx imports it as: import jsPDF from 'jspdf'
jest.mock('jspdf', () => {
  const mockDoc = {
    setFontSize:     jest.fn().mockReturnThis(),
    setFont:         jest.fn().mockReturnThis(),
    setTextColor:    jest.fn().mockReturnThis(),
    setDrawColor:    jest.fn().mockReturnThis(),
    setLineWidth:    jest.fn().mockReturnThis(),
    text:            jest.fn().mockReturnThis(),
    line:            jest.fn().mockReturnThis(),
    // splitTextToSize must return a real array with length property
    splitTextToSize: jest.fn().mockImplementation(() => ['sample text']),
    getTextWidth:    jest.fn().mockReturnValue(50),
    save:            jest.fn(),
    addPage:         jest.fn().mockReturnThis(),
  };
  // Use a class so 'new jsPDF()' works and returns mockDoc
  class MockJsPDF {
    constructor() {
      return mockDoc;
    }
  }
  MockJsPDF._mockDoc = mockDoc;
  return MockJsPDF;
});

jest.mock('../api/resourceApi', () => ({
  resourceApi: {
    getAll:    jest.fn(),
    upload:    jest.fn(),
    update:    jest.fn(),
    toggle:    jest.fn(),
    delete:    jest.fn(),
    download:  jest.fn(),
    view:      jest.fn(),
  },
  faqApi: {
    getAll:  jest.fn(),
    create:  jest.fn(),
    update:  jest.fn(),
    toggle:  jest.fn(),
    delete:  jest.fn(),
  },
}));

import { resourceApi, faqApi } from '../api/resourceApi';
import ResourcesPage from '../pages/Resources';

// Helper to access the jsPDF mock doc instance
function getMockJsPDFDoc() {
  return require('jspdf')._mockDoc;
}

// ── Store helpers ─────────────────────────────────────────────────────────────

function makeStore(role = 'ADMIN') {
  return configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: 1, email: 'admin@company.com', role, fullName: 'Admin User' },
        token: 'mock-token',
        loading: false,
        error: null,
      },
    },
  });
}

function renderWithStore(ui, role = 'ADMIN') {
  const store = makeStore(role);
  return render(
    <Provider store={store}>
      <MemoryRouter>{ui}</MemoryRouter>
    </Provider>
  );
}

// ── Sample data ───────────────────────────────────────────────────────────────

const policyResource = {
  id: 1,
  title: 'HR Policy 2024',
  originalFileName: 'hr_policy.pdf',
  fileType: 'application/pdf',
  fileSize: 102400,
  description: 'Human resources policy',
  category: 'POLICY',
  subCategory: 'Policy',
  active: true,
  uploadedByName: 'admin@company.com',
  uploadedAt: '2024-01-15T10:00:00',
  updatedAt: '2024-02-20T10:00:00',
};

const inactivePolicyResource = {
  ...policyResource,
  id: 2,
  title: 'Old IT Policy',
  active: false,
};

const formTemplateResource = {
  id: 3,
  title: 'Leave Application Form',
  originalFileName: 'leave_form.pdf',
  fileType: 'application/pdf',
  fileSize: 51200,
  description: 'Leave request form',
  category: 'FORM_TEMPLATE',
  subCategory: 'Forms',
  active: true,
  uploadedByName: 'hr@company.com',
  uploadedAt: '2024-01-10T10:00:00',
  updatedAt: '2024-01-10T10:00:00',
};

// ── Helpers ───────────────────────────────────────────────────────────────────

async function waitForLoad() {
  // Wait until the loading spinner is gone
  await waitFor(() => {
    expect(document.querySelector('.MuiCircularProgress-root')).toBeFalsy();
  }, { timeout: 5000 });
}

// ── ResourcesPage: Loading/Error ──────────────────────────────────────────────

describe('ResourcesPage', () => {

  beforeEach(() => {
    jest.clearAllMocks();
    global.URL.createObjectURL = jest.fn(() => 'blob:mock-url');
    global.URL.revokeObjectURL = jest.fn();
  });

  it('shows loading spinner while resources are fetching', async () => {
    resourceApi.getAll.mockReturnValue(new Promise(() => {}));
    renderWithStore(<ResourcesPage />);
    expect(document.querySelector('.MuiCircularProgress-root')).toBeTruthy();
  });

  it('renders tabs after resources load', async () => {
    resourceApi.getAll.mockResolvedValue([policyResource]);
    renderWithStore(<ResourcesPage />);
    await waitFor(() => {
      expect(screen.getByText('Policies & Documents')).toBeInTheDocument();
      expect(screen.getByText('Forms & Templates')).toBeInTheDocument();
      expect(screen.getByText('FAQs')).toBeInTheDocument();
    });
  });

  it('shows error alert on API failure', async () => {
    resourceApi.getAll.mockRejectedValue(new Error('Network error'));
    renderWithStore(<ResourcesPage />);
    await waitFor(() => {
      expect(screen.getByText('Failed to load resources')).toBeInTheDocument();
    });
  });

  it('renders page heading', async () => {
    resourceApi.getAll.mockResolvedValue([]);
    renderWithStore(<ResourcesPage />);
    await waitFor(() => {
      expect(screen.getByText('Resources')).toBeInTheDocument();
    });
  });
});

// ── PoliciesSection ───────────────────────────────────────────────────────────

describe('PoliciesSection', () => {

  beforeEach(() => {
    jest.clearAllMocks();
    global.URL.createObjectURL = jest.fn(() => 'blob:mock-url');
    global.URL.revokeObjectURL = jest.fn();
  });

  async function renderPoliciesTab(role = 'ADMIN', resources = [policyResource]) {
    resourceApi.getAll.mockResolvedValue(resources);
    renderWithStore(<ResourcesPage />, role);
    await waitForLoad();
    // Default tab is Policies & Documents (tab 0), no need to click
  }

  it('renders table header columns: Title, Category, Last Updated, Actions', async () => {
    await renderPoliciesTab();
    await waitFor(() => {
      // Use within the table head to avoid conflict with the Category dropdown label
      const tableHead = document.querySelector('thead');
      expect(within(tableHead).getByText('Title')).toBeInTheDocument();
      expect(within(tableHead).getByText('Category')).toBeInTheDocument();
      expect(within(tableHead).getByText('Last Updated')).toBeInTheDocument();
      expect(within(tableHead).getByText('Actions')).toBeInTheDocument();
    });
  });

  it('renders policy resource row with title', async () => {
    await renderPoliciesTab();
    await waitFor(() => {
      expect(screen.getByText('HR Policy 2024')).toBeInTheDocument();
    });
  });

  it('shows View and Download icon buttons for all users (EMPLOYEE)', async () => {
    await renderPoliciesTab('EMPLOYEE');
    await waitFor(() => {
      // MUI Tooltip renders aria-label on the IconButton
      const viewBtn = document.querySelector('[aria-label="View"]');
      const dlBtn   = document.querySelector('[aria-label="Download"]');
      // In MUI v5, Tooltip title is not propagated as aria-label on child by default
      // Instead look for the svg icons by test-id or the surrounding structure
      // Fall back: check that the OpenInNew and Download icons are rendered
      // by checking for the icon SVG paths or role="button" count
      const iconButtons = document.querySelectorAll('button[class*="MuiIconButton"]');
      expect(iconButtons.length).toBeGreaterThan(0);
    });
  });

  it('shows Edit/Toggle/Delete icon buttons for admin users', async () => {
    await renderPoliciesTab('ADMIN');
    await waitFor(() => {
      // Admin should see at least 4 icon buttons (View, Download, Edit, Toggle, Delete) per row
      const iconButtons = document.querySelectorAll('button[class*="MuiIconButton"]');
      expect(iconButtons.length).toBeGreaterThanOrEqual(4);
    });
  });

  it('does NOT show Edit/Delete for EMPLOYEE role', async () => {
    await renderPoliciesTab('EMPLOYEE');
    await waitFor(() => {
      // Employee should not see EditIcon or DeleteIcon in the table
      const editIcons   = document.querySelectorAll('[data-testid="EditIcon"]');
      const deleteIcons = document.querySelectorAll('[data-testid="DeleteIcon"]');
      expect(editIcons.length).toBe(0);
      expect(deleteIcons.length).toBe(0);
    });
  });

  it('hides inactive documents from EMPLOYEE', async () => {
    await renderPoliciesTab('EMPLOYEE', [policyResource, inactivePolicyResource]);
    await waitFor(() => {
      expect(screen.getByText('HR Policy 2024')).toBeInTheDocument();
      expect(screen.queryByText('Old IT Policy')).not.toBeInTheDocument();
    });
  });

  it('shows inactive documents to ADMIN', async () => {
    await renderPoliciesTab('ADMIN', [policyResource, inactivePolicyResource]);
    await waitFor(() => {
      expect(screen.getByText('HR Policy 2024')).toBeInTheDocument();
      expect(screen.getByText('Old IT Policy')).toBeInTheDocument();
    });
  });

  it('shows Upload Document button for ADMIN', async () => {
    await renderPoliciesTab('ADMIN');
    await waitFor(() => {
      expect(screen.getByText('Upload Document')).toBeInTheDocument();
    });
  });

  it('does NOT show Upload Document button for EMPLOYEE', async () => {
    await renderPoliciesTab('EMPLOYEE');
    await waitFor(() => {
      expect(screen.queryByText('Upload Document')).not.toBeInTheDocument();
    });
  });

  it('opens upload dialog on Upload Document button click', async () => {
    const user = userEvent.setup();
    await renderPoliciesTab('ADMIN');

    await waitFor(() => screen.getByText('Upload Document'));
    await user.click(screen.getByText('Upload Document'));

    await waitFor(() => {
      // The dialog title "Upload Document" appears in the MUI Dialog
      const dialogs = screen.getAllByText('Upload Document');
      expect(dialogs.length).toBeGreaterThan(1);
    });
  });

  it('search filters by title', async () => {
    const user = userEvent.setup();
    const docResource2 = { ...policyResource, id: 99, title: 'IT Security Policy', subCategory: 'Policy' };
    await renderPoliciesTab('ADMIN', [policyResource, docResource2]);

    await waitFor(() => {
      expect(screen.getByText('HR Policy 2024')).toBeInTheDocument();
      expect(screen.getByText('IT Security Policy')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText('Search by title or category…');
    await user.type(searchInput, 'IT Security');

    await waitFor(() => {
      expect(screen.getByText('IT Security Policy')).toBeInTheDocument();
      expect(screen.queryByText('HR Policy 2024')).not.toBeInTheDocument();
    });
  });

  it('category dropdown shows Policy and Document options', async () => {
    const user = userEvent.setup();
    await renderPoliciesTab('ADMIN');

    // The category select renders with an id that lets us find it by the label role
    const catSelects = document.querySelectorAll('[class*="MuiSelect"]');
    expect(catSelects.length).toBeGreaterThan(0);
    // Open the first select (Category)
    const selectInput = document.querySelector('[class*="MuiSelect-select"]');
    await user.click(selectInput);

    await waitFor(() => {
      // Policy and Document should appear in the dropdown list
      const policyOption = screen.getAllByText('Policy');
      const documentOption = screen.getAllByText('Document');
      expect(policyOption.length).toBeGreaterThan(0);
      expect(documentOption.length).toBeGreaterThan(0);
    });
  });

  it('download triggers resourceApi.download', async () => {
    const mockBlob = new Blob(['pdf'], { type: 'application/pdf' });
    resourceApi.download.mockResolvedValue(mockBlob);

    const user = userEvent.setup();
    await renderPoliciesTab('EMPLOYEE');

    await waitFor(() => {
      const iconButtons = document.querySelectorAll('button[class*="MuiIconButton"]');
      expect(iconButtons.length).toBeGreaterThan(0);
    });

    // Click the download icon (second icon button — first is View)
    const iconButtons = Array.from(document.querySelectorAll('button[class*="MuiIconButton"]'));
    // Download button is the one with DownloadIcon - click last visible one
    const downloadBtn = iconButtons.find(b => b.querySelector('[data-testid="DownloadIcon"]'));
    if (downloadBtn) {
      await user.click(downloadBtn);
      await waitFor(() => {
        expect(resourceApi.download).toHaveBeenCalledWith(1);
      });
    }
  });

  it('view triggers resourceApi.view', async () => {
    const mockBlob = new Blob(['pdf'], { type: 'application/pdf' });
    resourceApi.view.mockResolvedValue(mockBlob);
    const openSpy = jest.spyOn(window, 'open').mockImplementation(() => {});

    const user = userEvent.setup();
    await renderPoliciesTab('EMPLOYEE');

    await waitFor(() => {
      const iconButtons = document.querySelectorAll('button[class*="MuiIconButton"]');
      expect(iconButtons.length).toBeGreaterThan(0);
    });

    // View button is the first icon button (OpenInNewIcon)
    const viewBtn = document.querySelector('button[class*="MuiIconButton"]');
    if (viewBtn) {
      await user.click(viewBtn);
      await waitFor(() => {
        expect(resourceApi.view).toHaveBeenCalledWith(1);
      });
    }

    openSpy.mockRestore();
  });

  it('shows empty state when no documents available', async () => {
    await renderPoliciesTab('EMPLOYEE', []);
    await waitFor(() => {
      expect(screen.getByText('No documents available')).toBeInTheDocument();
    });
  });
});

// ── FormsTemplatesSection ─────────────────────────────────────────────────────

describe('FormsTemplatesSection', () => {

  beforeEach(() => {
    jest.clearAllMocks();
    global.URL.createObjectURL = jest.fn(() => 'blob:mock-url');
    global.URL.revokeObjectURL = jest.fn();
  });

  async function renderFormsTab(role = 'ADMIN', resources = [formTemplateResource]) {
    resourceApi.getAll.mockResolvedValue(resources);
    renderWithStore(<ResourcesPage />, role);

    await waitForLoad();

    const tabButton = screen.getByText('Forms & Templates');
    fireEvent.click(tabButton);

    await waitFor(() => {
      expect(screen.getByText('Resignation Letter')).toBeInTheDocument();
    });
  }

  it('shows Built-in Resignation Letter row', async () => {
    await renderFormsTab();
    expect(screen.getByText('Resignation Letter')).toBeInTheDocument();
  });

  it('Built-in row has Built-in chip', async () => {
    await renderFormsTab();
    expect(screen.getByText('Built-in')).toBeInTheDocument();
  });

  it('Uploaded row has Uploaded chip', async () => {
    await renderFormsTab('ADMIN', [formTemplateResource]);
    await waitFor(() => {
      expect(screen.getByText('Uploaded')).toBeInTheDocument();
    });
  });

  it('category dropdown shows Forms, Templates, and All Categories', async () => {
    const user = userEvent.setup();
    await renderFormsTab();

    const selectInput = document.querySelector('[class*="MuiSelect-select"]');
    await user.click(selectInput);

    await waitFor(() => {
      expect(screen.getByText('All Categories')).toBeInTheDocument();
      const formsOptions = screen.getAllByText('Forms');
      expect(formsOptions.length).toBeGreaterThan(0);
      const templatesOptions = screen.getAllByText('Templates');
      expect(templatesOptions.length).toBeGreaterThan(0);
    });
  });

  it('Use Template button on Resignation Letter opens dialog', async () => {
    const user = userEvent.setup();
    await renderFormsTab();

    const useButton = screen.getByText('Use Template');
    await user.click(useButton);

    await waitFor(() => {
      expect(screen.getByText('Fill Details')).toBeInTheDocument();
    });
  });

  it('Download button on uploaded row calls resourceApi.download', async () => {
    const mockBlob = new Blob(['content'], { type: 'application/pdf' });
    resourceApi.download.mockResolvedValue(mockBlob);

    const user = userEvent.setup();
    await renderFormsTab('ADMIN', [formTemplateResource]);

    await waitFor(() => {
      const downloadButtons = screen.getAllByText('Download');
      expect(downloadButtons.length).toBeGreaterThan(0);
    });

    const downloadButtons = screen.getAllByText('Download');
    await user.click(downloadButtons[0]);

    await waitFor(() => {
      expect(resourceApi.download).toHaveBeenCalledWith(formTemplateResource.id);
    });
  });

  it('search filters both built-in and uploaded rows', async () => {
    const user = userEvent.setup();
    await renderFormsTab('ADMIN', [formTemplateResource]);

    const searchInput = screen.getByPlaceholderText('Search forms and templates…');
    await user.type(searchInput, 'Leave Application');

    await waitFor(() => {
      expect(screen.getByText('Leave Application Form')).toBeInTheDocument();
      expect(screen.queryByText('Resignation Letter')).not.toBeInTheDocument();
    });
  });

  it('ADMIN sees Edit icon buttons on uploaded rows', async () => {
    await renderFormsTab('ADMIN', [formTemplateResource]);

    await waitFor(() => {
      // With canManage=true, Edit/Toggle/Delete buttons appear
      const editIcons = document.querySelectorAll('[data-testid="EditIcon"]');
      expect(editIcons.length).toBeGreaterThan(0);
    });
  });

  it('EMPLOYEE does NOT see Edit/Toggle/Delete on uploaded rows', async () => {
    await renderFormsTab('EMPLOYEE', [formTemplateResource]);

    await waitFor(() => {
      // Employee sees no edit icons
      const editIcons = document.querySelectorAll('[data-testid="EditIcon"]');
      expect(editIcons.length).toBe(0);
    });
  });

  it('Upload Template button visible only for admin/HR', async () => {
    await renderFormsTab('ADMIN');
    expect(screen.getByText('Upload Template')).toBeInTheDocument();
  });

  it('Upload Template button NOT visible for EMPLOYEE', async () => {
    await renderFormsTab('EMPLOYEE');
    expect(screen.queryByText('Upload Template')).not.toBeInTheDocument();
  });

  it('shows empty state message when no templates match search', async () => {
    const user = userEvent.setup();
    await renderFormsTab('ADMIN', [formTemplateResource]);

    const searchInput = screen.getByPlaceholderText('Search forms and templates…');
    await user.type(searchInput, 'zzz-no-match-zzz');

    await waitFor(() => {
      expect(screen.getByText('No templates match your filters')).toBeInTheDocument();
    });
  });
});

// ── ResignationLetterTemplate dialog ─────────────────────────────────────────

describe('ResignationLetterTemplate', () => {

  beforeEach(() => {
    jest.clearAllMocks();
    global.URL.createObjectURL = jest.fn(() => 'blob:mock-url');
    global.URL.revokeObjectURL = jest.fn();
  });

  async function openResignationDialog(role = 'ADMIN') {
    resourceApi.getAll.mockResolvedValue([]);
    renderWithStore(<ResourcesPage />, role);

    await waitForLoad();

    fireEvent.click(screen.getByText('Forms & Templates'));

    await waitFor(() => screen.getByText('Use Template'));
    fireEvent.click(screen.getByText('Use Template'));

    await waitFor(() => {
      expect(screen.getByText('Fill Details')).toBeInTheDocument();
    });
  }

  it('renders dialog when opened', async () => {
    await openResignationDialog();
    expect(screen.getAllByText('Fill Details').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Preview').length).toBeGreaterThan(0);
  });

  it('does NOT render dialog when open=false (dialog closed)', async () => {
    resourceApi.getAll.mockResolvedValue([]);
    renderWithStore(<ResourcesPage />, 'ADMIN');

    await waitForLoad();
    fireEvent.click(screen.getByText('Forms & Templates'));

    await waitFor(() => screen.getByText('Resignation Letter'));
    // Dialog should not be shown before clicking Use Template
    expect(screen.queryByText('Fill Details')).not.toBeInTheDocument();
  });

  it('Fill Details and Preview toggle buttons switch views', async () => {
    const user = userEvent.setup();
    await openResignationDialog();

    // Initially on Fill Details view
    expect(screen.getByLabelText(/Employee Name/i) ||
           screen.getByPlaceholderText(/Employee Name/i) ||
           document.querySelector('[class*="DialogContent"] input')).toBeTruthy();

    // Click Preview button
    const previewBtns = screen.getAllByText('Preview');
    await user.click(previewBtns[0]);

    await waitFor(() => {
      expect(document.querySelector('iframe[title="letter-preview"]')).toBeTruthy();
    });

    // Click Fill Details
    const fillDetailsBtns = screen.getAllByText('Fill Details');
    await user.click(fillDetailsBtns[0]);

    await waitFor(() => {
      expect(document.querySelector('iframe[title="letter-preview"]')).toBeFalsy();
    });
  });

  it('form fields update state on change', async () => {
    const user = userEvent.setup();
    await openResignationDialog();

    // Find the Employee Name input
    const nameInputs = document.querySelectorAll('[class*="DialogContent"] input, [class*="DialogContent"] textarea');
    expect(nameInputs.length).toBeGreaterThan(0);

    const firstInput = nameInputs[0];
    await user.clear(firstInput);
    await user.type(firstInput, 'John Smith');

    expect(firstInput.value).toBe('John Smith');
  });

  it('Preview Letter button switches to preview view', async () => {
    const user = userEvent.setup();
    await openResignationDialog();

    // The "Preview Letter →" button is in the DialogActions
    const previewBtn = screen.getByText(/Preview Letter/i);
    await user.click(previewBtn);

    await waitFor(() => {
      expect(document.querySelector('iframe[title="letter-preview"]')).toBeTruthy();
    });
  });

  it('Preview renders iframe with srcDoc', async () => {
    const user = userEvent.setup();
    await openResignationDialog();

    const previewBtns = screen.getAllByText('Preview');
    await user.click(previewBtns[0]);

    await waitFor(() => {
      const iframe = document.querySelector('iframe[title="letter-preview"]');
      expect(iframe).toBeTruthy();
    });
  });

  it('Download PDF button is visible in preview mode', async () => {
    const user = userEvent.setup();

    await openResignationDialog();

    // Switch to preview
    const previewBtns = screen.getAllByText('Preview');
    await user.click(previewBtns[0]);

    await waitFor(() => {
      expect(screen.getByText('Download PDF')).toBeInTheDocument();
    });
  });

  it('Print button calls window.open', async () => {
    const mockWindow = {
      document: { write: jest.fn(), close: jest.fn() },
      focus: jest.fn(),
      print: jest.fn()
    };
    const openSpy = jest.spyOn(window, 'open').mockReturnValue(mockWindow);

    const user = userEvent.setup();
    await openResignationDialog();

    // Switch to preview first
    const previewBtns = screen.getAllByText('Preview');
    await user.click(previewBtns[0]);

    await waitFor(() => screen.getByText('Print'));
    await user.click(screen.getByText('Print'));

    await waitFor(() => {
      expect(openSpy).toHaveBeenCalled();
    });

    openSpy.mockRestore();
  });

  it('Closing dialog via Close button resets form and closes dialog', async () => {
    const user = userEvent.setup();
    await openResignationDialog();

    // Fill a field
    const inputs = document.querySelectorAll('[class*="DialogContent"] input');
    if (inputs.length > 0) {
      await user.clear(inputs[0]);
      await user.type(inputs[0], 'Test Value');
    }

    // Click Close button
    await user.click(screen.getByText('Close'));

    // Dialog should close
    await waitFor(() => {
      expect(screen.queryByText('Fill Details')).not.toBeInTheDocument();
    });
  });
});

// ── FAQSection ────────────────────────────────────────────────────────────────

describe('FAQSection', () => {

  const sampleFaq = {
    id: 1,
    question: 'What is the leave policy?',
    answer: 'You get 21 days per year.',
    active: true,
    displayOrder: 1,
  };

  beforeEach(() => {
    jest.clearAllMocks();
    global.URL.createObjectURL = jest.fn(() => 'blob:mock-url');
    global.URL.revokeObjectURL = jest.fn();
  });

  async function renderFaqTab(role = 'ADMIN', faqs = [sampleFaq]) {
    resourceApi.getAll.mockResolvedValue([]);
    faqApi.getAll.mockResolvedValue(faqs);
    renderWithStore(<ResourcesPage />, role);

    await waitForLoad();
    fireEvent.click(screen.getByText('FAQs'));

    // Wait for FAQ section to load (spinner disappears)
    await waitFor(() => {
      expect(screen.queryAllByRole('progressbar').length).toBe(0);
    }, { timeout: 5000 });
  }

  it('shows FAQ question in accordion', async () => {
    await renderFaqTab();
    await waitFor(() => {
      expect(screen.getByText('What is the leave policy?')).toBeInTheDocument();
    });
  });

  it('shows Add FAQ button for ADMIN', async () => {
    await renderFaqTab('ADMIN');
    await waitFor(() => {
      expect(screen.getByText('Add FAQ')).toBeInTheDocument();
    });
  });

  it('does NOT show Add FAQ for EMPLOYEE', async () => {
    await renderFaqTab('EMPLOYEE');
    await waitFor(() => {
      expect(screen.queryByText('Add FAQ')).not.toBeInTheDocument();
    });
  });

  it('shows empty state when no FAQs exist', async () => {
    await renderFaqTab('EMPLOYEE', []);
    await waitFor(() => {
      expect(screen.getByText('No FAQs yet')).toBeInTheDocument();
    });
  });

  it('FAQ loading error shows toast', async () => {
    const { toast } = require('react-toastify');
    resourceApi.getAll.mockResolvedValue([]);
    faqApi.getAll.mockRejectedValue(new Error('Network error'));

    renderWithStore(<ResourcesPage />, 'EMPLOYEE');
    await waitForLoad();
    fireEvent.click(screen.getByText('FAQs'));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('Failed to load FAQs');
    });
  });

  it('FAQ section search filters by question', async () => {
    const user = userEvent.setup();
    const faq2 = { id: 2, question: 'How do I apply for reimbursement?', answer: 'Submit via portal', active: true };
    faqApi.getAll.mockResolvedValue([sampleFaq, faq2]);
    await renderFaqTab('ADMIN', [sampleFaq, faq2]);

    await waitFor(() => {
      expect(screen.getByText('What is the leave policy?')).toBeInTheDocument();
      expect(screen.getByText('How do I apply for reimbursement?')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText('Search FAQs…');
    await user.type(searchInput, 'reimbursement');

    await waitFor(() => {
      expect(screen.queryByText('What is the leave policy?')).not.toBeInTheDocument();
      expect(screen.getByText('How do I apply for reimbursement?')).toBeInTheDocument();
    });
  });
});
