import React, { useEffect, useState, useMemo, useRef } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, Typography, Button, Chip, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, CircularProgress, IconButton,
  Tooltip, Tabs, Tab, Table, TableHead, TableBody, TableRow, TableCell,
  TableContainer, TablePagination, Stack, InputAdornment, MenuItem, Grid,
  Rating, Avatar, LinearProgress, Divider, Alert, Stepper, Step, StepLabel,
  Paper, FormControl, InputLabel, Select, Popover
} from '@mui/material';
import CloudUploadIcon    from '@mui/icons-material/CloudUpload';
import WarningAmberIcon   from '@mui/icons-material/WarningAmber';
import TuneIcon           from '@mui/icons-material/Tune';
import SearchIcon         from '@mui/icons-material/Search';
import ArrowBackIcon      from '@mui/icons-material/ArrowBack';
import CheckCircleIcon    from '@mui/icons-material/CheckCircle';
import CancelIcon         from '@mui/icons-material/Cancel';
import EditIcon           from '@mui/icons-material/Edit';
import DeleteIcon         from '@mui/icons-material/Delete';
import OpenInNewIcon      from '@mui/icons-material/OpenInNew';
import RefreshIcon        from '@mui/icons-material/Refresh';
import DescriptionIcon    from '@mui/icons-material/Description';
import PersonAddIcon      from '@mui/icons-material/PersonAdd';
import WorkIcon           from '@mui/icons-material/Work';
import AssignmentIcon     from '@mui/icons-material/Assignment';
import HowToRegIcon       from '@mui/icons-material/HowToReg';
import EmojiEventsIcon    from '@mui/icons-material/EmojiEvents';
import GroupIcon          from '@mui/icons-material/Group';
import AppDateTimePicker  from '../components/AppDateTimePicker';
import { interviewApi }   from '../api/interviewApi';
import { employeeApi }    from '../api/employeeApi';
import { toast }          from 'react-toastify';

/* ── Constants ─────────────────────────────────────────────────────────────── */
const OFFICE_LOCATIONS  = ['Mandsaur', 'Jamnagar', 'Ahmedabad', 'WFH'];
const CANDIDATE_SOURCES = ['LinkedIn', 'Naukri', 'Referral', 'Company Website', 'Walk-in', 'Consultant', 'Whatsapp', 'Outlook', 'Other'];
const DIFF_OPTIONS = ['EASY', 'MEDIUM', 'HARD', 'MIXED'];
const APPLIED_PROFILES  = ['Accounts', 'Book Keeper', 'Personal Tax', 'Corporate Tax', 'Payroll'];
const STAGE_STEPS       = ['CV Bank', 'HR Screening', 'Technical Interview', 'Final Round'];

const STATUS_CFG = {
  NEW:                 { label: 'New',                  bg: '#f1f5f9', color: '#475569' },
  UNDER_HR_REVIEW:     { label: 'Under HR Review',      bg: '#dbeafe', color: '#1d4ed8' },
  HR_REJECTED:         { label: 'HR Rejected',          bg: '#fee2e2', color: '#dc2626' },
  TECHNICAL_PENDING:   { label: 'Technical Pending',    bg: '#fef3c7', color: '#d97706' },
  TECHNICAL_REJECTED:  { label: 'Technical Rejected',   bg: '#fee2e2', color: '#dc2626' },
  FINAL_ROUND_PENDING: { label: 'Final Round Pending',  bg: '#f3e8ff', color: '#7c3aed' },
  SELECTED:            { label: 'Selected',             bg: '#dcfce7', color: '#16a34a' },
  REJECTED:            { label: 'Rejected',             bg: '#fee2e2', color: '#dc2626' },
};

const statusToStep = (s) => {
  if (!s || s === 'NEW')                                        return 0;
  if (s === 'UNDER_HR_REVIEW' || s === 'HR_REJECTED')          return 1;
  if (s === 'TECHNICAL_PENDING' || s === 'TECHNICAL_REJECTED') return 2;
  return 3;
};

const EMPTY_UPLOAD = {
  name: '', email: '', phone: '',
  address: '', addressStreet: '', addressArea: '', addressLandmark: '',
  addressCity: '', addressDistrict: '', addressState: '', addressPostalCode: '', addressCountry: '',
  appliedProfile: '', officeLocation: '', source: '',
  skills: '', totalExperienceYears: '', linkedinUrl: '', githubUrl: '',
};
const EMPTY_HR     = { currentRoleResponsibilities: '', reasonForChange: '', currentCtc: '', expectedCtc: '', noticePeriod: '', preferredLocation: '', workBase: '', totalExperience: '', currentCompany: '', screeningDate: '', communicationSkills: '', hrComments: '', rejectionReason: '', relevantExperience: '', availability: '' };
const EMPTY_TECH   = { technicalSkillsRating: 3, communicationRating: 3, problemSolvingRating: 3, codingAbilityRating: 3, architectureKnowledgeRating: 3, comments: '', decision: '' };
const EMPTY_FINAL  = { finalInterviewDate: '', finalRemarks: '', salaryRecommendation: '', directorRecommendation: 'PENDING' };
const EMPTY_HR_DECISION = { offeredCtc: '', joiningDate: '' };
const RECOMMENDATION_OPTIONS = ['PENDING', 'APPROVE', 'HOLD', 'REJECT'];

const fmtDate = (v) => v ? new Date(v).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
const fmtDt   = (v) => v ? new Date(v).toLocaleString('en-IN',  { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

const timeAgo = (v) => {
  if (!v) return '';
  const days   = Math.floor((Date.now() - new Date(v).getTime()) / 86400000);
  const months = Math.floor(days / 30);
  if (months >= 1) return `${months} month${months > 1 ? 's' : ''} ago`;
  if (days   >= 1) return `${days} day${days > 1 ? 's' : ''} ago`;
  return 'today';
};

const StatusChip = ({ status }) => {
  const c = STATUS_CFG[status] || { label: status, bg: '#f1f5f9', color: '#475569' };
  return <Chip label={c.label} size="small" sx={{ bgcolor: c.bg, color: c.color, fontWeight: 700, fontSize: 11 }} />;
};

const SectionTitle = ({ children }) => (
  <Typography variant="subtitle2" fontWeight={700} color="text.secondary"
    sx={{ textTransform: 'uppercase', letterSpacing: 0.6, fontSize: 11, mb: 1.5 }}>
    {children}
  </Typography>
);

const SKILL_COLORS = [
  { bg: '#dbeafe', color: '#1e40af' }, { bg: '#dcfce7', color: '#15803d' },
  { bg: '#fef9c3', color: '#854d0e' }, { bg: '#f3e8ff', color: '#6b21a8' },
  { bg: '#ffedd5', color: '#9a3412' }, { bg: '#e0f2fe', color: '#0369a1' },
];

const SkillChips = ({ skills, max = 40 }) => {
  if (!skills) return null;
  const list = skills.split(',').map(s => s.trim()).filter(Boolean).slice(0, max);
  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
      {list.map((s, i) => {
        const clr = SKILL_COLORS[i % SKILL_COLORS.length];
        return (
          <Chip key={s} label={s} size="small"
            sx={{ bgcolor: clr.bg, color: clr.color, fontWeight: 600, fontSize: 11, height: 24, borderRadius: 1.5 }} />
        );
      })}
    </Box>
  );
};

/* ═══════════════════════════════════════════════════════════════════════════ */
const ATSPage = () => {
  const { user } = useSelector((s) => s.auth);
  const isAdmin   = ['ADMIN', 'DIRECTOR'].includes(user?.role);
  const isHR      = user?.role === 'HR';
  const isManager = ['MANAGER', 'ASSISTANT_MANAGER'].includes(user?.role);
  const canManage = isAdmin || isHR;

  /* ── Main state ────────────────────────────────────────────────────────── */
  const [tab,           setTab]          = useState(isManager && !canManage ? 'technical' : 'cvbank');
  const [candidates,    setCandidates]   = useState([]);
  const [myAssignments, setMyAssignments]= useState([]);
  const [stats,         setStats]        = useState(null);
  const [allEmployees,  setAllEmployees] = useState([]);
  const [directors,     setDirectors]    = useState([]);
  const [loading,       setLoading]      = useState(true);

  /* ── Detail view ───────────────────────────────────────────────────────── */
  const [selectedCand,  setSelectedCand]  = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [stageTab,      setStageTab]      = useState(0);

  /* ── Profile dropdown ─────────────────────────────────────────────────── */
  const [extraProfiles,    setExtraProfiles]    = useState([]);
  const [profileAnchor,    setProfileAnchor]    = useState(null);
  const [newProfileInput,  setNewProfileInput]  = useState('');
  const allProfiles = [...APPLIED_PROFILES, ...extraProfiles];

  /* ── Location dropdown ─────────────────────────────────────────────────── */
  const [extraLocations,   setExtraLocations]   = useState([]);
  const [locationAnchor,   setLocationAnchor]   = useState(null);
  const [newLocationInput, setNewLocationInput] = useState('');
  const allLocations = [...OFFICE_LOCATIONS, ...extraLocations];

  /* ── Source dropdown ───────────────────────────────────────────────────── */
  const [extraSources,    setExtraSources]    = useState([]);
  const [sourceAnchor,    setSourceAnchor]    = useState(null);
  const [newSourceInput,  setNewSourceInput]  = useState('');
  const allSources = [...CANDIDATE_SOURCES, ...extraSources];

  /* ── Upload dialog ─────────────────────────────────────────────────────── */
  const [uploadDialog,   setUploadDialog]  = useState(false);
  const [uploadFile,     setUploadFile]    = useState(null);
  const [uploadStoring,  setUploadStoring] = useState(false);   // Step 1: storing file
  const [storedResume,   setStoredResume]  = useState(null);    // { resumePath, resumeOriginalName, ...parsed }
  const [uploadForm,     setUploadForm]    = useState(EMPTY_UPLOAD);
  const [uploadSaving,   setUploadSaving]  = useState(false);   // Step 2: saving candidate
  const fileInputRef = useRef(null);

  /* ── Duplicate warning dialog ──────────────────────────────────────────── */
  const [dupDialog,    setDupDialog]    = useState(false);
  const [dupCandidate, setDupCandidate] = useState(null);
  const [dupReplacing, setDupReplacing] = useState(false);

  /* ── HR Screening inline form ──────────────────────────────────────────── */
  const [hrForm,   setHrForm]   = useState(EMPTY_HR);
  const [hrSaving, setHrSaving] = useState(false);

  /* ── HR Screening spreadsheet table view ───────────────────────────────── */
  const [hrTableForms,    setHrTableForms]    = useState({});
  const [hrTableSavingId, setHrTableSavingId] = useState(null); // eslint-disable-line no-unused-vars

  /* ── Assign Technical Dialog ───────────────────────────────────────────── */
  const [assignDialog, setAssignDialog] = useState(false);
  const [assignForm,   setAssignForm]   = useState({ interviewerId: '', scheduledAt: '' });
  const [assigning,    setAssigning]    = useState(false);

  /* ── Technical Feedback Dialog ─────────────────────────────────────────── */
  const [techDialog, setTechDialog] = useState(false);
  const [selTech,    setSelTech]    = useState(null);
  const [techForm,   setTechForm]   = useState(EMPTY_TECH);
  const [techSaving, setTechSaving] = useState(false);

  /* ── Generate Interview Link Dialog ─────────────────────────────────────── */
  const [linkDialog,      setLinkDialog]      = useState(false);
  const [linkTechId,      setLinkTechId]      = useState(null);
  const [linkForm,        setLinkForm]        = useState({ technology: '', difficulty: 'MIXED', questionCount: 20 });
  const [linkGenerating,  setLinkGenerating]  = useState(false);
  const [generatedLink,   setGeneratedLink]   = useState('');

  /* ── Final Round: Director's interview notes ─────────────────────────────── */
  const [finalForm,        setFinalForm]        = useState(EMPTY_FINAL);
  const [finalSaving,      setFinalSaving]      = useState(false);

  /* ── Final Round: HR's hiring decision ───────────────────────────────────── */
  const [hrDecisionForm,   setHrDecisionForm]   = useState(EMPTY_HR_DECISION);
  const [hrDeciding,       setHrDeciding]       = useState(false);

  /* ── Assign Director dialog (HR/Admin) ───────────────────────────────────── */
  const [assignDirectorDialog,  setAssignDirectorDialog]  = useState(false);
  const [assignDirectorCandId,  setAssignDirectorCandId]  = useState(null);
  const [assignDirectorForm,    setAssignDirectorForm]    = useState({ directorId: '' });
  const [assigningDirector,     setAssigningDirector]     = useState(false);

  /* ── Schedule & Generate Final Interview Link dialog (Director) ─────────── */
  const [finalLinkDialog,  setFinalLinkDialog]  = useState(false);
  const [finalLinkCandId,  setFinalLinkCandId]  = useState(null);
  const [finalLinkForm,    setFinalLinkForm]     = useState({ scheduledAt: '' });
  const [finalLinkGenerating, setFinalLinkGenerating] = useState(false);

  /* ── Edit Candidate ────────────────────────────────────────────────────── */
  const [editDialog, setEditDialog] = useState(false);
  const [editForm,   setEditForm]   = useState({});
  const [editSaving, setEditSaving] = useState(false);

  /* ── List filters ──────────────────────────────────────────────────────── */
  const [search,         setSearch]         = useState('');
  const [statusFilter,   setStatusFilter]   = useState('ALL');
  const [locationFilter, setLocationFilter] = useState('ALL');
  const [profileFilter,  setProfileFilter]  = useState('ALL');
  const [page,           setPage]           = useState(0);
  const RPP = 10;
  const [hrPage,         setHrPage]         = useState(0);
  const HR_RPP = 5;

  /* ── Init ──────────────────────────────────────────────────────────────── */
  useEffect(() => { loadAll(); }, [canManage]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadAll = async () => {
    setLoading(true);
    try {
      const promises = [
        interviewApi.getMyAssignments().then(a => setMyAssignments(Array.isArray(a) ? a : [])).catch(() => {}),
      ];
      if (canManage) {
        promises.push(
          interviewApi.getAllCandidates().then(c => setCandidates(Array.isArray(c) ? c : [])),
          interviewApi.getStats().then(s => setStats(s)),
          employeeApi.getAll().then(e => setAllEmployees(Array.isArray(e) ? e : [])),
          interviewApi.getDirectors().then(d => setDirectors(Array.isArray(d) ? d : [])).catch(() => {}),
        );
      }
      await Promise.all(promises);
    } catch (err) {
      toast.error(err?.response?.status === 404
        ? 'Interview module not available — please restart the backend'
        : 'Failed to load data');
    } finally { setLoading(false); }
  };

  const refreshCandidates = async () => {
    try {
      const [c, s] = await Promise.all([interviewApi.getAllCandidates(), interviewApi.getStats()]);
      setCandidates(Array.isArray(c) ? c : []);
      setStats(s);
    } catch { toast.error('Refresh failed'); }
  };

  const refreshDetail = async (id) => {
    setDetailLoading(true);
    try {
      const c = await interviewApi.getCandidate(id);
      setSelectedCand(c);
      populateForms(c);
    } catch { toast.error('Failed to refresh'); }
    finally { setDetailLoading(false); }
  };

  const populateForms = (c) => {
    const s = c.hrScreening;
    setHrForm(s ? {
      currentRoleResponsibilities: s.currentRoleResponsibilities || '',
      reasonForChange:             s.reasonForChange             || '',
      currentCtc:                  s.currentCtc                  || '',
      expectedCtc:                 s.expectedCtc                 || '',
      noticePeriod:                s.noticePeriod                || '',
      preferredLocation:           s.preferredLocation           || '',
      workBase:                    s.workBase                    || '',
      totalExperience:             s.totalExperience             || '',
      currentCompany:              s.currentCompany              || '',
      screeningDate:               s.screeningDate               || '',
      communicationSkills:         s.communicationSkills         || '',
      hrComments:                  s.hrComments                  || '',
      rejectionReason:             s.rejectionReason             || '',
      relevantExperience:          s.relevantExperience          || '',
      availability:                s.availability                || '',
    } : EMPTY_HR);
    const f = c.finalRound;
    setFinalForm(f ? {
      finalInterviewDate:     f.finalInterviewDate     || '',
      finalRemarks:           f.finalRemarks           || '',
      salaryRecommendation:   f.salaryRecommendation   || '',
      directorRecommendation: f.directorRecommendation || 'PENDING',
    } : EMPTY_FINAL);
    setHrDecisionForm(f ? {
      offeredCtc:  f.offeredCtc  || '',
      joiningDate: f.joiningDate || '',
    } : EMPTY_HR_DECISION);
    setStageTab(statusToStep(c.status));
  };

  /* ── Upload ─────────────────────────────────────────────────────────────── */

  const closeUploadDialog = () => {
    setUploadDialog(false);
    setUploadFile(null);
    setStoredResume(null);
    setUploadForm(EMPTY_UPLOAD);
  };

  /**
   * Step 1 — called the instant the user selects a file.
   * Sends the file to the server immediately so it's stored on disk,
   * then pre-fills the form with every field the parser extracted.
   */
  const handleFileSelect = async (file) => {
    if (!file) return;
    const ext = file.name.split('.').pop()?.toLowerCase();
    if (!['pdf', 'doc', 'docx'].includes(ext)) {
      toast.error('Only PDF, DOC, DOCX files are accepted');
      return;
    }
    setUploadFile(file);
    setStoredResume(null);
    setUploadStoring(true);
    try {
      const result = await interviewApi.storeResume(file);

      setStoredResume({
        resumePath:         result.resumePath,
        resumeOriginalName: result.resumeOriginalName,
        rawResumeText:      result.rawResumeText || '',
        educationSummary:   result.educationSummary   || '',
        currentDesignation: result.currentDesignation || '',
        currentCompanyCv:   result.currentCompanyCv   || '',
      });

      let parsed = result;
      // For image PDFs we track OCR-parsed fields separately so we never fall back
      // to a stale form value or an unreliable PDFBox guess for contact fields.
      let ocrParsed = null;

      // If backend extracted little/no text (image-based or complex-layout PDF), run browser OCR
      const isImagePdf = ext === 'pdf' && (!result.rawResumeText || result.rawResumeText.trim().length < 100);
      if (isImagePdf) {
        toast.info('Image-based CV detected — running OCR in browser, please wait...', { autoClose: false, toastId: 'ocr' });
        try {
          const { ocrPdfFile } = await import('../utils/ocrPdf');
          const ocrText = await ocrPdfFile(file, p => {
            if (p % 25 === 0) toast.update('ocr', { render: `OCR in progress: ${p}%` });
          });
          console.log('[OCR] text length:', ocrText.trim().length);
          if (ocrText.trim().length > 50) {
            ocrParsed = await interviewApi.parseText(ocrText);
            console.log('[OCR] parsed fields:', ocrParsed);
            parsed = { ...result, ...ocrParsed };
          }
          toast.dismiss('ocr');
        } catch (ocrErr) {
          toast.dismiss('ocr');
          console.warn('Browser OCR failed:', ocrErr);
        }
      }

      const extractedName    = isImagePdf ? (ocrParsed?.name    || '') : (parsed.name    || '');
      const extractedEmail   = isImagePdf ? (ocrParsed?.email   || '') : (parsed.email   || '');
      const extractedPhone   = isImagePdf ? (ocrParsed?.phone   || '') : (parsed.phone   || '');
      const extractedAddress = isImagePdf ? (ocrParsed?.address || '') : (parsed.address || '');

      setUploadForm(f => ({
        ...f,
        // For image PDFs: use OCR result only — empty string if not found (user types it).
        // Never fall back to PDFBox's guess or a stale previous-upload form value.
        name:                 extractedName    || f.name    || '',
        email:                extractedEmail   || f.email   || '',
        phone:                extractedPhone   || f.phone   || '',
        address:              extractedAddress || f.address || '',
        addressStreet:        parsed.addressStreet        || '',
        addressArea:          parsed.addressArea          || '',
        addressLandmark:      parsed.addressLandmark      || '',
        addressCity:          parsed.addressCity          || '',
        addressDistrict:      parsed.addressDistrict      || '',
        addressState:         parsed.addressState         || '',
        addressPostalCode:    parsed.addressPostalCode    || '',
        addressCountry:       parsed.addressCountry       || '',
        skills:               parsed.skills               || f.skills               || '',
        totalExperienceYears: parsed.totalExperienceYears || f.totalExperienceYears || '',
        linkedinUrl:          parsed.linkedinUrl          || f.linkedinUrl          || '',
        githubUrl:            parsed.githubUrl            || f.githubUrl            || '',
      }));

      toast.success('CV stored — details auto-extracted. Review and confirm.');
    } catch (err) {
      toast.error(err?.message || 'Failed to store CV on server');
      setUploadFile(null);
    } finally {
      setUploadStoring(false);
    }
  };

  /** Builds the shared FormData payload used by both create and replace-resume flows. */
  const buildCandidateFormData = () => {
    const fd = new FormData();
    fd.append('resumePath',         storedResume.resumePath);
    fd.append('resumeOriginalName', storedResume.resumeOriginalName);
    fd.append('rawResumeText',      storedResume.rawResumeText      || '');
    fd.append('educationSummary',   storedResume.educationSummary   || '');
    fd.append('currentDesignation', storedResume.currentDesignation || '');
    fd.append('currentCompanyCv',   storedResume.currentCompanyCv   || '');
    ['name','email','phone',
     'address','addressStreet','addressArea','addressLandmark',
     'addressCity','addressDistrict','addressState','addressPostalCode','addressCountry',
     'appliedProfile','officeLocation','source',
     'skills','totalExperienceYears','linkedinUrl','githubUrl']
      .forEach(k => fd.append(k, uploadForm[k] || ''));
    return fd;
  };

  /**
   * Step 2 — called when the user clicks "Add to CV Bank".
   * Before creating a new record, checks for a duplicate candidate.
   * If one is found, shows the replacement confirmation dialog instead.
   */
  const handleUploadSubmit = async () => {
    if (!uploadForm.name.trim())           { toast.error('Candidate name is required');   return; }
    if (!uploadForm.appliedProfile.trim()) { toast.error('Applied Profile is required');  return; }
    if (!uploadForm.officeLocation)        { toast.error('Office Location is required');  return; }
    if (!storedResume)                     { toast.error('Please upload a CV first');     return; }

    setUploadSaving(true);
    try {
      const existing = await interviewApi.checkDuplicate({
        email: uploadForm.email,
        phone: uploadForm.phone,
        name:  uploadForm.name,
      });

      if (existing?.id) {
        setDupCandidate(existing);
        setDupDialog(true);
        return; // wait for user choice in the duplicate dialog
      }

      await interviewApi.uploadCandidate(buildCandidateFormData());
      toast.success(`${uploadForm.name} added to CV Bank`);
      closeUploadDialog();
      await refreshCandidates();
    } catch (err) {
      toast.error(err?.message || 'Failed to save candidate');
    } finally {
      setUploadSaving(false);
    }
  };

  /** User clicked "Replace Resume" in the duplicate warning dialog. */
  const handleReplaceResume = async () => {
    if (!dupCandidate || !storedResume) return;
    setDupReplacing(true);
    try {
      await interviewApi.replaceResume(dupCandidate.id, buildCandidateFormData());
      toast.success(`CV replaced for ${dupCandidate.name} — previous version archived`);
      setDupDialog(false);
      setDupCandidate(null);
      closeUploadDialog();
      await refreshCandidates();
    } catch (err) {
      toast.error(err?.message || 'Failed to replace CV');
    } finally {
      setDupReplacing(false);
    }
  };

  /** User clicked "Cancel" in the duplicate warning dialog — keep the upload form open. */
  const handleDupCancel = () => {
    setDupDialog(false);
    setDupCandidate(null);
  };

  /* ── Detail open ───────────────────────────────────────────────────────── */
  const openDetail = async (c) => {
    setSelectedCand(null);
    setHrForm(EMPTY_HR);
    setFinalForm(EMPTY_FINAL);
    setHrDecisionForm(EMPTY_HR_DECISION);
    setDetailLoading(true);
    try {
      const full = await interviewApi.getCandidate(c.id);
      setSelectedCand(full);
      populateForms(full);
    } catch { toast.error('Failed to load candidate'); }
    finally { setDetailLoading(false); }
  };

  const openDetailAtHrTab = (c) => openDetail(c).then(() => setStageTab(1));

  /* ── Resume viewer ─────────────────────────────────────────────────────── */
  const openResume = async (candidateId) => {
    try {
      const token = localStorage.getItem('accessToken');
      const BASE = process.env.REACT_APP_API_URL || '/api';
      const res = await fetch(`${BASE}/interviews/candidates/${candidateId}/resume`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) { toast.error('Could not load resume'); return; }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
    } catch { toast.error('Could not open resume'); }
  };

  /* ── CV Bank actions ───────────────────────────────────────────────────── */
  const handleOpenHrScreening = async (c) => {
    try {
      await interviewApi.openHrScreening(c.id);
      toast.success('Moved to HR Review');
      await refreshCandidates();
      openDetail(c);
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed'); }
  };

  const handleReject = async (id) => {
    try {
      await interviewApi.rejectFromCvBank(id);
      toast.success('Candidate rejected');
      if (selectedCand?.id === id) await refreshDetail(id);
      await refreshCandidates();
    } catch { toast.error('Failed to reject'); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Permanently delete this candidate and all records?')) return;
    try {
      await interviewApi.deleteCandidate(id);
      toast.success('Deleted');
      if (selectedCand?.id === id) setSelectedCand(null);
      await refreshCandidates();
    } catch { toast.error('Failed to delete'); }
  };

  /* ── Edit ──────────────────────────────────────────────────────────────── */
  const openEdit = (c) => {
    setEditForm({
      name: c.name, email: c.email || '', phone: c.phone || '', address: c.address || '',
      addressStreet: c.addressStreet || '', addressArea: c.addressArea || '',
      addressLandmark: c.addressLandmark || '', addressCity: c.addressCity || '',
      addressDistrict: c.addressDistrict || '', addressState: c.addressState || '',
      addressPostalCode: c.addressPostalCode || '', addressCountry: c.addressCountry || '',
      appliedProfile: c.appliedProfile, officeLocation: c.officeLocation || '', source: c.source || '',
      skills: c.skills || '', totalExperienceYears: c.totalExperienceYears || '',
      currentDesignation: c.currentDesignation || '', currentCompanyCv: c.currentCompanyCv || '',
      educationSummary: c.educationSummary || '', linkedinUrl: c.linkedinUrl || '', githubUrl: c.githubUrl || '',
    });
    setEditDialog(true);
  };

  const handleEditSave = async () => {
    if (!editForm.name?.trim() || !editForm.appliedProfile?.trim()) { toast.error('Name & Applied Profile required'); return; }
    setEditSaving(true);
    try {
      await interviewApi.updateCandidate(selectedCand.id, editForm);
      toast.success('Updated');
      setEditDialog(false);
      await refreshDetail(selectedCand.id);
      await refreshCandidates();
    } catch { toast.error('Failed to update'); }
    finally { setEditSaving(false); }
  };

  /* ── HR Screening ──────────────────────────────────────────────────────── */
  const handleSaveHr = async () => {
    setHrSaving(true);
    try {
      await interviewApi.saveHrScreening(selectedCand.id, hrForm);
      toast.success('Screening form saved');
      await Promise.all([
        refreshDetail(selectedCand.id),
        refreshCandidates(),
      ]);
    } catch { toast.error('Failed to save'); }
    finally { setHrSaving(false); }
  };

  const handleHrDecision = async (decision) => {
    if (decision === 'SUITABLE') {
      setAssignForm({ interviewerId: '', scheduledAt: '' });
      setAssignDialog(true);
    } else {
      if (!hrForm.rejectionReason?.trim()) {
        toast.error('Rejection reason is mandatory before marking Not Suitable');
        return;
      }
      setHrSaving(true);
      try {
        const updated = await interviewApi.submitHrDecision(selectedCand.id, { ...hrForm, decision: 'NOT_SUITABLE' });
        toast.success('Marked Not Suitable — rejection email sent');
        setSelectedCand(updated);
        populateForms(updated);
        await refreshCandidates();
      } catch (err) { toast.error(err?.message || 'Failed to submit decision'); }
      finally { setHrSaving(false); }
    }
  };


  const handleAssignTechnical = async () => {
    if (!assignForm.interviewerId) { toast.error('Please select an interviewer'); return; }
    setAssigning(true);
    try {
      const updated = await interviewApi.submitHrDecision(selectedCand.id, {
        ...hrForm,
        decision:     'SUITABLE',
        interviewerId: Number(assignForm.interviewerId),
        // assignForm.scheduledAt is already a naive local "YYYY-MM-DDTHH:mm" string from
        // AppDateTimePicker — send it as-is so the exact wall-clock time the HR/Admin picked
        // is preserved. Converting via `new Date(...).toISOString()` shifts it by the
        // browser's UTC offset before the backend's zoneless LocalDateTime field stores it.
        scheduledAt:   assignForm.scheduledAt || null,
      });
      toast.success('Marked Suitable — Technical interview assigned, manager notified');
      setAssignDialog(false);
      setSelectedCand(updated);
      populateForms(updated);
      await refreshCandidates();
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed'); }
    finally { setAssigning(false); }
  };

  /* ── Technical Feedback ────────────────────────────────────────────────── */
  const openTechFeedback = (tech) => {
    setSelTech(tech);
    setTechForm({
      technicalSkillsRating:      tech.technicalSkillsRating      || 3,
      communicationRating:         tech.communicationRating         || 3,
      problemSolvingRating:        tech.problemSolvingRating        || 3,
      codingAbilityRating:         tech.codingAbilityRating         || 3,
      architectureKnowledgeRating: tech.architectureKnowledgeRating || 3,
      comments: tech.comments || '',
      decision: (tech.decision && tech.decision !== 'PENDING') ? tech.decision : '',
    });
    setTechDialog(true);
  };

  const openGenerateLinkDialog = (tech) => {
    setLinkTechId(tech.id);
    setLinkForm({
      technology: tech.interviewTechnology || tech.candidateAppliedProfile || '',
      difficulty: tech.interviewDifficulty || 'MIXED',
      questionCount: tech.questionCount || 20,
    });
    setGeneratedLink(tech.interviewLink || '');
    setLinkDialog(true);
  };

  const handleGenerateLink = async () => {
    setLinkGenerating(true);
    try {
      const updated = await interviewApi.generateInterviewLink(linkTechId, {
        technology: linkForm.technology, difficulty: linkForm.difficulty, questionCount: linkForm.questionCount,
      });
      setGeneratedLink(updated.interviewLink || '');
      toast.success('Interview link generated and emailed to candidate!');
      await refreshCandidates();
      if (selectedCand) {
        const fresh = await interviewApi.getCandidate(selectedCand.id).catch(() => null);
        if (fresh) { setSelectedCand(fresh); populateForms(fresh); }
      }
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed to generate link'); }
    finally { setLinkGenerating(false); }
  };

  const handleTechFeedbackSubmit = async () => {
    if (!techForm.decision) { toast.error('Select Approve or Reject'); return; }
    setTechSaving(true);
    try {
      const updated = await interviewApi.submitTechnicalFeedback(selTech.id, techForm);
      toast.success(techForm.decision === 'APPROVE'
        ? 'Approved — moved to Final Round, director notified'
        : 'Rejected — rejection email sent');
      setTechDialog(false);
      const assigns = await interviewApi.getMyAssignments().catch(() => myAssignments);
      setMyAssignments(Array.isArray(assigns) ? assigns : myAssignments);
      if (selectedCand) { setSelectedCand(updated); populateForms(updated); }
      await refreshCandidates();
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed'); }
    finally { setTechSaving(false); }
  };

  /* ── Final Round: Director submits interview notes + recommendation ─────── */
  const handleSaveFinal = async () => {
    if (!finalForm.finalInterviewDate) { toast.error('Final Interview Date is required'); return; }
    setFinalSaving(true);
    try {
      await interviewApi.saveFinalRound(selectedCand.id, finalForm);
      toast.success('Interview notes submitted — HR has been notified');
      await refreshDetail(selectedCand.id);
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed to save'); }
    finally { setFinalSaving(false); }
  };

  /* ── Final Round: HR reviews the Director's notes and records the decision ── */
  const handleFinalDecision = async (decision) => {
    if (!selectedCand.finalRound?.id) { toast.error('The Director must submit interview notes first'); return; }
    if (decision === 'APPROVE' && (!hrDecisionForm.offeredCtc || !hrDecisionForm.joiningDate)) {
      toast.error('Offered CTC and Joining Date are required to approve');
      return;
    }
    setHrDeciding(true);
    try {
      const updated = await interviewApi.submitFinalDecision(selectedCand.finalRound.id, { ...hrDecisionForm, finalDecision: decision });
      toast.success(decision === 'APPROVE' ? 'Candidate Selected — offer email sent!' : decision === 'REJECT' ? 'Candidate rejected' : 'Candidate placed on hold');
      setSelectedCand(updated);
      populateForms(updated);
      await refreshCandidates();
    } catch (err) { toast.error(err?.response?.data?.message || 'Failed'); }
    finally { setHrDeciding(false); }
  };

  const handleAssignFinalDirector = async () => {
    if (!assignDirectorForm.directorId) { toast.error('Please select a Director'); return; }
    setAssigningDirector(true);
    try {
      await interviewApi.assignFinalRoundDirector(assignDirectorCandId, {
        directorId: Number(assignDirectorForm.directorId),
      });
      toast.success('Candidate assigned to Director — they have been notified');
      setAssignDirectorDialog(false);
      await refreshDetail(assignDirectorCandId);
      await refreshCandidates();
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to assign Director');
    } finally {
      setAssigningDirector(false);
    }
  };

  const handleGenerateFinalLink = async () => {
    setFinalLinkGenerating(true);
    try {
      // finalLinkForm.scheduledAt is a naive local "YYYY-MM-DDTHH:mm" string from
      // AppDateTimePicker — send it as-is (see handleAssignTechnical for why).
      const updated = await interviewApi.generateFinalInterviewLink(finalLinkCandId, {
        scheduledAt: finalLinkForm.scheduledAt || null,
      });
      toast.success('Final interview link generated — email sent to candidate');
      setFinalLinkDialog(false);
      await refreshDetail(finalLinkCandId);
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Failed to generate link');
    } finally {
      setFinalLinkGenerating(false);
    }
  };

  /* ── Filters ───────────────────────────────────────────────────────────── */
  // Pre-filtered by search + location + profile (status chip applied separately below)
  const preFilteredCands = useMemo(() => {
    const q = search.toLowerCase();
    return candidates.filter(c => {
      const ms = !q || c.name?.toLowerCase().includes(q) || c.appliedProfile?.toLowerCase().includes(q) || c.candidateId?.toLowerCase().includes(q);
      const ml = locationFilter === 'ALL' || c.officeLocation === locationFilter;
      const mp = profileFilter  === 'ALL' || c.appliedProfile === profileFilter;
      return ms && ml && mp;
    });
  }, [candidates, search, locationFilter, profileFilter]);

  const filteredCands = useMemo(() =>
    statusFilter === 'ALL'
      ? preFilteredCands
      : preFilteredCands.filter(c => c.status === statusFilter),
  [preFilteredCands, statusFilter]);

  const hrCands    = useMemo(() =>
    candidates.filter(c => c.status === 'UNDER_HR_REVIEW' || c.hrScreening),
  [candidates]);
  const pagedHrCands = useMemo(() =>
    hrCands.slice(hrPage * HR_RPP, (hrPage + 1) * HR_RPP),
  [hrCands, hrPage]);
  const techCands  = useMemo(() => candidates.filter(c => c.status === 'TECHNICAL_PENDING'), [candidates]);
  const finalCands = useMemo(() => candidates.filter(c => c.status === 'FINAL_ROUND_PENDING'), [candidates]);

  /* ── Sync hrTableForms whenever the HR tab is active or hrCands changes ── */
  useEffect(() => { // eslint-disable-line react-hooks/exhaustive-deps
    if (tab !== 'hr') return;
    const forms = {};
    hrCands.forEach(c => {
      const s = c.hrScreening || {};
      forms[c.id] = {
        currentRoleResponsibilities: s.currentRoleResponsibilities || '',
        reasonForChange:             s.reasonForChange             || '',
        currentCtc:                  s.currentCtc                  || '',
        expectedCtc:                 s.expectedCtc                 || '',
        noticePeriod:                s.noticePeriod                || '',
        preferredLocation:           s.preferredLocation           || '',
        workBase:                    s.workBase                    || '',
        totalExperience:             s.totalExperience             || '',
        currentCompany:              s.currentCompany              || '',
        screeningDate:               s.screeningDate               || '',
        communicationSkills:         s.communicationSkills         || '',
        hrComments:                  s.hrComments                  || '',
        rejectionReason:             s.rejectionReason             || '',
      };
    });
    setHrTableForms(forms);
  }, [tab, hrCands]);

  /* ── Loading ───────────────────────────────────────────────────────────── */
  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress sx={{ color: '#1e3a5f' }} />
    </Box>
  );

  /* ══════════════════════════════════════════════════════════════════════════ */
  /* CANDIDATE DETAIL VIEW                                                      */
  /* ══════════════════════════════════════════════════════════════════════════ */
  if (selectedCand) {
    const c          = selectedCand;
    const step       = statusToStep(c.status);
    const rejected   = ['HR_REJECTED', 'TECHNICAL_REJECTED', 'REJECTED'].includes(c.status);
    const selected   = c.status === 'SELECTED';
    const techs      = c.technicalInterviews || [];
    const finalData  = c.finalRound;
    const hrData     = c.hrScreening;
    const canHrAct   = canManage && c.status === 'UNDER_HR_REVIEW';
    const techApproved      = [...techs].reverse().find(t => t.decision === 'APPROVE');
    const hasDirector        = !!finalData?.conductedById;
    const isAssignedDirector = hasDirector && Number(user?.employeeId) === Number(finalData.conductedById);
    // Any Admin, or specifically the Director this Final Round was assigned to — mirrors the
    // backend's requireFinalRoundOwner() check, which is the real enforcement point.
    const canActOnFinalRound = user?.role === 'ADMIN' || isAssignedDirector;
    // Director submits notes; only HR records the actual hiring decision — separation of duties.
    const canSubmitDirectorNotes = canActOnFinalRound && c.status === 'FINAL_ROUND_PENDING';
    const directorNotesSubmitted = !!finalData?.directorNotesAt;
    const canRecordHrDecision = isHR && c.status === 'FINAL_ROUND_PENDING' && directorNotesSubmitted;

    return (
      <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>
        {detailLoading && <LinearProgress sx={{ mb: 1 }} />}

        {/* ── Header ── */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
          <Box>
            <Button startIcon={<ArrowBackIcon />} size="small" onClick={() => setSelectedCand(null)}
              sx={{ color: '#64748b', textTransform: 'none', mb: 1 }}>
              Back to {tab === 'cvbank' ? 'CV Bank' : tab === 'hr' ? 'HR Screening' : tab === 'final' ? 'Final Round' : 'List'}
            </Button>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Avatar sx={{ bgcolor: '#1e3a5f', width: 48, height: 48, fontSize: 20 }}>
                {c.name?.charAt(0)?.toUpperCase()}
              </Avatar>
              <Box>
                <Box>
                  <Typography variant="h5" fontWeight={700}>{c.name}</Typography>
                </Box>
                <Typography variant="body2" color="text.secondary">
                  {c.appliedProfile}{c.officeLocation ? ` · ${c.officeLocation}` : ''}
                </Typography>
              </Box>
              <StatusChip status={c.status} />
            </Box>
          </Box>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Tooltip title="Back to Dashboard">
              <IconButton
                size="small"
                onClick={() => {
                  setSelectedCand(null);
                  setTab(isManager && !canManage ? 'technical' : 'cvbank');
                  setPage(0);
                  loadAll();
                }}
                sx={{ border: '1px solid #e2e8f0', borderRadius: 2 }}>
                <RefreshIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            {canManage && (
              <Tooltip title="Edit">
                <IconButton size="small" onClick={() => openEdit(c)} sx={{ border: '1px solid #e2e8f0', borderRadius: 2 }}>
                  <EditIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {isAdmin && (
              <Tooltip title="Delete">
                <IconButton size="small" color="error" onClick={() => handleDelete(c.id)} sx={{ border: '1px solid #fecaca', borderRadius: 2 }}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            )}
            {c.resumeOriginalName && (
              <Button variant="outlined" startIcon={<DescriptionIcon />} size="small"
                onClick={() => openResume(c.id)}
                sx={{ textTransform: 'none', borderColor: '#e2e8f0' }}>
                Resume
              </Button>
            )}
          </Box>
        </Box>

        {/* ── Stage Stepper ── */}
        <Card sx={{ borderRadius: 3, mb: 3 }}>
          <CardContent sx={{ py: 2 }}>
            <Stepper activeStep={step} alternativeLabel>
              {STAGE_STEPS.map((label, idx) => (
                <Step key={label} completed={selected || (!rejected && idx < step)}>
                  <StepLabel error={rejected && idx === step}
                    sx={{ '& .MuiStepLabel-label': { fontSize: 12, fontWeight: 600 }, cursor: idx <= step ? 'pointer' : 'default' }}
                    onClick={() => idx <= step && setStageTab(idx)}>
                    {label}
                  </StepLabel>
                </Step>
              ))}
            </Stepper>
          </CardContent>
        </Card>

        {/* ── Stage Sub-tabs ── */}
        <Tabs value={stageTab} onChange={(_, v) => setStageTab(v)}
          sx={{ mb: 2, '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, fontSize: 13, minWidth: 'auto', px: 2.5 },
                '& .MuiTabs-indicator': { bgcolor: '#1e3a5f' } }}>
          <Tab label="CV Info" value={0} />
          <Tab label="HR Screening" value={1} disabled={step < 1} />
          <Tab label="Technical Interview" value={2} disabled={step < 2} />
          <Tab label="Final Round" value={3} disabled={step < 3} />
        </Tabs>

        {/* ── Stage 0: CV Info ── */}
        {stageTab === 0 && (
          <Grid container spacing={3}>
            {/* ── Left column ── */}
            <Grid item xs={12} md={5}>
              <Card sx={{ borderRadius: 3, mb: 2 }}>
                <CardContent>
                  <SectionTitle>Candidate Information</SectionTitle>
                  {[
                    ['Email',           c.email],
                    ['Phone',           c.phone],
                    ['Applied Profile', c.appliedProfile],
                    ['Office Location', c.officeLocation],
                    ['Source',          c.source],
                    ['Added By',        c.createdByName],
                    ['Added On',        fmtDate(c.createdAt)],
                  ].filter(([, v]) => v).map(([label, val]) => (
                    <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.25, pb: 1.25, borderBottom: '1px solid #f1f5f9' }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={600}>{label}</Typography>
                      <Typography variant="caption" fontWeight={500} sx={{ maxWidth: 200, textAlign: 'right' }}>{val}</Typography>
                    </Box>
                  ))}
                  {c.address && (
                    <Box sx={{ mt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={600}>Address</Typography>
                      <Typography variant="body2" sx={{ mt: 0.5, color: '#475569', fontSize: 13 }}>{c.address}</Typography>
                    </Box>
                  )}
                </CardContent>
              </Card>

              {/* ── Resume file ── */}
              {c.resumeOriginalName && (
                <Card sx={{ borderRadius: 3, mb: 2 }}>
                  <CardContent sx={{ py: '14px !important' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <DescriptionIcon sx={{ color: '#1e3a5f', fontSize: 28 }} />
                        <Box>
                          <Typography variant="body2" fontWeight={600} sx={{ fontSize: 13 }}>{c.resumeOriginalName}</Typography>
                          <Typography variant="caption" color="text.secondary">Uploaded CV</Typography>
                        </Box>
                      </Box>
                      <Button onClick={() => openResume(c.id)}
                        size="small" startIcon={<OpenInNewIcon />} variant="outlined"
                        sx={{ textTransform: 'none', fontSize: 12, borderColor: '#e2e8f0', color: '#1e3a5f' }}>
                        Open
                      </Button>
                    </Box>
                  </CardContent>
                </Card>
              )}

              {/* ── CV Bank actions ── */}
              {canManage && c.status === 'NEW' && (
                <Card sx={{ borderRadius: 3 }}>
                  <CardContent>
                    <SectionTitle>CV Bank Actions</SectionTitle>
                    <Stack spacing={1}>
                      <Button fullWidth variant="contained" startIcon={<AssignmentIcon />}
                        onClick={() => handleOpenHrScreening(c)}
                        sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', fontWeight: 600 }}>
                        Open HR Screening
                      </Button>
                      <Button fullWidth variant="outlined" color="error" startIcon={<CancelIcon />}
                        onClick={() => handleReject(c.id)}
                        sx={{ textTransform: 'none', fontWeight: 600 }}>
                        Reject Candidate
                      </Button>
                    </Stack>
                  </CardContent>
                </Card>
              )}
            </Grid>

            {/* ── Right column: Resume Insights ── */}
            <Grid item xs={12} md={7}>
              <Card sx={{ borderRadius: 3, border: '1px solid #e8f0fe' }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                    <SectionTitle>Resume Insights</SectionTitle>
                    {!c.skills && !c.totalExperienceYears && !c.educationSummary &&
                     !c.linkedinUrl && !c.githubUrl && !c.currentDesignation && !c.currentCompanyCv && (
                      <Typography variant="caption" color="text.secondary">
                        No CV uploaded — data entered manually
                      </Typography>
                    )}
                  </Box>

                  {/* Experience & Current Role */}
                  {(c.totalExperienceYears || c.currentDesignation || c.currentCompanyCv) ? (
                    <Box sx={{ mb: 2.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={700}
                        sx={{ textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.6, display: 'block', mb: 0.75 }}>
                        Experience
                      </Typography>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        {c.totalExperienceYears && (
                          <Chip label={c.totalExperienceYears} size="small"
                            sx={{ bgcolor: '#1e3a5f', color: '#fff', fontWeight: 700, fontSize: 12 }} />
                        )}
                        {c.currentDesignation && (
                          <Typography variant="body2" fontWeight={600}>{c.currentDesignation}</Typography>
                        )}
                        {c.currentCompanyCv && (
                          <Typography variant="body2" color="text.secondary">@ {c.currentCompanyCv}</Typography>
                        )}
                      </Box>
                    </Box>
                  ) : c.educationSummary && (
                    <Box sx={{ mb: 2.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={700}
                        sx={{ textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.6, display: 'block', mb: 0.75 }}>
                        Experience
                      </Typography>
                      <Chip label="Fresher" size="small"
                        sx={{ bgcolor: '#dcfce7', color: '#16a34a', fontWeight: 700, fontSize: 12, border: '1px solid #bbf7d0' }} />
                    </Box>
                  )}

                  {/* Skills */}
                  {c.skills && (
                    <Box sx={{ mb: 2.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={700}
                        sx={{ textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.6, display: 'block', mb: 0.75 }}>
                        Skills ({c.skills.split(',').filter(Boolean).length})
                      </Typography>
                      <SkillChips skills={c.skills} />
                    </Box>
                  )}

                  {/* Education */}
                  {c.educationSummary && (
                    <Box sx={{ mb: 2.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={700}
                        sx={{ textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.6, display: 'block', mb: 0.75 }}>
                        Education
                      </Typography>
                      {c.educationSummary.split('|').map((edu, i) => (
                        <Box key={i} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, mb: 0.5 }}>
                          <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: '#1e3a5f', mt: '6px', flexShrink: 0 }} />
                          <Typography variant="body2" sx={{ fontSize: 13 }}>{edu.trim()}</Typography>
                        </Box>
                      ))}
                    </Box>
                  )}

                  {/* Social / Profile links */}
                  {(c.linkedinUrl || c.githubUrl) && (
                    <Box sx={{ mb: 1 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={700}
                        sx={{ textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.6, display: 'block', mb: 0.75 }}>
                        Online Profiles
                      </Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        {c.linkedinUrl && (
                          <Button href={c.linkedinUrl} target="_blank" rel="noopener noreferrer"
                            variant="outlined" size="small" startIcon={<OpenInNewIcon />}
                            sx={{ textTransform: 'none', fontSize: 12, borderColor: '#0077b5', color: '#0077b5', '&:hover': { bgcolor: '#f0f9ff' } }}>
                            LinkedIn
                          </Button>
                        )}
                        {c.githubUrl && (
                          <Button href={c.githubUrl} target="_blank" rel="noopener noreferrer"
                            variant="outlined" size="small" startIcon={<OpenInNewIcon />}
                            sx={{ textTransform: 'none', fontSize: 12, borderColor: '#333', color: '#333', '&:hover': { bgcolor: '#f8f9fa' } }}>
                            GitHub
                          </Button>
                        )}
                      </Stack>
                    </Box>
                  )}

                  {/* Empty state */}
                  {!c.skills && !c.totalExperienceYears && !c.educationSummary &&
                   !c.linkedinUrl && !c.githubUrl && !c.currentDesignation && !c.currentCompanyCv && (
                    <Box sx={{ textAlign: 'center', py: 3 }}>
                      <DescriptionIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1 }} />
                      <Typography variant="body2" color="text.secondary">
                        {c.resumeOriginalName
                          ? 'CV was uploaded but no structured data could be extracted'
                          : 'Upload a CV to auto-extract skills, experience, education and more'}
                      </Typography>
                    </Box>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        )}

        {/* ── Stage 1: HR Screening ── */}
        {stageTab === 1 && (
          <Grid container spacing={3}>
            <Grid item xs={12} md={8}>
              <Card sx={{ borderRadius: 3 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2.5 }}>
                    <SectionTitle>HR Screening Questionnaire</SectionTitle>
                    {hrData?.decision && hrData.decision !== 'PENDING' && (
                      <Chip label={hrData.decision === 'SUITABLE' ? 'Suitable ✓' : 'Not Suitable ✗'}
                        size="small"
                        sx={{ bgcolor: hrData.decision === 'SUITABLE' ? '#dcfce7' : '#fee2e2',
                              color:   hrData.decision === 'SUITABLE' ? '#16a34a' : '#dc2626', fontWeight: 700 }} />
                    )}
                  </Box>

                  {canHrAct ? (<>
                    {/* Q1 & Q2 — full-width multiline */}
                    {[
                      { num: 1, key: 'currentRoleResponsibilities', label: 'What is your current role and responsibilities?' },
                      { num: 2, key: 'reasonForChange',             label: 'Why do you want to leave your current organisation?' },
                    ].map(({ num, key, label }) => (
                      <Box key={key} sx={{ display: 'flex', gap: 1.5, mb: 2, alignItems: 'flex-start' }}>
                        <Box sx={{ minWidth: 26, height: 26, borderRadius: '50%', bgcolor: '#1e3a5f', color: '#fff',
                          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, flexShrink: 0 }}>
                          {num}
                        </Box>
                        <Box sx={{ flex: 1 }}>
                          <Typography variant="body2" fontWeight={700} sx={{ mb: 0.75, color: '#374151', lineHeight: 1.4 }}>{label}</Typography>
                          <TextField fullWidth size="small" multiline rows={2} placeholder="—"
                            value={hrForm[key]}
                            onChange={e => setHrForm(f => ({ ...f, [key]: e.target.value }))} />
                        </Box>
                      </Box>
                    ))}

                    {/* Q3–Q10 — paired two-column grid */}
                    <Grid container spacing={2} sx={{ mb: 2 }}>
                      {[
                        { num: 3,  key: 'currentCtc',        label: 'What is your current pay?' },
                        { num: 4,  key: 'expectedCtc',       label: 'What do you expect from us? Is it negotiable?' },
                        { num: 5,  key: 'noticePeriod',      label: 'What is the duration of your notice period?' },
                        { num: 6,  key: 'preferredLocation', label: 'Location' },
                        { num: 7,  key: 'workBase',          label: 'What is your work base? (UK / US / India)' },
                        { num: 8,  key: 'totalExperience',   label: 'How many years of experience do you have?' },
                        { num: 9,  key: 'currentCompany',    label: 'Previous / Current Company' },
                        { num: 10, key: 'screeningDate',     label: 'Screening Date', type: 'date' },
                      ].map(({ num, key, label, type }) => (
                        <Grid item xs={12} sm={6} key={key}>
                          <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
                            <Box sx={{ minWidth: 26, height: 26, borderRadius: '50%', bgcolor: '#1e3a5f', color: '#fff',
                              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, flexShrink: 0, mt: 0.25 }}>
                              {num}
                            </Box>
                            <Box sx={{ flex: 1 }}>
                              <Typography variant="body2" fontWeight={700} sx={{ mb: 0.75, color: '#374151', lineHeight: 1.4 }}>{label}</Typography>
                              <TextField fullWidth size="small"
                                type={type || 'text'}
                                InputLabelProps={type === 'date' ? { shrink: true } : undefined}
                                placeholder={type === 'date' ? undefined : '—'}
                                value={hrForm[key]}
                                onChange={e => setHrForm(f => ({ ...f, [key]: e.target.value }))} />
                            </Box>
                          </Box>
                        </Grid>
                      ))}
                    </Grid>

                    {/* Q11 — Communication rating with visual bar */}
                    <Box sx={{ display: 'flex', gap: 1.5, mb: 2.5, alignItems: 'flex-start' }}>
                      <Box sx={{ minWidth: 26, height: 26, borderRadius: '50%', bgcolor: '#1e3a5f', color: '#fff',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, flexShrink: 0 }}>
                        11
                      </Box>
                      <Box sx={{ flex: 1 }}>
                        <Typography variant="body2" fontWeight={700} sx={{ mb: 0.75, color: '#374151' }}>
                          Communication per HR (Rate 1–10)
                        </Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                          <TextField size="small" type="number" placeholder="1–10"
                            inputProps={{ min: 1, max: 10 }}
                            value={hrForm.communicationSkills}
                            onChange={e => setHrForm(f => ({ ...f, communicationSkills: e.target.value }))}
                            sx={{ width: 100 }} />
                          {hrForm.communicationSkills && Number(hrForm.communicationSkills) > 0 && (
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.4 }}>
                              {[1,2,3,4,5,6,7,8,9,10].map(n => {
                                const val = parseInt(hrForm.communicationSkills, 10) || 0;
                                const col = val >= 8 ? '#16a34a' : val >= 5 ? '#ca8a04' : '#dc2626';
                                return <Box key={n} sx={{ width: 18, height: 18, borderRadius: 0.5, bgcolor: n <= val ? col : '#e2e8f0' }} />;
                              })}
                              <Typography variant="caption" fontWeight={700} sx={{ ml: 0.5, color: '#374151' }}>
                                {hrForm.communicationSkills}/10
                              </Typography>
                            </Box>
                          )}
                        </Box>
                      </Box>
                    </Box>

                    <Divider sx={{ my: 2 }} />

                    {/* Remarks */}
                    <Box sx={{ mb: 2 }}>
                      <Typography variant="body2" fontWeight={700} sx={{ mb: 0.75, color: '#374151' }}>
                        Remarks / Notes
                      </Typography>
                      <TextField fullWidth size="small" multiline rows={2} placeholder="Additional observations…"
                        value={hrForm.hrComments}
                        onChange={e => setHrForm(f => ({ ...f, hrComments: e.target.value }))} />
                    </Box>

                    {/* Rejection Reason */}
                    <Box sx={{ mb: 1 }}>
                      <TextField
                        fullWidth size="small" multiline rows={2}
                        label={<>Rejection Reason <Box component="span" sx={{ color: '#dc2626', ml: 0.25 }}>*</Box></>}
                        placeholder="Required if marking as Not Suitable — will be included in the candidate's rejection email"
                        value={hrForm.rejectionReason}
                        onChange={e => setHrForm(f => ({ ...f, rejectionReason: e.target.value }))}
                        error={hrForm.rejectionReason === '' && hrData?.decision === 'NOT_SUITABLE'}
                        sx={{
                          '& .MuiOutlinedInput-root': {
                            '& fieldset': { borderColor: '#fca5a5' },
                            '&:hover fieldset': { borderColor: '#ef4444' },
                            '&.Mui-focused fieldset': { borderColor: '#dc2626' },
                          },
                        }}
                        helperText="Mandatory for rejection · sent to candidate in the rejection email"
                      />
                    </Box>

                    {hrData?.conductedByName && (
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 1.5, display: 'block' }}>
                        Conducted by {hrData.conductedByName} · Updated {fmtDt(hrData.updatedAt)}
                      </Typography>
                    )}

                    <Box sx={{ display: 'flex', gap: 1.5, mt: 2.5, flexWrap: 'wrap' }}>
                      <Button variant="outlined" onClick={handleSaveHr} disabled={hrSaving}
                        sx={{ textTransform: 'none', borderColor: '#e2e8f0', color: '#475569', fontWeight: 600 }}>
                        {hrSaving ? 'Saving…' : 'Save Progress'}
                      </Button>
                      <Button variant="contained" startIcon={<CheckCircleIcon />}
                        onClick={() => handleHrDecision('SUITABLE')} disabled={hrSaving}
                        sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', fontWeight: 600 }}>
                        Suitable → Assign Technical
                      </Button>
                      <Tooltip title={!hrForm.rejectionReason?.trim() ? 'Fill in Rejection Reason above before marking Not Suitable' : ''} arrow>
                        <span>
                          <Button variant="outlined" color="error" startIcon={<CancelIcon />}
                            onClick={() => handleHrDecision('NOT_SUITABLE')}
                            disabled={hrSaving || !hrForm.rejectionReason?.trim()}
                            sx={{ textTransform: 'none', fontWeight: 600 }}>
                            Not Suitable
                          </Button>
                        </span>
                      </Tooltip>
                    </Box>
                  </>) : hrData ? (<>
                    {/* Read-only Q&A summary — candidate / non-HR viewer */}

                    {/* Q1 & Q2 full-width */}
                    {[
                      { num: 1, key: 'currentRoleResponsibilities', label: 'Current role & responsibilities' },
                      { num: 2, key: 'reasonForChange',             label: 'Why leaving current organisation' },
                    ].map(({ num, key, label }) => (
                      <Box key={key} sx={{ display: 'flex', gap: 1.5, pb: 2, mb: 2, borderBottom: '1px solid #f1f5f9', alignItems: 'flex-start' }}>
                        <Box sx={{ minWidth: 22, height: 22, borderRadius: '50%', bgcolor: '#f59e0b', color: '#fff',
                          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, flexShrink: 0 }}>
                          {num}
                        </Box>
                        <Box sx={{ flex: 1 }}>
                          <Typography variant="caption" sx={{ color: '#64748b', fontWeight: 600, textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.3 }}>
                            {label}
                          </Typography>
                          <Typography variant="body2" sx={{ mt: 0.5, color: hrData[key] ? '#1e293b' : '#94a3b8', fontStyle: hrData[key] ? 'normal' : 'italic', whiteSpace: 'pre-wrap' }}>
                            {hrData[key] || 'Not answered'}
                          </Typography>
                        </Box>
                      </Box>
                    ))}

                    {/* Q3–Q10 two-column grid */}
                    <Grid container sx={{ mb: 2 }}>
                      {[
                        { num: 3,  key: 'currentCtc',        label: 'Current pay' },
                        { num: 4,  key: 'expectedCtc',       label: 'Expected pay (negotiable?)' },
                        { num: 5,  key: 'noticePeriod',      label: 'Notice period' },
                        { num: 6,  key: 'preferredLocation', label: 'Location' },
                        { num: 7,  key: 'workBase',          label: 'Work base (UK / US / India)' },
                        { num: 8,  key: 'totalExperience',   label: 'Total experience' },
                        { num: 9,  key: 'currentCompany',    label: 'Previous / Current company' },
                        { num: 10, key: 'screeningDate',     label: 'Screening date' },
                      ].map(({ num, key, label }) => (
                        <Grid item xs={12} sm={6} key={key} sx={{ pb: 1.5, mb: 0.5, borderBottom: '1px solid #f1f5f9', pr: 1 }}>
                          <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
                            <Box sx={{ minWidth: 20, height: 20, borderRadius: '50%', bgcolor: '#f59e0b', color: '#fff',
                              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 9, fontWeight: 700, flexShrink: 0 }}>
                              {num}
                            </Box>
                            <Box>
                              <Typography variant="caption" sx={{ color: '#64748b', fontWeight: 600, textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.3 }}>
                                {label}
                              </Typography>
                              <Typography variant="body2" sx={{ mt: 0.25, color: hrData[key] ? '#1e293b' : '#94a3b8', fontStyle: hrData[key] ? 'normal' : 'italic' }}>
                                {key === 'screeningDate' ? fmtDate(hrData[key]) : (hrData[key] || '—')}
                              </Typography>
                            </Box>
                          </Box>
                        </Grid>
                      ))}
                    </Grid>

                    {/* Q11 — Communication */}
                    <Box sx={{ display: 'flex', gap: 1.5, pb: 2, mb: 2, borderBottom: '1px solid #f1f5f9', alignItems: 'flex-start' }}>
                      <Box sx={{ minWidth: 22, height: 22, borderRadius: '50%', bgcolor: '#f59e0b', color: '#fff',
                        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700, flexShrink: 0 }}>
                        11
                      </Box>
                      <Box sx={{ flex: 1 }}>
                        <Typography variant="caption" sx={{ color: '#64748b', fontWeight: 600, textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.3 }}>
                          Communication per HR (1–10)
                        </Typography>
                        {hrData.communicationSkills && Number(hrData.communicationSkills) > 0 ? (
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mt: 0.5 }}>
                            <Typography variant="h6" fontWeight={700} sx={{ color: '#1e293b', lineHeight: 1 }}>
                              {hrData.communicationSkills}
                            </Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.4 }}>
                              {[1,2,3,4,5,6,7,8,9,10].map(n => {
                                const val = parseInt(hrData.communicationSkills, 10) || 0;
                                const col = val >= 8 ? '#16a34a' : val >= 5 ? '#ca8a04' : '#dc2626';
                                return <Box key={n} sx={{ width: 18, height: 18, borderRadius: 0.5, bgcolor: n <= val ? col : '#e2e8f0' }} />;
                              })}
                              <Typography variant="caption" fontWeight={700} sx={{ ml: 0.5, color: '#374151' }}>/10</Typography>
                            </Box>
                          </Box>
                        ) : (
                          <Typography variant="body2" sx={{ mt: 0.5, color: '#94a3b8', fontStyle: 'italic' }}>Not rated</Typography>
                        )}
                      </Box>
                    </Box>

                    {/* Remarks */}
                    {hrData.hrComments && (
                      <Box sx={{ mb: 2, p: 2, bgcolor: '#f0f9ff', borderRadius: 2, border: '1px solid #bae6fd' }}>
                        <Typography variant="caption" fontWeight={700} sx={{ color: '#0369a1', textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.3, display: 'block', mb: 0.5 }}>
                          Remarks / Notes
                        </Typography>
                        <Typography variant="body2" sx={{ color: '#1e293b', whiteSpace: 'pre-wrap' }}>
                          {hrData.hrComments}
                        </Typography>
                      </Box>
                    )}

                    {/* Rejection Reason */}
                    {hrData.rejectionReason && (
                      <Alert severity="error" variant="outlined" sx={{ mb: 2, borderRadius: 2 }}>
                        <Typography variant="caption" fontWeight={700} sx={{ display: 'block', mb: 0.5, textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.3 }}>
                          Rejection Reason
                        </Typography>
                        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{hrData.rejectionReason}</Typography>
                      </Alert>
                    )}

                    {/* Screened-by footer */}
                    {hrData.conductedByName && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', pt: 1.5, borderTop: '1px solid #f1f5f9' }}>
                        Screened by {hrData.conductedByName} · {fmtDt(hrData.updatedAt)}
                      </Typography>
                    )}
                  </>) : (
                    <Alert severity="info" sx={{ borderRadius: 2 }}>
                      HR screening has not been conducted yet for this candidate.
                    </Alert>
                  )}
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={4}>
              <Card sx={{ borderRadius: 3 }}>
                <CardContent>
                  <SectionTitle>Candidate Quick View</SectionTitle>
                  {[['Name', c.name], ['Profile', c.appliedProfile], ['Email', c.email], ['Phone', c.phone], ['Location', c.officeLocation], ['Source', c.source]].filter(([, v]) => v).map(([l, v]) => (
                    <Box key={l} sx={{ display: 'flex', justifyContent: 'space-between', mb: 1.5 }}>
                      <Typography variant="caption" color="text.secondary" fontWeight={600}>{l}</Typography>
                      <Typography variant="caption" fontWeight={500}>{v}</Typography>
                    </Box>
                  ))}
                  {c.resumeOriginalName && (
                    <Button fullWidth variant="outlined" startIcon={<DescriptionIcon />} size="small"
                      onClick={() => openResume(c.id)}
                      sx={{ mt: 1, textTransform: 'none', borderColor: '#e2e8f0' }}>
                      View Resume
                    </Button>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        )}

        {/* ── Stage 2: Technical Interview ── */}
        {stageTab === 2 && (
          <Box>
            {techs.length === 0 ? (
              <Alert severity="info">No technical interview assigned yet.</Alert>
            ) : techs.map((tech, idx) => {
              const ivStatus = tech.interviewStatus || 'PENDING_LINK';
              const STATUS_BADGE = {
                PENDING_LINK:        { label: 'Link Not Generated', bg: '#f1f5f9', color: '#475569' },
                LINK_GENERATED:      { label: 'Waiting for Candidate', bg: '#dbeafe', color: '#1d4ed8' },
                IN_PROGRESS:         { label: 'In Progress', bg: '#fef3c7', color: '#d97706' },
                CANDIDATE_SUBMITTED: { label: 'Candidate Submitted', bg: '#dcfce7', color: '#16a34a' },
                EVALUATED:           { label: 'Evaluated', bg: '#f3e8ff', color: '#7c3aed' },
              };
              const badge = STATUS_BADGE[ivStatus] || STATUS_BADGE.PENDING_LINK;
              return (
              <Card key={tech.id} sx={{ borderRadius: 3, mb: 2 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2, flexWrap: 'wrap', gap: 1 }}>
                    <Box>
                      <Typography variant="subtitle1" fontWeight={700}>
                        Technical Interview {techs.length > 1 ? `#${idx + 1}` : ''}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Interviewer: <strong>{tech.interviewerName || '—'}</strong>
                        {tech.assignedByName && ` · Assigned by ${tech.assignedByName}`}
                      </Typography>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mt: 0.75 }}>
                        {tech.scheduledAt && (
                          <Box>
                            <Typography variant="caption" color="text.disabled" display="block">Scheduled</Typography>
                            <Typography variant="caption" fontWeight={600}>{fmtDt(tech.scheduledAt)}</Typography>
                          </Box>
                        )}
                        <Box>
                          <Typography variant="caption" color="text.disabled" display="block">Candidate Submitted</Typography>
                          <Typography variant="caption" fontWeight={600} color={tech.completedAt ? 'text.primary' : 'text.disabled'}>
                            {tech.completedAt ? fmtDt(tech.completedAt) : '—'}
                          </Typography>
                        </Box>
                        <Box>
                          <Typography variant="caption" color="text.disabled" display="block">Manager Evaluated</Typography>
                          <Typography variant="caption" fontWeight={600} color={tech.evaluatedAt ? '#7c3aed' : 'text.disabled'}>
                            {tech.evaluatedAt ? fmtDt(tech.evaluatedAt) : '—'}
                          </Typography>
                        </Box>
                      </Box>
                    </Box>
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                      <Chip label={badge.label} size="small"
                        sx={{ bgcolor: badge.bg, color: badge.color, fontWeight: 700 }} />

                      {/* Generate / Regenerate Link */}
                      {(isAdmin || isHR || isManager) && (ivStatus === 'PENDING_LINK' || ivStatus === 'LINK_GENERATED') && (
                        <Button variant="outlined" size="small" onClick={() => openGenerateLinkDialog(tech)}
                          sx={{ textTransform: 'none', borderColor: '#6366f1', color: '#6366f1', fontWeight: 600 }}>
                          {ivStatus === 'LINK_GENERATED' ? 'Regenerate Link' : 'Generate Interview Link'}
                        </Button>
                      )}

                      {/* Join Room — for manager during / after interview */}
                      {(ivStatus === 'IN_PROGRESS' || ivStatus === 'CANDIDATE_SUBMITTED') && (
                        <Button variant="contained" size="small"
                          onClick={() => window.open(`/interview/room/${tech.id}`, '_blank')}
                          sx={{ bgcolor: '#059669', '&:hover': { bgcolor: '#047857' }, textTransform: 'none', fontWeight: 700 }}>
                          {ivStatus === 'IN_PROGRESS' ? '🔴 Join Live Room' : 'Review Answers'}
                        </Button>
                      )}

                      {/* View evaluation room */}
                      {ivStatus === 'EVALUATED' && (
                        <Button variant="outlined" size="small"
                          onClick={() => window.open(`/interview/room/${tech.id}`, '_blank')}
                          sx={{ textTransform: 'none', borderColor: '#7c3aed', color: '#7c3aed', fontWeight: 600 }}>
                          View Evaluation
                        </Button>
                      )}

                      {/* Legacy feedback (non-video) */}
                      {tech.decision && tech.decision !== 'PENDING' ? (
                        <Chip label={tech.decision === 'APPROVE' ? 'Approved' : 'Rejected'}
                          sx={{ bgcolor: tech.decision === 'APPROVE' ? '#dcfce7' : '#fee2e2',
                                color:   tech.decision === 'APPROVE' ? '#16a34a' : '#dc2626', fontWeight: 700 }} />
                      ) : ivStatus === 'PENDING_LINK' ? (
                        <Button variant="contained" size="small" onClick={() => openTechFeedback(tech)}
                          sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none' }}>
                          {tech.technicalSkillsRating ? 'Edit Feedback' : 'Submit Feedback'}
                        </Button>
                      ) : null}
                    </Box>
                  </Box>

                  {/* Interview link info */}
                  {tech.interviewLink && ivStatus !== 'PENDING_LINK' && (
                    <Alert severity="info" sx={{ mb: 1.5, py: 0.5 }} icon={false}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <Typography variant="caption" fontWeight={600}>Interview Link:</Typography>
                        <Typography variant="caption" sx={{ color: '#1d4ed8', wordBreak: 'break-all' }}>
                          {tech.interviewLink}
                        </Typography>
                        <Button size="small" onClick={() => { navigator.clipboard.writeText(tech.interviewLink); toast.success('Link copied!'); }}
                          sx={{ textTransform: 'none', fontSize: 11, p: '2px 8px', minWidth: 0 }}>Copy</Button>
                      </Box>
                    </Alert>
                  )}

                  {/* Score bar */}
                  {tech.score != null && (
                    <Box sx={{ mb: 1.5 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                        <Typography variant="caption" color="text.secondary">MCQ Score</Typography>
                        <Typography variant="caption" fontWeight={700}>{tech.score}/{tech.totalMarks}</Typography>
                      </Box>
                      <LinearProgress variant="determinate"
                        value={tech.totalMarks > 0 ? Math.round((tech.score / tech.totalMarks) * 100) : 0}
                        sx={{ height: 6, borderRadius: 3, bgcolor: '#e2e8f0' }} />
                    </Box>
                  )}

                  {tech.technicalSkillsRating != null && (
                    <>
                      <Divider sx={{ my: 1.5 }} />
                      <Grid container spacing={2}>
                        {[
                          ['Technical Skills',       tech.technicalSkillsRating],
                          ['Communication',           tech.communicationRating],
                          ['Problem Solving',         tech.problemSolvingRating],
                          ['Coding Ability',          tech.codingAbilityRating],
                          ['Architecture Knowledge',  tech.architectureKnowledgeRating],
                        ].map(([label, val]) => (
                          <Grid item xs={12} sm={6} md={4} key={label}>
                            <Typography variant="caption" color="text.secondary">{label}</Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                              <Rating value={val || 0} readOnly size="small" />
                              <Typography variant="caption" fontWeight={700}>{val}/5</Typography>
                            </Box>
                          </Grid>
                        ))}
                        {tech.comments && (
                          <Grid item xs={12}>
                            <Typography variant="caption" color="text.secondary">Comments</Typography>
                            <Typography variant="body2">{tech.comments}</Typography>
                          </Grid>
                        )}
                      </Grid>
                    </>
                  )}
                </CardContent>
              </Card>
              );
            })}
          </Box>
        )}

        {/* ── Stage 3: Final Round ── */}
        {stageTab === 3 && (() => {
          const fivStatus = finalData?.interviewStatus || 'PENDING_LINK';
          const FIV_BADGE = {
            PENDING_LINK:        { label: 'Link Not Generated',    bg: '#f1f5f9', color: '#475569' },
            LINK_GENERATED:      { label: 'Waiting for Candidate', bg: '#dbeafe', color: '#1d4ed8' },
            IN_PROGRESS:         { label: '🔴 In Progress',        bg: '#fef3c7', color: '#d97706' },
            CANDIDATE_SUBMITTED: { label: 'Interview Submitted',   bg: '#dcfce7', color: '#16a34a' },
            EVALUATED:           { label: 'Evaluated',             bg: '#f3e8ff', color: '#7c3aed' },
          };
          const fivBadge   = FIV_BADGE[fivStatus] || FIV_BADGE.PENDING_LINK;
          const canJoin    = fivStatus === 'IN_PROGRESS' || fivStatus === 'CANDIDATE_SUBMITTED';
          const isEvaluated= fivStatus === 'EVALUATED';
          const decisionCfg = {
            APPROVE: { label: 'APPROVED — SELECTED', bg: '#dcfce7', color: '#16a34a' },
            HOLD:    { label: 'ON HOLD',              bg: '#fef3c7', color: '#d97706' },
            REJECT:  { label: 'REJECTED',             bg: '#fee2e2', color: '#dc2626' },
          };
          const decCfg = finalData?.finalDecision && finalData.finalDecision !== 'PENDING'
            ? decisionCfg[finalData.finalDecision] : null;

          return (
          <Grid container spacing={3}>
            <Grid item xs={12} md={8}>

              {/* Technical Evaluation Summary — for HR/Director review before/while handling Final Round */}
              {techApproved && (
                <Card sx={{ borderRadius: 3, mb: 2 }}>
                  <CardContent>
                    <SectionTitle>Technical Evaluation Summary</SectionTitle>
                    <Grid container spacing={1.5} sx={{ mt: 0.5 }}>
                      {[
                        ['Technical Skills',      techApproved.technicalSkillsRating],
                        ['Communication',         techApproved.communicationRating],
                        ['Problem Solving',       techApproved.problemSolvingRating],
                        ['Coding Ability',        techApproved.codingAbilityRating],
                        ['Architecture Knowledge',techApproved.architectureKnowledgeRating],
                      ].map(([label, val]) => val && (
                        <Grid item xs={12} sm={4} key={label}>
                          <Typography variant="caption" color="text.secondary">{label}</Typography>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                            <Rating value={val} readOnly size="small" />
                            <Typography variant="caption" fontWeight={700}>{val}/5</Typography>
                          </Box>
                        </Grid>
                      ))}
                      {techApproved.comments && (
                        <Grid item xs={12}>
                          <Typography variant="caption" color="text.secondary">Interviewer Comments</Typography>
                          <Typography variant="body2">{techApproved.comments}</Typography>
                        </Grid>
                      )}
                    </Grid>
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 1.5, display: 'block' }}>
                      Approved by {techApproved.interviewerName || 'the interviewer'}
                      {techApproved.updatedAt && ` · ${fmtDt(techApproved.updatedAt)}`}
                    </Typography>
                  </CardContent>
                </Card>
              )}

              {/* Video Interview Card */}
              <Card sx={{ borderRadius: 3, mb: 2 }}>
                <CardContent>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, flexWrap: 'wrap', gap: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <SectionTitle sx={{ mb: 0 }}>Final Video Interview</SectionTitle>
                      <Chip label={fivBadge.label} size="small"
                        sx={{ bgcolor: fivBadge.bg, color: fivBadge.color, fontWeight: 700 }} />
                      {decCfg && (
                        <Chip label={decCfg.label} size="small"
                          sx={{ bgcolor: decCfg.bg, color: decCfg.color, fontWeight: 700 }} />
                      )}
                    </Box>
                    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                      {/* HR/Admin: assign a Director before anything else can happen */}
                      {canManage && !hasDirector && (
                        <Button variant="contained" size="small"
                          onClick={() => { setAssignDirectorCandId(c.id); setAssignDirectorForm({ directorId: '' }); setAssignDirectorDialog(true); }}
                          sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' }, textTransform: 'none', fontWeight: 700 }}>
                          Assign to Director
                        </Button>
                      )}
                      {canManage && hasDirector && (fivStatus === 'PENDING_LINK' || fivStatus === 'LINK_GENERATED') && (
                        <Button variant="outlined" size="small"
                          onClick={() => { setAssignDirectorCandId(c.id); setAssignDirectorForm({ directorId: finalData?.conductedById || '' }); setAssignDirectorDialog(true); }}
                          sx={{ textTransform: 'none', borderColor: '#94a3b8', color: '#475569', fontWeight: 600 }}>
                          Reassign Director
                        </Button>
                      )}
                      {/* Director: schedule the interview & generate the secure link */}
                      {hasDirector && canActOnFinalRound && (fivStatus === 'PENDING_LINK' || fivStatus === 'LINK_GENERATED') && (
                        <Button variant="outlined" size="small"
                          onClick={() => { setFinalLinkCandId(c.id); setFinalLinkForm({ scheduledAt: finalData?.scheduledAt ? finalData.scheduledAt.slice(0, 16) : '' }); setFinalLinkDialog(true); }}
                          sx={{ textTransform: 'none', borderColor: '#7c3aed', color: '#7c3aed', fontWeight: 600 }}>
                          {fivStatus === 'LINK_GENERATED' ? 'Reschedule / Regenerate Link' : 'Schedule & Generate Interview Link'}
                        </Button>
                      )}
                      {/* Join Room — Director's live/review room; not shown to HR */}
                      {!isHR && canJoin && (
                        <Button variant="contained" size="small"
                          onClick={() => window.open(`/interview/final-room/${finalData.id}`, '_blank')}
                          sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' }, textTransform: 'none', fontWeight: 700 }}>
                          {fivStatus === 'IN_PROGRESS' ? '🔴 Join Live Room' : 'Review Interview'}
                        </Button>
                      )}
                      {/* View evaluation — Director's room; HR sees the read-only summary below instead */}
                      {!isHR && isEvaluated && finalData?.id && (
                        <Button variant="outlined" size="small"
                          onClick={() => window.open(`/interview/final-room/${finalData.id}`, '_blank')}
                          sx={{ textTransform: 'none', borderColor: '#7c3aed', color: '#7c3aed', fontWeight: 600 }}>
                          View Evaluation
                        </Button>
                      )}
                    </Box>
                  </Box>

                  {/* Director assignment / schedule */}
                  {!hasDirector ? (
                    <Alert severity="warning" sx={{ mb: 1.5, py: 0.5 }}>
                      No Director assigned yet — {canManage ? 'assign one above to proceed.' : 'awaiting HR to assign a Director.'}
                    </Alert>
                  ) : (
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 1.5 }}>
                      <Box>
                        <Typography variant="caption" color="text.disabled" display="block">Assigned Director</Typography>
                        <Typography variant="caption" fontWeight={600}>{finalData.conductedByName}</Typography>
                      </Box>
                      {finalData?.scheduledAt && (
                        <Box>
                          <Typography variant="caption" color="text.disabled" display="block">Scheduled For</Typography>
                          <Typography variant="caption" fontWeight={600}>{fmtDt(finalData.scheduledAt)}</Typography>
                        </Box>
                      )}
                    </Box>
                  )}

                  {/* Interview link */}
                  {finalData?.interviewLink && fivStatus !== 'PENDING_LINK' && (
                    <Alert severity="info" sx={{ mb: 1.5, py: 0.5 }} icon={false}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <Typography variant="caption" fontWeight={600}>Interview Link:</Typography>
                        <Typography variant="caption" sx={{ color: '#1d4ed8', wordBreak: 'break-all' }}>
                          {finalData.interviewLink}
                        </Typography>
                        <Button size="small"
                          onClick={() => { navigator.clipboard.writeText(finalData.interviewLink); toast.success('Link copied!'); }}
                          sx={{ textTransform: 'none', fontSize: 11, p: '2px 8px', minWidth: 0 }}>Copy</Button>
                      </Box>
                    </Alert>
                  )}

                  {/* Timestamps */}
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
                    {[
                      ['Candidate Started',   finalData?.startedAt],
                      ['Candidate Submitted', finalData?.completedAt],
                      ['Director Evaluated',  finalData?.evaluatedAt],
                    ].map(([label, val]) => (
                      <Box key={label}>
                        <Typography variant="caption" color="text.disabled" display="block">{label}</Typography>
                        <Typography variant="caption" fontWeight={600} color={val ? 'text.primary' : 'text.disabled'}>
                          {fmtDt(val)}
                        </Typography>
                      </Box>
                    ))}
                  </Box>

                  {/* Evaluation summary when evaluated */}
                  {isEvaluated && finalData?.overallRating && (
                    <>
                      <Divider sx={{ my: 1.5 }} />
                      <Grid container spacing={1.5}>
                        {[
                          ['Overall',       finalData.overallRating],
                          ['Communication', finalData.communicationRating],
                          ['Culture Fit',   finalData.cultureFitRating],
                        ].map(([label, val]) => val && (
                          <Grid item xs={12} sm={4} key={label}>
                            <Typography variant="caption" color="text.secondary">{label}</Typography>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                              <Rating value={val} readOnly size="small" />
                              <Typography variant="caption" fontWeight={700}>{val}/5</Typography>
                            </Box>
                          </Grid>
                        ))}
                        {finalData.offeredCtc && (
                          <Grid item xs={12} sm={4}>
                            <Typography variant="caption" color="text.secondary">Offered CTC</Typography>
                            <Typography variant="body2" fontWeight={600}>{finalData.offeredCtc}</Typography>
                          </Grid>
                        )}
                        {finalData.noticePeriod && (
                          <Grid item xs={12} sm={4}>
                            <Typography variant="caption" color="text.secondary">Notice Period</Typography>
                            <Typography variant="body2" fontWeight={600}>{finalData.noticePeriod}</Typography>
                          </Grid>
                        )}
                        {finalData.directorRemarks && (
                          <Grid item xs={12}>
                            <Typography variant="caption" color="text.secondary">Director Remarks</Typography>
                            <Typography variant="body2">{finalData.directorRemarks}</Typography>
                          </Grid>
                        )}
                      </Grid>
                    </>
                  )}
                </CardContent>
              </Card>

              {/* Director's interview notes + advisory recommendation */}
              <Card sx={{ borderRadius: 3, mb: 2 }}>
                <CardContent>
                  <SectionTitle>Director's Interview Notes</SectionTitle>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <TextField fullWidth size="small" label="Final Interview Date *" type="date"
                        value={finalForm.finalInterviewDate}
                        onChange={e => setFinalForm(f => ({ ...f, finalInterviewDate: e.target.value }))}
                        disabled={!canSubmitDirectorNotes} InputLabelProps={{ shrink: true }} />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <TextField fullWidth size="small" label="Your Recommendation"
                        select SelectProps={{ native: true }}
                        value={finalForm.directorRecommendation}
                        onChange={e => setFinalForm(f => ({ ...f, directorRecommendation: e.target.value }))}
                        disabled={!canSubmitDirectorNotes}>
                        {RECOMMENDATION_OPTIONS.map(o => <option key={o} value={o}>{o}</option>)}
                      </TextField>
                    </Grid>
                    <Grid item xs={12}>
                      <TextField fullWidth size="small" label="Salary Recommendation"
                        value={finalForm.salaryRecommendation}
                        onChange={e => setFinalForm(f => ({ ...f, salaryRecommendation: e.target.value }))}
                        disabled={!canSubmitDirectorNotes} placeholder="e.g. ₹12 LPA" />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField fullWidth size="small" label="Remarks" multiline rows={2}
                        value={finalForm.finalRemarks}
                        onChange={e => setFinalForm(f => ({ ...f, finalRemarks: e.target.value }))}
                        disabled={!canSubmitDirectorNotes} />
                    </Grid>
                  </Grid>
                  {directorNotesSubmitted ? (
                    <Typography variant="caption" color="text.secondary" sx={{ mt: 1.5, display: 'block' }}>
                      Submitted by {finalData.conductedByName} · {fmtDt(finalData.directorNotesAt)}
                    </Typography>
                  ) : hasDirector && (
                    <Alert severity="info" sx={{ mt: 1.5, py: 0.5 }}>
                      Notes not submitted yet — HR cannot record a hiring decision until the Director submits them.
                    </Alert>
                  )}
                  {canSubmitDirectorNotes && (
                    <Box sx={{ display: 'flex', gap: 1.5, mt: 2, flexWrap: 'wrap' }}>
                      <Button variant="outlined" onClick={handleSaveFinal}
                        disabled={finalSaving || !finalForm.finalInterviewDate}
                        sx={{ textTransform: 'none', borderColor: '#e2e8f0', color: '#475569', fontWeight: 600 }}>
                        {finalSaving ? 'Saving…' : directorNotesSubmitted ? 'Update Notes' : 'Submit Notes'}
                      </Button>
                    </Box>
                  )}
                </CardContent>
              </Card>

              {/* HR's hiring decision — reviews the Director's notes, sends the offer/rejection.
                  Only HR sees this section; Admin and Director never do. */}
              {isHR && (directorNotesSubmitted || finalData?.decidedAt) && (
                <Card sx={{ borderRadius: 3 }}>
                  <CardContent>
                    <SectionTitle>HR Hiring Decision</SectionTitle>
                    {finalData?.directorRecommendation && finalData.directorRecommendation !== 'PENDING' && (
                      <Alert severity="info" sx={{ mb: 2, py: 0.5 }}>
                        Director recommends: <strong>{finalData.directorRecommendation}</strong>
                        {finalData.salaryRecommendation && ` · Suggested salary: ${finalData.salaryRecommendation}`}
                      </Alert>
                    )}
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth size="small" label="Offered CTC *"
                          value={hrDecisionForm.offeredCtc}
                          onChange={e => setHrDecisionForm(f => ({ ...f, offeredCtc: e.target.value }))}
                          disabled={!canRecordHrDecision} placeholder="e.g. ₹12 LPA" />
                      </Grid>
                      <Grid item xs={12} sm={6}>
                        <TextField fullWidth size="small" label="Joining Date *" type="date"
                          value={hrDecisionForm.joiningDate}
                          onChange={e => setHrDecisionForm(f => ({ ...f, joiningDate: e.target.value }))}
                          disabled={!canRecordHrDecision} InputLabelProps={{ shrink: true }} />
                      </Grid>
                    </Grid>
                    {finalData?.decidedAt && (
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 1.5, display: 'block' }}>
                        Decision recorded by {finalData.decidedByName} · {fmtDt(finalData.decidedAt)}
                      </Typography>
                    )}
                    {canRecordHrDecision && (
                      <Box sx={{ display: 'flex', gap: 1.5, mt: 2, flexWrap: 'wrap' }}>
                        <Button variant="contained" onClick={() => handleFinalDecision('APPROVE')}
                          disabled={hrDeciding}
                          sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', fontWeight: 700 }}>
                          {hrDeciding ? 'Sending…' : 'Approve & Send Offer'}
                        </Button>
                        <Button variant="outlined" onClick={() => handleFinalDecision('HOLD')}
                          disabled={hrDeciding}
                          sx={{ textTransform: 'none', borderColor: '#d97706', color: '#d97706', fontWeight: 600 }}>
                          Hold
                        </Button>
                        <Button variant="outlined" onClick={() => handleFinalDecision('REJECT')}
                          disabled={hrDeciding}
                          sx={{ textTransform: 'none', borderColor: '#dc2626', color: '#dc2626', fontWeight: 600 }}>
                          Reject
                        </Button>
                      </Box>
                    )}
                  </CardContent>
                </Card>
              )}
            </Grid>

            <Grid item xs={12} md={4}>
              {selected && (
                <Card sx={{ borderRadius: 3, bgcolor: '#f0fdf4', border: '1px solid #bbf7d0' }}>
                  <CardContent sx={{ textAlign: 'center', py: 3 }}>
                    <EmojiEventsIcon sx={{ fontSize: 48, color: '#16a34a', mb: 1 }} />
                    <Typography variant="h6" fontWeight={700} color="#16a34a">Candidate Selected!</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Offer email has been sent to {c.email || 'the candidate'}.
                    </Typography>
                    {(finalData?.offeredCtc || finalData?.salaryRecommendation) && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        Salary: {finalData.offeredCtc || finalData.salaryRecommendation}
                      </Typography>
                    )}
                    {finalData?.joiningDate && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                        Joining: {fmtDate(finalData.joiningDate)}
                      </Typography>
                    )}
                  </CardContent>
                </Card>
              )}
            </Grid>
          </Grid>
          );
        })()}

        {renderDialogs()}
      </Box>
    );
  }

  /* ══════════════════════════════════════════════════════════════════════════ */
  /* MAIN VIEW                                                                  */
  /* ══════════════════════════════════════════════════════════════════════════ */

  /* Stats data for CV Bank header */
  const statCards = stats ? [
    { label: 'Total Candidates',     value: stats.total,             color: '#1e3a5f' },
    { label: 'New',                  value: stats.new,               color: '#475569' },
    { label: 'Under HR Review',      value: stats.underHrReview,     color: '#1d4ed8' },
    { label: 'Technical Pending',    value: stats.technicalPending,  color: '#d97706' },
    { label: 'Final Round Pending',  value: stats.finalRoundPending, color: '#7c3aed' },
    { label: 'Selected',             value: stats.selected,          color: '#16a34a' },
    { label: 'All Rejected',         value: (Number(stats.hrRejected) + Number(stats.technicalRejected) + Number(stats.rejected)), color: '#dc2626' },
  ] : [];

  /* Shared candidate table component */
  const CandidateTable = ({ rows, emptyMsg }) => (
    <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: '#f1f5f9' }}>
              {['Candidate', 'Applied Profile', 'Experience / Skills', 'Location', 'Status', 'Added', 'Actions'].map(h => (
                <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#475569', py: 1.5, whiteSpace: 'nowrap' }}>{h}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 6 }}>
                  <WorkIcon sx={{ fontSize: 40, color: '#cbd5e1', mb: 1, display: 'block', mx: 'auto' }} />
                  <Typography color="text.secondary">{emptyMsg || 'No candidates'}</Typography>
                </TableCell>
              </TableRow>
            ) : rows.slice(page * RPP, (page + 1) * RPP).map((c, i) => (
              <TableRow key={c.id} hover sx={{ cursor: 'pointer', bgcolor: i % 2 === 0 ? '#fff' : '#f8fafc' }}
                onClick={() => openDetail(c)}>
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Avatar sx={{ bgcolor: '#1e3a5f', width: 28, height: 28, fontSize: 11 }}>{c.name?.charAt(0)}</Avatar>
                    <Box>
                      <Typography variant="body2" fontWeight={600}>{c.name}</Typography>
                      {c.email && <Typography variant="caption" color="text.secondary">{c.email}</Typography>}
                    </Box>
                  </Box>
                </TableCell>
                <TableCell><Typography variant="body2">{c.appliedProfile}</Typography></TableCell>
                <TableCell sx={{ minWidth: 160 }}>
                  {c.totalExperienceYears && (
                    <Chip label={c.totalExperienceYears} size="small"
                      sx={{ bgcolor: '#f1f5f9', color: '#1e3a5f', fontWeight: 700, fontSize: 10, mb: 0.5, mr: 0.5 }} />
                  )}
                  {c.skills && (
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontSize: 10 }}>
                      {c.skills.split(',').filter(Boolean).length} skill{c.skills.split(',').filter(Boolean).length !== 1 ? 's' : ''} extracted
                    </Typography>
                  )}
                  {!c.totalExperienceYears && !c.skills && (
                    <Typography variant="caption" color="text.secondary">—</Typography>
                  )}
                </TableCell>
                <TableCell><Typography variant="body2">{c.officeLocation || '—'}</Typography></TableCell>
                <TableCell><StatusChip status={c.status} /></TableCell>
                <TableCell sx={{ fontSize: 12, color: '#64748b', whiteSpace: 'nowrap' }}>{fmtDate(c.resumeUploadedAt || c.createdAt)}</TableCell>
                <TableCell onClick={e => e.stopPropagation()}>
                  {canManage && c.status === 'NEW' && (
                    <Box sx={{ display: 'flex', gap: 0.5 }}>
                      <Tooltip title="Open HR Screening">
                        <IconButton size="small" sx={{ color: '#1e3a5f' }}
                          onClick={e => { e.stopPropagation(); handleOpenHrScreening(c); }}>
                          <AssignmentIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Reject">
                        <IconButton size="small" color="error"
                          onClick={e => { e.stopPropagation(); handleReject(c.id); }}>
                          <CancelIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {rows.length > RPP && (
        <TablePagination component="div" count={rows.length} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={RPP} rowsPerPageOptions={[RPP]} />
      )}
    </Card>
  );

  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>
      {/* ── Sticky header: title, stats, tabs, and (for CV Bank) the filter row ── */}
      <Box sx={{ position: 'sticky', top: 64, zIndex: 2, bgcolor: '#f8fafc', pb: 1 }}>
      {/* ── Page header ── */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
        <Box>
          <Typography variant="h5" fontWeight={700}>Interview Management Portal</Typography>
          <Typography variant="body2" color="text.secondary" mt={0.5}>
            CV Bank → HR Screening → Technical Interview → Final Round
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          {tab === 'cvbank' && canManage && (
            <Button variant="contained" startIcon={<CloudUploadIcon />}
              onClick={() => { setUploadForm(EMPTY_UPLOAD); setUploadFile(null); setStoredResume(null); setUploadDialog(true); }}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', borderRadius: 2 }}>
              Upload Resume
            </Button>
          )}
          {canManage && (
            <Tooltip title="Refresh">
              <IconButton
                onClick={() => {
                  setSelectedCand(null);
                  setTab(isManager && !canManage ? 'technical' : 'cvbank');
                  setPage(0);
                  loadAll();
                }}
                sx={{ border: '1px solid #e2e8f0', borderRadius: 2 }}>
                <RefreshIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Box>
      </Box>

      {/* ── Stats (CV Bank only) ── */}
      {tab === 'cvbank' && stats && (
        <Grid container spacing={1.5} sx={{ mb: 3 }}>
          {statCards.map(s => (
            <Grid item xs={6} sm={4} md key={s.label}>
              <Card sx={{ borderRadius: 2, boxShadow: '0 2px 8px rgba(0,0,0,0.05)', height: '100%' }}>
                <CardContent sx={{ p: 1.25, '&:last-child': { pb: 1.25 } }}>
                  <Box sx={{ minHeight: 18, display: 'flex', alignItems: 'flex-start' }}>
                    <Typography variant="caption" color="text.secondary" fontWeight={600}
                      sx={{ textTransform: 'uppercase', fontSize: 9.5, letterSpacing: 0.5, lineHeight: 1.3 }}>
                      {s.label}
                    </Typography>
                  </Box>
                  <Typography variant="h5" fontWeight={800} sx={{ color: s.color, mt: 0.25 }}>{s.value}</Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      {/* ── Tabs ── */}
      {(() => {
        const tabLabel = (title, n) => (
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 0.5, lineHeight: 1.3 }}>
            <Box component="span">{title}</Box>
            <Box component="span" sx={{ fontSize: 11, fontWeight: 500, color: '#94a3b8', textTransform: 'none' }}>
              ({n} candidate{n === 1 ? '' : 's'})
            </Box>
          </Box>
        );
        const techCount = canManage ? techCands.length : myAssignments.length;
        return (
          <Tabs value={tab} onChange={(_, v) => { setTab(v); setPage(0); setHrPage(0); setSearch(''); setStatusFilter('ALL'); setLocationFilter('ALL'); setProfileFilter('ALL'); }}
            sx={{ mb: 4.5, borderBottom: '1px solid #e2e8f0',
                  '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, minWidth: 'auto', px: 2, py: 1.5 },
                  '& .MuiTabs-indicator': { bgcolor: '#1e3a5f' } }}>
            {canManage && (
              <Tab icon={<GroupIcon fontSize="small" />} iconPosition="start"
                label={tabLabel('CV Bank', candidates.length)} value="cvbank" />
            )}
            {canManage && (
              <Tab icon={<AssignmentIcon fontSize="small" />} iconPosition="start"
                label={tabLabel('HR Screening', hrCands.length)} value="hr" />
            )}
            <Tab icon={<WorkIcon fontSize="small" />} iconPosition="start"
              label={isManager && !canManage
                ? tabLabel('My Assignments', myAssignments.length)
                : tabLabel('Technical', techCount)}
              value="technical" />
            {canManage && (
              <Tab icon={<EmojiEventsIcon fontSize="small" />} iconPosition="start"
                label={tabLabel('Final Round', finalCands.length)} value="final" />
            )}
          </Tabs>
        );
      })()}

      {/* ── Search + Dropdowns row (CV Bank only) — part of the sticky header ── */}
      {tab === 'cvbank' && canManage && (
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
          <TextField placeholder="Search by name, profile, or Cand ID…" value={search} size="small"
            onChange={e => { setSearch(e.target.value); setPage(0); }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: '#94a3b8' }} /></InputAdornment> }}
            sx={{ width: 260, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }} />

          <FormControl size="small" sx={{ width: 155, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
            <InputLabel sx={{ fontSize: 13 }}>Office Location</InputLabel>
            <Select
              value={locationFilter}
              label="Office Location"
              onChange={e => { setLocationFilter(e.target.value); setPage(0); }}
              sx={{ fontSize: 13 }}>
              <MenuItem value="ALL"><em>All Locations</em></MenuItem>
              {allLocations.map(l => <MenuItem key={l} value={l} sx={{ fontSize: 13 }}>{l}</MenuItem>)}
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ width: 155, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
            <InputLabel sx={{ fontSize: 13 }}>Applied Profile</InputLabel>
            <Select
              value={profileFilter}
              label="Applied Profile"
              onChange={e => { setProfileFilter(e.target.value); setPage(0); }}
              sx={{ fontSize: 13 }}>
              <MenuItem value="ALL"><em>All Profiles</em></MenuItem>
              {allProfiles.map(p => <MenuItem key={p} value={p} sx={{ fontSize: 13 }}>{p}</MenuItem>)}
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ width: 185, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }}>
            <InputLabel sx={{ fontSize: 13 }}>Status</InputLabel>
            <Select
              value={statusFilter}
              label="Status"
              onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
              sx={{ fontSize: 13 }}>
              <MenuItem value="ALL" sx={{ fontSize: 13 }}><em>All</em></MenuItem>
              <MenuItem value="NEW"                 sx={{ fontSize: 13 }}>New</MenuItem>
              <MenuItem value="UNDER_HR_REVIEW"     sx={{ fontSize: 13 }}>Under HR Review</MenuItem>
              <MenuItem value="TECHNICAL_PENDING"   sx={{ fontSize: 13 }}>Technical Pending</MenuItem>
              <MenuItem value="FINAL_ROUND_PENDING" sx={{ fontSize: 13 }}>Final Round Pending</MenuItem>
              <MenuItem value="SELECTED"            sx={{ fontSize: 13 }}>Selected</MenuItem>
              <MenuItem value="HR_REJECTED"         sx={{ fontSize: 13 }}>HR Rejected</MenuItem>
              <MenuItem value="TECHNICAL_REJECTED"  sx={{ fontSize: 13 }}>Technical Rejected</MenuItem>
              <MenuItem value="REJECTED"            sx={{ fontSize: 13 }}>Rejected</MenuItem>
            </Select>
          </FormControl>

          {(locationFilter !== 'ALL' || profileFilter !== 'ALL' || statusFilter !== 'ALL' || search) && (
            <Button size="small" variant="outlined"
              onClick={() => { setSearch(''); setLocationFilter('ALL'); setProfileFilter('ALL'); setStatusFilter('ALL'); setPage(0); }}
              sx={{ textTransform: 'none', fontSize: 12, borderColor: '#e2e8f0', color: '#64748b',
                    borderRadius: 2, height: 40, whiteSpace: 'nowrap' }}>
              Clear Filters
            </Button>
          )}
        </Box>
      )}
      </Box>
      {/* ── /Sticky header ── */}

      {/* ═══ CV BANK TAB ════════════════════════════════════════════════════ */}
      {tab === 'cvbank' && canManage && (
        <Box sx={{ pt: 2 }}>
          <CandidateTable rows={filteredCands} emptyMsg="No candidates match the selected filters" />
        </Box>
      )}

      {/* ═══ HR SCREENING TAB ═══════════════════════════════════════════════ */}
      {tab === 'hr' && canManage && (
        <Box>
          {hrCands.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 8 }}>
              <AssignmentIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 1 }} />
              <Typography color="text.secondary" variant="h6">No HR Screening data yet</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Open a candidate for HR Screening from the CV Bank, then save the screening form to see data here
              </Typography>
            </Box>
          ) : (
            <Card sx={{ borderRadius: 3, overflow: 'hidden' }}>
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table size="small" sx={{ tableLayout: 'fixed', minWidth: 250 + pagedHrCands.length * 230 }}>
                  {/* ── Header: Questions label + candidate names ── */}
                  <TableHead>
                    <TableRow sx={{ bgcolor: '#1e3a5f', height: 36 }}>
                      <TableCell sx={{
                        width: 250, minWidth: 250,
                        position: 'sticky', left: 0, zIndex: 3,
                        bgcolor: '#1e3a5f', color: '#fff', fontWeight: 700, fontSize: 12,
                        borderRight: '2px solid rgba(255,255,255,0.3)',
                        py: 0.75, px: 1, verticalAlign: 'middle',
                      }}>
                        Questions
                      </TableCell>
                      {pagedHrCands.map(cand => {
                        const notSuitable = cand.hrScreening?.decision === 'NOT_SUITABLE';
                        const headerBg    = notSuitable ? '#E60C09' : '#FDFF00';
                        const headerColor = notSuitable ? '#fff'    : '#1a1a1a';
                        return (
                          <TableCell key={cand.id} sx={{
                            width: 200, minWidth: 200,
                            bgcolor: headerBg,
                            borderRight: '1px solid rgba(0,0,0,0.1)',
                            py: 0.75, px: 1, verticalAlign: 'middle',
                          }}>
                            <Typography sx={{ fontSize: 12, fontWeight: 700, color: headerColor, lineHeight: 1.2 }}>
                              {cand.name}
                            </Typography>
                          </TableCell>
                        );
                      })}
                    </TableRow>
                  </TableHead>

                  <TableBody>
                    {/* ── Q1–Q11 rows — read-only ── */}
                    {[
                      { num: 1,  key: 'currentRoleResponsibilities', label: 'What is your current role and responsibilities?' },
                      { num: 2,  key: 'reasonForChange',             label: 'Why do you want to leave your current organisation?' },
                      { num: 3,  key: 'currentCtc',                  label: 'What is your current pay?' },
                      { num: 4,  key: 'expectedCtc',                 label: 'What do you expect from us? Is it negotiable?' },
                      { num: 5,  key: 'noticePeriod',                label: 'What is the duration of your notice period?' },
                      { num: 6,  key: 'preferredLocation',           label: 'Location' },
                      { num: 7,  key: 'workBase',                    label: 'What is your work base? (UK / US / India)' },
                      { num: 8,  key: 'totalExperience',             label: 'How many years of experience do you have?' },
                      { num: 9,  key: 'currentCompany',              label: 'Previous / Current Company' },
                      { num: 10, key: 'screeningDate',               label: 'Screening Date' },
                      { num: 11, key: 'communicationSkills',         label: 'Communication per HR (1-10)', isRating: true },
                    ].map(({ num, key, label, isRating }) => (
                      <TableRow key={key} sx={{
                        height: 34,
                        '&:nth-of-type(odd)':  { bgcolor: '#f8fafc' },
                        '&:nth-of-type(even)': { bgcolor: '#fff' },
                      }}>
                        {/* Question label — sticky left */}
                        <TableCell sx={{
                          position: 'sticky', left: 0, zIndex: 1, bgcolor: 'inherit',
                          borderRight: '2px solid #e2e8f0',
                          py: 0.5, px: 1, verticalAlign: 'middle',
                        }}>
                          <Box sx={{ display: 'flex', gap: 0.75, alignItems: 'center' }}>
                            <Box sx={{
                              minWidth: 18, width: 18, height: 18, borderRadius: '50%',
                              bgcolor: '#f59e0b', color: '#fff', flexShrink: 0,
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              fontSize: 9, fontWeight: 700,
                            }}>
                              {num}
                            </Box>
                            <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#1e293b', lineHeight: 1.3 }}>
                              {label}
                            </Typography>
                          </Box>
                        </TableCell>

                        {/* Read-only answer cells */}
                        {pagedHrCands.map(cand => {
                          const val = cand.hrScreening?.[key] || '';
                          const displayVal = key === 'screeningDate' && val ? fmtDate(val) : val;
                          return (
                            <TableCell key={cand.id} sx={{
                              borderRight: '1px solid #e2e8f0',
                              py: 0.5, px: 1, verticalAlign: 'middle',
                            }}>
                              {isRating ? (
                                val && Number(val) > 0 ? (
                                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                                    <Typography sx={{ fontSize: 12, fontWeight: 700, color: '#1e293b' }}>{val}</Typography>
                                    <Box sx={{ display: 'flex', gap: 0.25 }}>
                                      {[1,2,3,4,5,6,7,8,9,10].map(n => {
                                        const v = parseInt(val, 10) || 0;
                                        const col = v >= 8 ? '#16a34a' : v >= 5 ? '#ca8a04' : '#dc2626';
                                        return <Box key={n} sx={{ width: 9, height: 9, borderRadius: 0.25, bgcolor: n <= v ? col : '#e2e8f0' }} />;
                                      })}
                                    </Box>
                                  </Box>
                                ) : <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>—</Typography>
                              ) : displayVal ? (
                                <Typography sx={{ fontSize: 12, color: '#1e293b', lineHeight: 1.4, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                                  {displayVal}
                                </Typography>
                              ) : (
                                <Typography sx={{ fontSize: 12, color: '#94a3b8' }}>—</Typography>
                              )}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                    ))}

                    {/* ── Remarks row — read-only ── */}
                    <TableRow sx={{ bgcolor: '#f0f9ff', height: 34 }}>
                      <TableCell sx={{ position: 'sticky', left: 0, zIndex: 1, bgcolor: '#f0f9ff', borderRight: '2px solid #e2e8f0', py: 0.5, px: 1, verticalAlign: 'middle' }}>
                        <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#0369a1' }}>Remarks / Notes</Typography>
                      </TableCell>
                      {pagedHrCands.map(cand => {
                        const val = cand.hrScreening?.hrComments || '';
                        return (
                          <TableCell key={cand.id} sx={{ borderRight: '1px solid #e2e8f0', bgcolor: '#f0f9ff', py: 0.5, px: 1, verticalAlign: 'middle' }}>
                            <Typography sx={{ fontSize: 12, color: val ? '#0369a1' : '#94a3b8', lineHeight: 1.4, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                              {val || '—'}
                            </Typography>
                          </TableCell>
                        );
                      })}
                    </TableRow>

                    {/* ── Rejection Reason row — read-only ── */}
                    <TableRow sx={{ bgcolor: '#fff5f5', height: 34 }}>
                      <TableCell sx={{ position: 'sticky', left: 0, zIndex: 1, bgcolor: '#fff5f5', borderRight: '2px solid #e2e8f0', py: 0.5, px: 1, verticalAlign: 'middle' }}>
                        <Typography sx={{ fontSize: 12, fontWeight: 600, color: '#dc2626' }}>Rejection Reason</Typography>
                      </TableCell>
                      {pagedHrCands.map(cand => {
                        const val = cand.hrScreening?.rejectionReason || '';
                        return (
                          <TableCell key={cand.id} sx={{ borderRight: '1px solid #e2e8f0', bgcolor: '#fff5f5', py: 0.5, px: 1, verticalAlign: 'middle' }}>
                            <Typography sx={{ fontSize: 12, color: val ? '#dc2626' : '#94a3b8', lineHeight: 1.4, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                              {val || '—'}
                            </Typography>
                          </TableCell>
                        );
                      })}
                    </TableRow>

                    {/* ── Status row ── */}
                    <TableRow sx={{ bgcolor: '#f1f5f9', height: 36 }}>
                      <TableCell sx={{ position: 'sticky', left: 0, zIndex: 1, bgcolor: '#f1f5f9', borderRight: '2px solid #e2e8f0', fontWeight: 700, fontSize: 12, color: '#475569', py: 0.5, px: 1, verticalAlign: 'middle' }}>
                        Status
                      </TableCell>
                      {pagedHrCands.map(cand => {
                        const decision = cand.hrScreening?.decision;
                        const cfg = STATUS_CFG[cand.status] || { label: cand.status, bg: '#f1f5f9', color: '#475569' };
                        return (
                          <TableCell key={cand.id} sx={{ borderRight: '1px solid #e2e8f0', py: 0.5, px: 1, verticalAlign: 'middle' }}>
                            {decision && decision !== 'PENDING' ? (
                              <Chip label={decision === 'SUITABLE' ? 'Suitable ✓' : 'Not Suitable ✗'} size="small"
                                sx={{ height: 22, fontSize: 11, fontWeight: 700,
                                  bgcolor: decision === 'SUITABLE' ? '#dcfce7' : '#fee2e2',
                                  color:   decision === 'SUITABLE' ? '#16a34a' : '#dc2626',
                                  '& .MuiChip-label': { px: 1 } }} />
                            ) : (
                              <Chip label={cfg.label} size="small"
                                sx={{ height: 22, fontSize: 11, fontWeight: 600,
                                  bgcolor: cfg.bg, color: cfg.color,
                                  '& .MuiChip-label': { px: 1 } }} />
                            )}
                          </TableCell>
                        );
                      })}
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination component="div" count={hrCands.length} page={hrPage}
                onPageChange={(_, p) => setHrPage(p)} rowsPerPage={HR_RPP} rowsPerPageOptions={[HR_RPP]} />
            </Card>
          )}
        </Box>
      )}

      {/* ═══ TECHNICAL TAB ══════════════════════════════════════════════════ */}
      {tab === 'technical' && (
        <Box>
          {/* Admin/HR view */}
          {canManage && (
            techCands.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 8 }}>
                <WorkIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 1 }} />
                <Typography color="text.secondary" variant="h6">No candidates pending technical interviews</Typography>
              </Box>
            ) : (
              <CandidateTable rows={techCands} emptyMsg="No technical pending" />
            )
          )}

          {/* Manager view */}
          {!canManage && isManager && (
            myAssignments.length === 0 ? (
              <Box sx={{ textAlign: 'center', py: 8 }}>
                <WorkIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 1 }} />
                <Typography color="text.secondary" variant="h6">No interviews assigned to you</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                  HR will assign candidates when they are deemed suitable for technical evaluation
                </Typography>
              </Box>
            ) : (
              <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                        {['Candidate', 'Applied Profile', 'Scheduled', 'Evaluated On', 'Assigned By', 'Interview Status', 'Score', 'Action'].map(h => (
                          <TableCell key={h} sx={{ fontWeight: 700, fontSize: 12, color: '#475569', py: 1.5 }}>{h}</TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {myAssignments.map((t, i) => {
                        const ivStatus = t.interviewStatus || 'PENDING_LINK';
                        const IV_BADGE = {
                          PENDING_LINK:        { label: 'Link Not Generated', bg: '#f1f5f9', color: '#475569' },
                          LINK_GENERATED:      { label: 'Waiting Candidate',  bg: '#dbeafe', color: '#1d4ed8' },
                          IN_PROGRESS:         { label: '🔴 In Progress',     bg: '#fef3c7', color: '#d97706' },
                          CANDIDATE_SUBMITTED: { label: 'Submitted',          bg: '#dcfce7', color: '#16a34a' },
                          EVALUATED:           { label: 'Evaluated',          bg: '#f3e8ff', color: '#7c3aed' },
                        };
                        const badge = IV_BADGE[ivStatus] || IV_BADGE.PENDING_LINK;
                        return (
                        <TableRow key={t.id} sx={{ bgcolor: i % 2 === 0 ? '#fff' : '#f8fafc' }}>
                          <TableCell><Typography variant="body2" fontWeight={600}>{t.candidateName || '—'}</Typography></TableCell>
                          <TableCell><Typography variant="body2">{t.candidateAppliedProfile || '—'}</Typography></TableCell>
                          <TableCell sx={{ fontSize: 12, whiteSpace: 'nowrap', color: '#64748b' }}>{fmtDt(t.scheduledAt)}</TableCell>
                          <TableCell sx={{ fontSize: 12, whiteSpace: 'nowrap', color: t.evaluatedAt ? '#7c3aed' : '#94a3b8' }}>
                            {t.evaluatedAt ? fmtDt(t.evaluatedAt) : '—'}
                          </TableCell>
                          <TableCell><Typography variant="caption">{t.assignedByName || '—'}</Typography></TableCell>
                          <TableCell>
                            <Chip label={badge.label} size="small"
                              sx={{ bgcolor: badge.bg, color: badge.color, fontWeight: 700, fontSize: 11 }} />
                          </TableCell>
                          <TableCell>
                            {t.score != null
                              ? <Typography variant="caption" fontWeight={700}>{t.score}/{t.totalMarks}</Typography>
                              : <Typography variant="caption" color="text.disabled">—</Typography>}
                          </TableCell>
                          <TableCell>
                            <Stack direction="row" spacing={0.75}>
                              {/* Generate link */}
                              {ivStatus === 'PENDING_LINK' && (
                                <Button size="small" variant="outlined" onClick={() => openGenerateLinkDialog(t)}
                                  sx={{ textTransform: 'none', fontSize: 11, borderColor: '#6366f1', color: '#6366f1' }}>
                                  Generate Link
                                </Button>
                              )}
                              {/* Join room */}
                              {(ivStatus === 'IN_PROGRESS' || ivStatus === 'CANDIDATE_SUBMITTED') && (
                                <Button size="small" variant="contained"
                                  onClick={() => window.open(`/interview/room/${t.id}`, '_blank')}
                                  sx={{ textTransform: 'none', fontSize: 11, bgcolor: '#059669', '&:hover': { bgcolor: '#047857' } }}>
                                  {ivStatus === 'IN_PROGRESS' ? 'Join Room' : 'Review'}
                                </Button>
                              )}
                              {/* Legacy feedback */}
                              {(ivStatus === 'PENDING_LINK' || ivStatus === 'EVALUATED') && (
                                <Button size="small"
                                  variant={(!t.decision || t.decision === 'PENDING') ? 'contained' : 'outlined'}
                                  onClick={() => openTechFeedback(t)}
                                  sx={{ textTransform: 'none', fontSize: 11,
                                        ...(!t.decision || t.decision === 'PENDING'
                                          ? { bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }
                                          : {}) }}>
                                  {(!t.decision || t.decision === 'PENDING') ? 'Feedback' : 'View'}
                                </Button>
                              )}
                            </Stack>
                          </TableCell>
                        </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Card>
            )
          )}
        </Box>
      )}

      {/* ═══ FINAL ROUND TAB ════════════════════════════════════════════════ */}
      {tab === 'final' && canManage && (
        <Box>
          {finalCands.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 8 }}>
              <EmojiEventsIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 1 }} />
              <Typography color="text.secondary" variant="h6">No candidates in Final Round</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Candidates appear here after clearing the technical interview
              </Typography>
            </Box>
          ) : (
            <CandidateTable rows={finalCands} emptyMsg="No final round candidates" />
          )}
        </Box>
      )}

      {renderDialogs()}
    </Box>
  );

  /* ══════════════════════════════════════════════════════════════════════════ */
  /* DIALOGS                                                                    */
  /* ══════════════════════════════════════════════════════════════════════════ */
  function renderDialogs() {
    return (
      <>
        {/* ── Upload CV ── */}
        <Dialog open={uploadDialog} onClose={() => !uploadSaving && !uploadStoring && closeUploadDialog()}
          maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ fontWeight: 700, pb: 0.5 }}>
            Upload Candidate CV
            <Typography variant="body2" color="text.secondary" fontWeight={400} sx={{ mt: 0.25 }}>
              Select a PDF / DOC / DOCX — the file is saved immediately and details are auto-extracted
            </Typography>
          </DialogTitle>
          <DialogContent>
            <Box sx={{ mt: 1.5 }}>
              {/* ── Drop zone ── */}
              <Paper variant="outlined"
                onClick={() => !uploadStoring && fileInputRef.current?.click()}
                sx={{ p: 2.5, borderRadius: 2, borderStyle: 'dashed', textAlign: 'center', mb: 2,
                      cursor: uploadStoring ? 'not-allowed' : 'pointer',
                      borderColor: storedResume ? '#16a34a' : '#cbd5e1',
                      bgcolor:     storedResume ? '#f0fdf4'  : 'transparent',
                      '&:hover': !uploadStoring ? { borderColor: '#1e3a5f', bgcolor: '#f8fafc' } : {} }}>
                <input ref={fileInputRef} type="file" accept=".pdf,.doc,.docx" hidden
                  onChange={e => handleFileSelect(e.target.files?.[0])} />

                {uploadStoring ? (
                  <Box>
                    <CircularProgress size={22} sx={{ color: '#1e3a5f', mb: 0.75 }} />
                    <Typography variant="body2" fontWeight={600} color="text.secondary">
                      Storing CV on server…
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Saving file and extracting details
                    </Typography>
                  </Box>
                ) : storedResume ? (
                  <Box>
                    <CheckCircleIcon sx={{ color: '#16a34a', fontSize: 32, mb: 0.5 }} />
                    <Typography variant="body2" fontWeight={700} color="#16a34a">
                      CV stored on server
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                      {uploadFile?.name}
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#16a34a', mt: 0.25, display: 'block' }}>
                      Click to replace
                    </Typography>
                  </Box>
                ) : uploadFile ? (
                  <Box>
                    <CircularProgress size={22} sx={{ color: '#94a3b8', mb: 0.75 }} />
                    <Typography variant="body2" fontWeight={600}>{uploadFile.name}</Typography>
                  </Box>
                ) : (
                  <Box>
                    <CloudUploadIcon sx={{ color: '#94a3b8', fontSize: 36, mb: 0.5 }} />
                    <Typography variant="body2" fontWeight={700}>Click to select CV</Typography>
                    <Typography variant="caption" color="text.secondary">
                      PDF, DOC, DOCX · max 10 MB · stored instantly on selection
                    </Typography>
                  </Box>
                )}
              </Paper>
              {/* ── Step indicator ── */}
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  <Box sx={{ width: 22, height: 22, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    bgcolor: storedResume ? '#16a34a' : '#1e3a5f', color: '#fff', fontSize: 11, fontWeight: 700 }}>
                    {storedResume ? '✓' : '1'}
                  </Box>
                  <Typography variant="caption" fontWeight={600} color={storedResume ? '#16a34a' : 'text.primary'}>
                    Store CV
                  </Typography>
                </Box>
                <Box sx={{ flex: 1, height: 1, bgcolor: storedResume ? '#16a34a' : '#e2e8f0' }} />
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  <Box sx={{ width: 22, height: 22, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    bgcolor: storedResume ? '#1e3a5f' : '#e2e8f0', color: storedResume ? '#fff' : '#94a3b8', fontSize: 11, fontWeight: 700 }}>
                    2
                  </Box>
                  <Typography variant="caption" fontWeight={600} color={storedResume ? 'text.primary' : 'text.disabled'}>
                    Confirm Details
                  </Typography>
                </Box>
              </Box>

              {/* ── Form fields (disabled until CV is stored) ── */}
              <Grid container spacing={2}
                sx={{ opacity: storedResume ? 1 : 0.45, pointerEvents: storedResume ? 'auto' : 'none',
                      transition: 'opacity 0.2s' }}>
                <Grid item xs={12}>
                  <TextField label="Full Name *" value={uploadForm.name} fullWidth size="small"
                    onChange={e => setUploadForm(f => ({ ...f, name: e.target.value }))} />
                </Grid>
                <Grid item xs={12}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel>Applied Profile / Role *</InputLabel>
                      <Select
                        value={uploadForm.appliedProfile}
                        label="Applied Profile / Role *"
                        onChange={e => setUploadForm(f => ({ ...f, appliedProfile: e.target.value }))}>
                        {allProfiles.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
                      </Select>
                    </FormControl>
                    <Tooltip title="Add new profile">
                      <IconButton size="small" onClick={e => { setNewProfileInput(''); setProfileAnchor(e.currentTarget); }}
                        sx={{ border: '1px solid #e2e8f0', borderRadius: 1.5, p: 0.75, color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                        <TuneIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Email" value={uploadForm.email} fullWidth size="small" type="email"
                    onChange={e => setUploadForm(f => ({ ...f, email: e.target.value }))} />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Phone / Contact" value={uploadForm.phone} fullWidth size="small"
                    onChange={e => setUploadForm(f => ({ ...f, phone: e.target.value }))} />
                </Grid>
                <Grid item xs={6}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <TextField label="Office Location *" select value={uploadForm.officeLocation} fullWidth size="small"
                      onChange={e => setUploadForm(f => ({ ...f, officeLocation: e.target.value }))}>
                      <MenuItem value=""><em>Select location</em></MenuItem>
                      {allLocations.map(l => <MenuItem key={l} value={l}>{l}</MenuItem>)}
                    </TextField>
                    <Tooltip title="Add new location">
                      <IconButton size="small" onClick={e => { setNewLocationInput(''); setLocationAnchor(e.currentTarget); }}
                        sx={{ border: '1px solid #e2e8f0', borderRadius: 1.5, p: 0.75, color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                        <TuneIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </Grid>
                <Grid item xs={6}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <TextField label="Source" select value={uploadForm.source} fullWidth size="small"
                      onChange={e => setUploadForm(f => ({ ...f, source: e.target.value }))}>
                      <MenuItem value=""><em>Select source</em></MenuItem>
                      {allSources.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                    </TextField>
                    <Tooltip title="Add new source">
                      <IconButton size="small" onClick={e => { setNewSourceInput(''); setSourceAnchor(e.currentTarget); }}
                        sx={{ border: '1px solid #e2e8f0', borderRadius: 1.5, p: 0.75, color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                        <TuneIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </Grid>
                <Grid item xs={12}>
                  <TextField label="Full Address (from CV)" value={uploadForm.address} fullWidth size="small" multiline rows={2}
                    onChange={e => setUploadForm(f => ({ ...f, address: e.target.value }))}
                    InputProps={{ sx: { fontSize: 13 } }}
                    helperText="Auto-extracted from CV — edit if needed" />
                </Grid>
                <Grid item xs={12}>
                  <Divider sx={{ my: 0.25 }}>
                    <Typography variant="caption" color="text.secondary" fontWeight={600} sx={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: 0.5 }}>
                      Address Breakdown
                    </Typography>
                  </Divider>
                </Grid>
                <Grid item xs={12}>
                  <TextField label="Street / House No." value={uploadForm.addressStreet} fullWidth size="small"
                    placeholder="e.g. H.No. 12, Green Colony"
                    onChange={e => setUploadForm(f => ({ ...f, addressStreet: e.target.value }))} />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Area / Locality" value={uploadForm.addressArea} fullWidth size="small"
                    placeholder="e.g. Sector 5, MG Road"
                    onChange={e => setUploadForm(f => ({ ...f, addressArea: e.target.value }))} />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Landmark" value={uploadForm.addressLandmark} fullWidth size="small"
                    placeholder="e.g. Near SBI Bank"
                    onChange={e => setUploadForm(f => ({ ...f, addressLandmark: e.target.value }))} />
                </Grid>
                <Grid item xs={5}>
                  <TextField label="City" value={uploadForm.addressCity} fullWidth size="small"
                    onChange={e => setUploadForm(f => ({ ...f, addressCity: e.target.value }))} />
                </Grid>
                <Grid item xs={4}>
                  <TextField label="District" value={uploadForm.addressDistrict} fullWidth size="small"
                    onChange={e => setUploadForm(f => ({ ...f, addressDistrict: e.target.value }))} />
                </Grid>
                <Grid item xs={3}>
                  <TextField label="PIN Code" value={uploadForm.addressPostalCode} fullWidth size="small"
                    onChange={e => setUploadForm(f => ({ ...f, addressPostalCode: e.target.value }))} />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="State" value={uploadForm.addressState} fullWidth size="small"
                    onChange={e => setUploadForm(f => ({ ...f, addressState: e.target.value }))} />
                </Grid>
                <Grid item xs={6}>
                  <TextField label="Country" value={uploadForm.addressCountry} fullWidth size="small"
                    placeholder="India"
                    onChange={e => setUploadForm(f => ({ ...f, addressCountry: e.target.value }))} />
                </Grid>

              </Grid>
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2, gap: 1 }}>
            <Button onClick={closeUploadDialog} disabled={uploadSaving || uploadStoring}>Cancel</Button>
            {!storedResume ? (
              <Button variant="contained" disabled
                sx={{ bgcolor: '#94a3b8', textTransform: 'none', minWidth: 160 }}>
                {uploadStoring ? 'Storing CV…' : 'Select a CV first'}
              </Button>
            ) : (
              <Button variant="contained" onClick={handleUploadSubmit}
                disabled={uploadSaving
                  || !uploadForm.name.trim()
                  || !uploadForm.appliedProfile.trim()
                  || !uploadForm.officeLocation}
                sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none', minWidth: 160 }}>
                {uploadSaving ? 'Adding to CV Bank…' : 'Add to CV Bank'}
              </Button>
            )}
          </DialogActions>
        </Dialog>

        {/* ── Duplicate Resume Warning ── */}
        <Dialog open={dupDialog} maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5, pb: 1 }}>
            <WarningAmberIcon sx={{ color: '#d97706', fontSize: 28 }} />
            <Box>
              <Typography variant="h6" fontWeight={700} sx={{ fontSize: 17, lineHeight: 1.2 }}>
                Resume Already Exists
              </Typography>
              <Typography variant="body2" color="text.secondary" fontWeight={400} sx={{ fontSize: 12 }}>
                A CV for this candidate was previously uploaded
              </Typography>
            </Box>
          </DialogTitle>
          <DialogContent sx={{ pt: '4px !important' }}>
            {dupCandidate && (
              <Alert severity="warning" variant="outlined"
                icon={false}
                sx={{ mb: 2, borderRadius: 2, borderColor: '#fcd34d', bgcolor: '#fffbeb' }}>
                <Typography variant="body2" sx={{ fontSize: 13.5, lineHeight: 1.7 }}>
                  A resume for{' '}
                  <Box component="span" fontWeight={700}>{dupCandidate.name}</Box>
                  {' '}was previously uploaded on{' '}
                  <Box component="span" fontWeight={700}>
                    {fmtDate(dupCandidate.resumeUploadedAt || dupCandidate.createdAt)}
                  </Box>
                  {' '}
                  <Box component="span" color="text.secondary" sx={{ fontSize: 12 }}>
                    ({timeAgo(dupCandidate.resumeUploadedAt || dupCandidate.createdAt)})
                  </Box>
                  .
                </Typography>
              </Alert>
            )}
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Do you want to replace the existing resume with the newly uploaded version?
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ fontSize: 12 }}>
              The previous resume will be <strong>archived</strong> and the new resume will become the active CV.
              All parsed fields will be refreshed from the new document.
            </Typography>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
            <Button onClick={handleDupCancel} disabled={dupReplacing}
              sx={{ textTransform: 'none', color: '#475569' }}>
              Cancel
            </Button>
            <Button variant="contained" onClick={handleReplaceResume} disabled={dupReplacing}
              startIcon={dupReplacing ? <CircularProgress size={16} color="inherit" /> : null}
              sx={{ bgcolor: '#d97706', '&:hover': { bgcolor: '#b45309' }, textTransform: 'none', fontWeight: 700, minWidth: 160 }}>
              {dupReplacing ? 'Replacing…' : 'Replace Resume'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Edit Candidate ── */}
        <Dialog open={editDialog} onClose={() => setEditDialog(false)} maxWidth="sm" fullWidth
          PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle fontWeight={700}>Edit Candidate</DialogTitle>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 0.5 }}>
              <Grid item xs={12}>
                <TextField label="Full Name *" value={editForm.name || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, name: e.target.value }))} />
              </Grid>
              <Grid item xs={12}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <FormControl fullWidth size="small">
                    <InputLabel>Applied Profile *</InputLabel>
                    <Select
                      value={editForm.appliedProfile || ''}
                      label="Applied Profile *"
                      onChange={e => setEditForm(f => ({ ...f, appliedProfile: e.target.value }))}>
                      {allProfiles.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
                    </Select>
                  </FormControl>
                  <Tooltip title="Add new profile">
                    <IconButton size="small" onClick={e => { setNewProfileInput(''); setProfileAnchor(e.currentTarget); }}
                      sx={{ border: '1px solid #e2e8f0', borderRadius: 1.5, p: 0.75, color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                      <TuneIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Grid>
              <Grid item xs={6}>
                <TextField label="Email" value={editForm.email || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, email: e.target.value }))} />
              </Grid>
              <Grid item xs={6}>
                <TextField label="Phone" value={editForm.phone || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, phone: e.target.value }))} />
              </Grid>
              <Grid item xs={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <TextField label="Office Location" select value={editForm.officeLocation || ''} fullWidth size="small"
                    onChange={e => setEditForm(f => ({ ...f, officeLocation: e.target.value }))}>
                    <MenuItem value=""><em>Select</em></MenuItem>
                    {allLocations.map(l => <MenuItem key={l} value={l}>{l}</MenuItem>)}
                  </TextField>
                  <Tooltip title="Add new location">
                    <IconButton size="small" onClick={e => { setNewLocationInput(''); setLocationAnchor(e.currentTarget); }}
                      sx={{ border: '1px solid #e2e8f0', borderRadius: 1.5, p: 0.75, color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                      <TuneIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Grid>
              <Grid item xs={6}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <TextField label="Source" select value={editForm.source || ''} fullWidth size="small"
                    onChange={e => setEditForm(f => ({ ...f, source: e.target.value }))}>
                    <MenuItem value=""><em>Select</em></MenuItem>
                    {allSources.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
                  </TextField>
                  <Tooltip title="Add new source">
                    <IconButton size="small" onClick={e => { setNewSourceInput(''); setSourceAnchor(e.currentTarget); }}
                      sx={{ border: '1px solid #e2e8f0', borderRadius: 1.5, p: 0.75, color: '#475569', '&:hover': { bgcolor: '#f1f5f9' } }}>
                      <TuneIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Grid>
              <Grid item xs={12}>
                <TextField label="Full Address" value={editForm.address || ''} fullWidth size="small" multiline rows={2}
                  onChange={e => setEditForm(f => ({ ...f, address: e.target.value }))} />
              </Grid>

              {/* ── Parsed / enriched fields ── */}
              <Grid item xs={12}>
                <Divider sx={{ my: 0.5 }}>
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>Resume Insights</Typography>
                </Divider>
              </Grid>
              <Grid item xs={6}>
                <TextField label="Total Experience" value={editForm.totalExperienceYears || ''} fullWidth size="small"
                  placeholder="e.g. 5 years"
                  onChange={e => setEditForm(f => ({ ...f, totalExperienceYears: e.target.value }))} />
              </Grid>
              <Grid item xs={6}>
                <TextField label="Current Designation" value={editForm.currentDesignation || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, currentDesignation: e.target.value }))} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Current Company (from CV)" value={editForm.currentCompanyCv || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, currentCompanyCv: e.target.value }))} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Education Summary" value={editForm.educationSummary || ''} fullWidth size="small"
                  multiline rows={2}
                  onChange={e => setEditForm(f => ({ ...f, educationSummary: e.target.value }))} />
              </Grid>
              <Grid item xs={6}>
                <TextField label="LinkedIn URL" value={editForm.linkedinUrl || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, linkedinUrl: e.target.value }))} />
              </Grid>
              <Grid item xs={6}>
                <TextField label="GitHub URL" value={editForm.githubUrl || ''} fullWidth size="small"
                  onChange={e => setEditForm(f => ({ ...f, githubUrl: e.target.value }))} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Skills (comma-separated)" value={editForm.skills || ''} fullWidth size="small"
                  multiline rows={2} placeholder="Java, Spring Boot, React, MySQL…"
                  onChange={e => setEditForm(f => ({ ...f, skills: e.target.value }))} />
                {editForm.skills && (
                  <Box sx={{ mt: 1 }}>
                    <SkillChips skills={editForm.skills} max={20} />
                  </Box>
                )}
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setEditDialog(false)}>Cancel</Button>
            <Button variant="contained" onClick={handleEditSave}
              disabled={editSaving || !editForm.name?.trim() || !editForm.appliedProfile?.trim()}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
              {editSaving ? 'Saving…' : 'Save Changes'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Assign Technical Interview ── */}
        <Dialog open={assignDialog} onClose={() => !assigning && setAssignDialog(false)}
          maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ fontWeight: 700 }}>
            Assign Technical Interview
            {selectedCand && (
              <Typography variant="body2" color="text.secondary" fontWeight={400}>
                {selectedCand.name} — {selectedCand.appliedProfile}
              </Typography>
            )}
          </DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
              <TextField label="Assign To (Technical Interviewer) *" select fullWidth size="small"
                value={assignForm.interviewerId}
                onChange={e => setAssignForm(f => ({ ...f, interviewerId: e.target.value }))}>
                <MenuItem value=""><em>— Select interviewer —</em></MenuItem>
                {allEmployees
                  .filter(e => e.active !== false && ['MANAGER', 'ASSISTANT_MANAGER', 'ADMIN', 'DIRECTOR'].includes(e.role))
                  .map(e => (
                    <MenuItem key={e.id} value={String(e.id)}>
                      {e.firstName} {e.lastName} — {e.role?.replace('_', ' ')}
                    </MenuItem>
                  ))}
              </TextField>
              <AppDateTimePicker
                label="Interview Date & Time"
                value={assignForm.scheduledAt}
                onChange={(val) => setAssignForm(f => ({ ...f, scheduledAt: val }))}
              />
              <Alert severity="info" sx={{ fontSize: 12 }}>
                The interviewer will receive an email notification. The candidate status will move to <strong>Technical Pending</strong>.
              </Alert>
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setAssignDialog(false)} disabled={assigning}>Cancel</Button>
            <Button variant="contained" onClick={handleAssignTechnical}
              disabled={assigning || !assignForm.interviewerId}
              sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' } }}>
              {assigning ? 'Assigning…' : 'Confirm — Mark Suitable'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Generate Interview Link ── */}
        <Dialog open={linkDialog} onClose={() => !linkGenerating && setLinkDialog(false)}
          maxWidth="sm" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ fontWeight: 700 }}>Generate Video Interview Link</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <TextField fullWidth size="small" label="Technology / Topic"
                value={linkForm.technology}
                onChange={e => setLinkForm(f => ({ ...f, technology: e.target.value }))}
                placeholder="e.g. React, Java, Accounting, General" />
              <FormControl fullWidth size="small">
                <InputLabel>Question Difficulty</InputLabel>
                <Select value={linkForm.difficulty} label="Question Difficulty"
                  onChange={e => setLinkForm(f => ({ ...f, difficulty: e.target.value }))}>
                  {DIFF_OPTIONS.map(d => <MenuItem key={d} value={d}>{d.charAt(0) + d.slice(1).toLowerCase()}</MenuItem>)}
                </Select>
              </FormControl>
              <TextField fullWidth size="small" label="Number of Questions (5–40)"
                type="number" inputProps={{ min: 5, max: 40 }}
                value={linkForm.questionCount}
                onChange={e => setLinkForm(f => ({ ...f, questionCount: Number(e.target.value) }))} />
              <Alert severity="info" sx={{ py: 0.5 }}>
                Questions are randomly selected from the Question Bank for the chosen technology and difficulty — every candidate gets a unique shuffle. The candidate gets a 45-minute timer.
              </Alert>
              {generatedLink && (
                <Box sx={{ p: 2, bgcolor: '#f0fdf4', borderRadius: 2, border: '1px solid #86efac' }}>
                  <Typography variant="caption" fontWeight={700} color="success.main" display="block" mb={0.5}>
                    ✅ Interview link generated & emailed to candidate
                  </Typography>
                  <Typography variant="caption" sx={{ wordBreak: 'break-all', color: '#1d4ed8' }}>
                    {generatedLink}
                  </Typography>
                  <Button size="small" onClick={() => { navigator.clipboard.writeText(generatedLink); toast.success('Copied!'); }}
                    sx={{ mt: 1, textTransform: 'none', fontSize: 11 }}>Copy Link</Button>
                </Box>
              )}
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={() => setLinkDialog(false)} disabled={linkGenerating}>Close</Button>
            <Button variant="contained" onClick={handleGenerateLink} disabled={linkGenerating || !linkForm.technology.trim()}
              sx={{ bgcolor: '#6366f1', '&:hover': { bgcolor: '#4f46e5' }, textTransform: 'none', fontWeight: 700 }}>
              {linkGenerating ? 'Generating…' : generatedLink ? 'Regenerate Link' : 'Generate & Send Email'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Assign to Director (HR/Admin) ── */}
        <Dialog open={assignDirectorDialog} onClose={() => !assigningDirector && setAssignDirectorDialog(false)}
          maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ fontWeight: 700 }}>Assign to Director</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info" sx={{ fontSize: 12 }}>
                The candidate has cleared the Technical Interview. Pick the Director who will conduct
                the Final Round — they'll be notified by email and can then schedule the interview
                and generate the candidate's secure interview link.
              </Alert>
              <TextField fullWidth size="small" label="Director *"
                select SelectProps={{ native: true }}
                InputLabelProps={{ shrink: true }}
                value={assignDirectorForm.directorId}
                onChange={e => setAssignDirectorForm(f => ({ ...f, directorId: e.target.value }))}>
                <option value="">— Select Director —</option>
                {directors.map(e => (
                  <option key={e.id} value={e.id}>{e.firstName} {e.lastName}</option>
                ))}
              </TextField>
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={() => setAssignDirectorDialog(false)} disabled={assigningDirector}>Close</Button>
            <Button variant="contained" onClick={handleAssignFinalDirector}
              disabled={assigningDirector || !assignDirectorForm.directorId}
              sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' }, textTransform: 'none', fontWeight: 700 }}>
              {assigningDirector ? 'Assigning…' : 'Assign & Notify Director'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Schedule & Generate Final Interview Link (Director) ── */}
        <Dialog open={finalLinkDialog} onClose={() => !finalLinkGenerating && setFinalLinkDialog(false)}
          maxWidth="xs" fullWidth PaperProps={{ sx: { borderRadius: 3 } }}>
          <DialogTitle sx={{ fontWeight: 700 }}>Schedule Final Interview</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Alert severity="info" sx={{ fontSize: 12 }}>
                A secure video interview link will be generated and emailed to the candidate along
                with the scheduled time. You can join the room at <strong>/interview/final-room/:id</strong>.
              </Alert>
              <AppDateTimePicker
                label="Interview Date & Time"
                value={finalLinkForm.scheduledAt}
                onChange={(val) => setFinalLinkForm(f => ({ ...f, scheduledAt: val }))}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, py: 2 }}>
            <Button onClick={() => setFinalLinkDialog(false)} disabled={finalLinkGenerating}>Close</Button>
            <Button variant="contained" onClick={handleGenerateFinalLink} disabled={finalLinkGenerating}
              sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' }, textTransform: 'none', fontWeight: 700 }}>
              {finalLinkGenerating ? 'Generating…' : 'Schedule & Send Email'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Technical Feedback ── */}
        <Dialog open={techDialog} onClose={() => !techSaving && setTechDialog(false)}
          maxWidth="md" fullWidth PaperProps={{ sx: { borderRadius: 3, maxHeight: '90vh' } }}>
          <DialogTitle sx={{ fontWeight: 700 }}>
            Technical Interview Evaluation
            {selTech && (
              <Typography variant="body2" color="text.secondary" fontWeight={400}>
                {selTech.candidateName} — {selTech.candidateAppliedProfile}
                {selTech.scheduledAt && ` · ${fmtDt(selTech.scheduledAt)}`}
              </Typography>
            )}
          </DialogTitle>
          <DialogContent>
            <Box sx={{ mt: 1 }}>
              <Typography variant="subtitle2" fontWeight={700} mb={2}>Skill Ratings (1–5 Stars)</Typography>
              <Grid container spacing={2} sx={{ mb: 2 }}>
                {[
                  ['technicalSkillsRating',      'Technical Skills'],
                  ['communicationRating',          'Communication'],
                  ['problemSolvingRating',         'Problem Solving'],
                  ['codingAbilityRating',          'Coding Ability'],
                  ['architectureKnowledgeRating',  'Architecture Knowledge'],
                ].map(([field, label]) => (
                  <Grid item xs={12} sm={6} key={field}>
                    <Typography variant="caption" color="text.secondary" fontWeight={600}>{label}</Typography>
                    <Rating value={techForm[field] || 0} size="large"
                      onChange={(_, v) => setTechForm(f => ({ ...f, [field]: v }))}
                      sx={{ display: 'flex', mt: 0.5 }} />
                  </Grid>
                ))}
              </Grid>
              <Divider sx={{ mb: 2 }} />
              <TextField label="Comments / Observations" fullWidth multiline rows={3} size="small"
                value={techForm.comments}
                onChange={e => setTechForm(f => ({ ...f, comments: e.target.value }))}
                placeholder="Key technical strengths, areas of concern, specific observations…"
                sx={{ mb: 2 }} />
              <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>Final Decision *</Typography>
              <Stack direction="row" spacing={2}>
                <Button fullWidth
                  variant={techForm.decision === 'APPROVE' ? 'contained' : 'outlined'}
                  startIcon={<CheckCircleIcon />}
                  onClick={() => setTechForm(f => ({ ...f, decision: 'APPROVE' }))}
                  sx={techForm.decision === 'APPROVE'
                    ? { bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', fontWeight: 700 }
                    : { borderColor: '#16a34a', color: '#16a34a', textTransform: 'none', fontWeight: 600 }}>
                  Approve — Move to Final Round
                </Button>
                <Button fullWidth
                  variant={techForm.decision === 'REJECT' ? 'contained' : 'outlined'}
                  startIcon={<CancelIcon />} color="error"
                  onClick={() => setTechForm(f => ({ ...f, decision: 'REJECT' }))}
                  sx={{ textTransform: 'none', fontWeight: techForm.decision === 'REJECT' ? 700 : 600 }}>
                  Reject — Send Rejection Email
                </Button>
              </Stack>
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setTechDialog(false)} disabled={techSaving}>Cancel</Button>
            <Button variant="contained" onClick={handleTechFeedbackSubmit} disabled={techSaving || !techForm.decision}
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
              {techSaving ? 'Submitting…' : 'Submit Evaluation'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* ── Add New Source Popover ── */}
        <Popover
          open={Boolean(sourceAnchor)}
          anchorEl={sourceAnchor}
          onClose={() => setSourceAnchor(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          PaperProps={{ sx: { borderRadius: 2, p: 2, width: 280, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' } }}>
          <Typography variant="subtitle2" fontWeight={700} mb={1.5}>Add New Source</Typography>
          <TextField
            autoFocus size="small" fullWidth
            label="Source name"
            value={newSourceInput}
            onChange={e => setNewSourceInput(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                const v = newSourceInput.trim();
                if (v && !allSources.includes(v)) setExtraSources(s => [...s, v]);
                setSourceAnchor(null);
              }
            }} />
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 1.5 }}>
            <Button size="small" onClick={() => setSourceAnchor(null)}>Cancel</Button>
            <Button size="small" variant="contained"
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}
              disabled={!newSourceInput.trim() || allSources.includes(newSourceInput.trim())}
              onClick={() => {
                const v = newSourceInput.trim();
                if (v) setExtraSources(s => [...s, v]);
                setSourceAnchor(null);
              }}>
              Add
            </Button>
          </Box>
        </Popover>

        {/* ── Add New Location Popover ── */}
        <Popover
          open={Boolean(locationAnchor)}
          anchorEl={locationAnchor}
          onClose={() => setLocationAnchor(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          PaperProps={{ sx: { borderRadius: 2, p: 2, width: 280, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' } }}>
          <Typography variant="subtitle2" fontWeight={700} mb={1.5}>Add New Location</Typography>
          <TextField
            autoFocus size="small" fullWidth
            label="Location name"
            value={newLocationInput}
            onChange={e => setNewLocationInput(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                const v = newLocationInput.trim();
                if (v && !allLocations.includes(v)) setExtraLocations(l => [...l, v]);
                setLocationAnchor(null);
              }
            }} />
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 1.5 }}>
            <Button size="small" onClick={() => setLocationAnchor(null)}>Cancel</Button>
            <Button size="small" variant="contained"
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}
              disabled={!newLocationInput.trim() || allLocations.includes(newLocationInput.trim())}
              onClick={() => {
                const v = newLocationInput.trim();
                if (v) setExtraLocations(l => [...l, v]);
                setLocationAnchor(null);
              }}>
              Add
            </Button>
          </Box>
        </Popover>

        {/* ── Add New Profile Popover ── */}
        <Popover
          open={Boolean(profileAnchor)}
          anchorEl={profileAnchor}
          onClose={() => setProfileAnchor(null)}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          PaperProps={{ sx: { borderRadius: 2, p: 2, width: 280, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' } }}>
          <Typography variant="subtitle2" fontWeight={700} mb={1.5}>Add New Profile</Typography>
          <TextField
            autoFocus size="small" fullWidth
            label="Profile name"
            value={newProfileInput}
            onChange={e => setNewProfileInput(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                const v = newProfileInput.trim();
                if (v && !allProfiles.includes(v)) setExtraProfiles(p => [...p, v]);
                setProfileAnchor(null);
              }
            }} />
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 1.5 }}>
            <Button size="small" onClick={() => setProfileAnchor(null)}>Cancel</Button>
            <Button size="small" variant="contained"
              sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}
              disabled={!newProfileInput.trim() || allProfiles.includes(newProfileInput.trim())}
              onClick={() => {
                const v = newProfileInput.trim();
                if (v) setExtraProfiles(p => [...p, v]);
                setProfileAnchor(null);
              }}>
              Add
            </Button>
          </Box>
        </Popover>
      </>
    );
  }
};

export default ATSPage;
