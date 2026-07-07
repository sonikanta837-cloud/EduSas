import React, { useEffect, useState, useMemo } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, CardActions, Typography, Button,
  Chip, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  CircularProgress, IconButton, Tooltip, Divider, Radio, RadioGroup,
  FormControlLabel, FormControl, FormLabel, LinearProgress,
  Autocomplete, Stack, InputAdornment, Tabs, Tab,
  Table, TableHead, TableBody, TableRow, TableCell, TableContainer,
  TablePagination
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SchoolIcon from '@mui/icons-material/School';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import AssignmentIcon from '@mui/icons-material/Assignment';
import CardMembershipIcon from '@mui/icons-material/CardMembership';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import QuizIcon from '@mui/icons-material/Quiz';
import DeleteIcon from '@mui/icons-material/Delete';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SearchIcon from '@mui/icons-material/Search';
import CancelIcon from '@mui/icons-material/Cancel';
import DownloadIcon from '@mui/icons-material/Download';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import { courseApi } from '../api/courseApi';
import { employeeApi } from '../api/employeeApi';
import { toast } from 'react-toastify';

const statusColors = {
  NOT_ENROLLED: 'default',
  ENROLLED: 'primary',
  IN_PROGRESS: 'warning',
  COMPLETED: 'success',
  FAILED: 'error'
};

const statusLabels = {
  NOT_ENROLLED: 'Not Enrolled',
  ENROLLED: 'Enrolled',
  IN_PROGRESS: 'In Progress',
  COMPLETED: 'Completed',
  FAILED: 'Failed'
};

function extractYoutubeId(url) {
  if (!url) return null;
  const clean = url.trim();
  // Handles: watch?v=, youtu.be/, embed/, shorts/, live/
  const m = clean.match(
    /(?:youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/|live\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/
  );
  return m ? m[1] : null;
}

// eslint-disable-next-line no-unused-vars
function YoutubeThumbnail({ url, title }) {
  const vid = extractYoutubeId(url);
  if (!vid) {
    return (
      <Box sx={{ height: 160, bgcolor: '#1e293b', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <SchoolIcon sx={{ fontSize: 48, color: '#475569' }} />
      </Box>
    );
  }
  return (
    <Box sx={{ position: 'relative', height: 160, overflow: 'hidden', bgcolor: '#000' }}>
      <img
        src={`https://img.youtube.com/vi/${vid}/mqdefault.jpg`}
        alt={title}
        style={{ width: '100%', height: '100%', objectFit: 'cover', opacity: 0.85 }}
      />
      <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <PlayCircleOutlineIcon sx={{ fontSize: 52, color: 'rgba(255,255,255,0.9)' }} />
      </Box>
    </Box>
  );
}

const EMPTY_FORM = { title: '', description: '', content: '', youtubeUrl: '', durationHours: '' };
const EMPTY_EXAM_FORM = { passingScore: 70, questions: [] };
const EMPTY_QUESTION = { question: '', options: ['', '', '', ''], correctAnswer: 0 };

const CoursesPage = () => {
  const { user } = useSelector((s) => s.auth);
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'DIRECTOR';
  const isManager = user?.role === 'MANAGER' || user?.role === 'ASSISTANT_MANAGER';

  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [myEmployee, setMyEmployee] = useState(null);
  const [allEmployees, setAllEmployees] = useState([]);

  // Create / Edit course dialog
  const [courseDialogOpen, setCourseDialogOpen] = useState(false);
  const [editingCourse, setEditingCourse] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);

  // Assign dialog
  const [assignCourse, setAssignCourse] = useState(null);
  const [assignTarget, setAssignTarget] = useState(null);
  const [assignAll, setAssignAll] = useState(false);
  const [enrolledIds, setEnrolledIds] = useState([]);
  const [assigning, setAssigning] = useState(false);

  // Exam creation dialog
  const [examCreateCourse, setExamCreateCourse] = useState(null);
  const [examForm, setExamForm] = useState(EMPTY_EXAM_FORM);
  const [generating, setGenerating] = useState(false);
  const [examEditIndex, setExamEditIndex] = useState(0);

  // MCQ exam taking dialog
  const [takeExamCourse, setTakeExamCourse] = useState(null);
  const [examQuestions, setExamQuestions] = useState([]);
  const [examAnswers, setExamAnswers] = useState([]);
  const [examLoading, setExamLoading] = useState(false);
  const [currentQuestion, setCurrentQuestion] = useState(0);
  const [examResult, setExamResult] = useState(null);

  const [certs, setCerts] = useState([]);
  const [certsLoading, setCertsLoading] = useState(false);
  const [certPage, setCertPage] = useState(0);
  const [certRpp, setCertRpp] = useState(10);
  const [allEnrollments, setAllEnrollments] = useState([]);
  const [enrollPage, setEnrollPage] = useState(0);
  const [enrollRpp, setEnrollRpp] = useState(10);

  // Detail / catalog view
  const [viewCourse, setViewCourse] = useState(null);
  const [detailTab, setDetailTab] = useState('lessons');
  const [courseSearch, setCourseSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const CARD_GRADIENT = 'linear-gradient(135deg, #1e3a5f 0%, #0f172a 100%)';

  const CATEGORIES = ['Development', 'Design', 'Marketing', 'Business', 'Technology', 'Management'];

  const getProgress = (course) =>
    ({ COMPLETED: 100, IN_PROGRESS: 60, ENROLLED: 10 }[course.enrollmentStatus] ?? 0);

  const filteredCourses = useMemo(() => {
    const q = courseSearch.toLowerCase();
    return courses.filter(c => {
      const matchSearch = !q || c.title?.toLowerCase().includes(q) || c.description?.toLowerCase().includes(q);
      const matchStatus =
        statusFilter === 'ALL' ? true :
        statusFilter === 'CERTIFICATE' ? !!c.certificateNumber :
        statusFilter === 'ENROLLED' ? ['ENROLLED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'].includes(c.enrollmentStatus) :
        c.enrollmentStatus === statusFilter;
      return matchSearch && matchStatus;
    });
  }, [courses, courseSearch, statusFilter]);

  const mergeEnrollment = (allCourses, enrolled) => {
    const map = {};
    enrolled.forEach(c => { map[c.id] = c; });
    return allCourses.map(c => map[c.id] ?? { ...c, enrollmentStatus: 'NOT_ENROLLED' });
  };

  useEffect(() => {
    const init = async () => {
      try {
        if (isAdmin) {
          const [empResp, allResp, allCourses, enrollments] = await Promise.all([
            employeeApi.getByUserId(user.userId).catch(() => null),
            employeeApi.getAll(),
            courseApi.getAll(),
            courseApi.getAllEnrollments(),
          ]);
          setMyEmployee(empResp);
          setAllEmployees(allResp);
          setCourses(allCourses);
          setAllEnrollments(Array.isArray(enrollments) ? enrollments : []);
        } else if (isManager) {
          const empResp = await employeeApi.getByUserId(user.userId).catch(() => null);
          setMyEmployee(empResp);
          const [teamResp, allCourses, enrolled] = await Promise.all([
            empResp ? employeeApi.getTeam(empResp.id) : Promise.resolve([]),
            courseApi.getAll(),
            empResp ? courseApi.getForEmployee(empResp.id) : Promise.resolve([]),
          ]);
          setAllEmployees(teamResp);
          setCourses(mergeEnrollment(allCourses, enrolled));
        } else {
          const emp = await employeeApi.getByUserId(user.userId);
          setMyEmployee(emp);
          const data = await courseApi.getForEmployee(emp.id);
          setCourses(data);
        }
      } catch {
        toast.error('Failed to load courses');
      } finally {
        setLoading(false);
      }
    };
    init();
  }, [user, isAdmin, isManager]);

  useEffect(() => {
    const req = isAdmin ? courseApi.getAllCertificates() : courseApi.getMyCertificates();
    req.then(r => setCerts(Array.isArray(r) ? r : [])).catch(() => setCerts([]));
  }, [isAdmin]);

  useEffect(() => { setEnrollPage(0); }, [statusFilter]);
  useEffect(() => {
    if (statusFilter !== 'CERTIFICATE') return;
    setCertsLoading(true);
    const req = isAdmin ? courseApi.getAllCertificates() : courseApi.getMyCertificates();
    req.then(r => setCerts(Array.isArray(r) ? r : []))
      .catch(() => setCerts([]))
      .finally(() => setCertsLoading(false));
  }, [statusFilter, isAdmin]);

  useEffect(() => {
    if (viewCourse) {
      const updated = courses.find(c => c.id === viewCourse.id);
      if (updated) setViewCourse(updated);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courses]);

  const refresh = async () => {
    try {
      if (isAdmin) {
        const [allCourses, enrollments] = await Promise.all([
          courseApi.getAll(),
          courseApi.getAllEnrollments(),
        ]);
        setCourses(allCourses);
        setAllEnrollments(Array.isArray(enrollments) ? enrollments : []);
      } else if (isManager && myEmployee) {
        const [allCourses, enrolled] = await Promise.all([
          courseApi.getAll(),
          courseApi.getForEmployee(myEmployee.id),
        ]);
        setCourses(mergeEnrollment(allCourses, enrolled));
      } else if (myEmployee) {
        setCourses(await courseApi.getForEmployee(myEmployee.id));
      }
    } catch {
      toast.error('Failed to refresh courses');
    }
  };

  // --- Course CRUD ---
  const openCreateCourse = () => {
    setEditingCourse(null);
    setForm(EMPTY_FORM);
    setCourseDialogOpen(true);
  };

  const openEditCourse = (course) => {
    setEditingCourse(course);
    setForm({
      title: course.title || '',
      description: course.description || '',
      content: course.content || '',
      youtubeUrl: course.youtubeUrl || '',
      durationHours: course.durationHours || ''
    });
    setCourseDialogOpen(true);
  };

  const handleSaveCourse = async () => {
    if (!form.title.trim()) { toast.error('Title is required'); return; }
    try {
      const payload = { ...form, durationHours: parseInt(form.durationHours) || null };
      if (editingCourse) {
        await courseApi.update(editingCourse.id, payload);
        toast.success('Course updated!');
      } else {
        await courseApi.create(payload, user.userId);
        toast.success('Course created!');
        toast.info(
          form.youtubeUrl
            ? 'Fetching video transcript and generating quiz questions...'
            : 'Generating quiz questions from course content...',
          { autoClose: 10000 }
        );
        setTimeout(refresh, 12000);
      }
      setCourseDialogOpen(false);
      await refresh();
    } catch {
      toast.error('Failed to save course');
    }
  };

  const handleDeleteCourse = async (id) => {
    if (!window.confirm('Delete this course?')) return;
    try {
      await courseApi.delete(id);
      toast.success('Course deleted');
      await refresh();
    } catch {
      toast.error('Failed to delete course');
    }
  };

  // --- Enroll / Watch ---
  const handleEnroll = async (courseId) => {
    try {
      await courseApi.enroll(courseId, myEmployee.id);
      toast.success('Enrolled successfully!');
      await refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to enroll');
    }
  };

  const handleMarkWatched = async (courseId) => {
    try {
      await courseApi.markWatched(courseId, myEmployee.id);
      toast.success('Progress updated!');
      await refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update progress');
    }
  };

  // --- Assign ---
  const openAssign = async (course) => {
    setAssignCourse(course);
    setAssignTarget(null);
    setAssignAll(false);
    setEnrolledIds([]);
    try {
      const ids = await courseApi.getEnrolledEmployeeIds(course.id);
      setEnrolledIds(ids);
    } catch {
      setEnrolledIds([]);
    }
  };

  const handleAssign = async () => {
    setAssigning(true);
    try {
      await courseApi.assign(assignCourse.id, assignTarget?.id ?? null, assignAll);
      toast.success(assignAll ? 'Assigned to all employees!' : `Assigned to ${assignTarget?.firstName} ${assignTarget?.lastName}!`);
      setAssignCourse(null);
      await refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to assign');
    } finally {
      setAssigning(false);
    }
  };

  // --- Exam creation ---
  const openExamCreate = async (course) => {
    setExamCreateCourse(course);
    setExamEditIndex(0);
    setExamForm({ passingScore: course.examPassingScore || 70, questions: [] });
    if (course.hasExam) {
      try {
        const questions = await courseApi.getAdminExamQuestions(course.id);
        setExamForm({ passingScore: course.examPassingScore || 70, questions });
      } catch {
        // leave empty — admin can add/generate questions
      }
    }
  };

  const handleGenerateQuiz = async () => {
    setGenerating(true);
    try {
      const questions = await courseApi.generateQuestions(examCreateCourse.id);
      if (!questions || questions.length === 0) {
        toast.warning('No questions could be generated. Try adding more course content or a YouTube URL.');
        return;
      }
      setExamForm(f => ({ ...f, questions }));
      setExamEditIndex(0);
      toast.success(`Generated ${questions.length} questions! Review and save when ready.`);
    } catch {
      toast.error('Failed to generate questions');
    } finally {
      setGenerating(false);
    }
  };

  const addQuestion = () => {
    setExamForm(f => {
      const updated = [...f.questions, { ...EMPTY_QUESTION, options: ['', '', '', ''] }];
      setExamEditIndex(updated.length - 1);
      return { ...f, questions: updated };
    });
  };

  const updateQuestion = (qi, field, value) => {
    setExamForm(f => {
      const qs = [...f.questions];
      qs[qi] = { ...qs[qi], [field]: value };
      return { ...f, questions: qs };
    });
  };

  const updateOption = (qi, oi, value) => {
    setExamForm(f => {
      const qs = [...f.questions];
      const opts = [...qs[qi].options];
      opts[oi] = value;
      qs[qi] = { ...qs[qi], options: opts };
      return { ...f, questions: qs };
    });
  };

  const removeQuestion = (qi) => {
    setExamForm(f => {
      const updated = f.questions.filter((_, i) => i !== qi);
      setExamEditIndex(idx => Math.min(idx, Math.max(0, updated.length - 1)));
      return { ...f, questions: updated };
    });
  };

  const handleSaveExam = async () => {
    if (examForm.questions.length === 0) { toast.error('Add at least one question'); return; }
    for (let i = 0; i < examForm.questions.length; i++) {
      const q = examForm.questions[i];
      if (!q.question || !q.question.toString().trim()) {
        toast.error(`Question ${i + 1} has no text`); return;
      }
      if (!Array.isArray(q.options) || q.options.some(o => !o || !o.toString().trim())) {
        toast.error(`Question ${i + 1} has empty options`); return;
      }
    }
    try {
      await courseApi.createExam(examCreateCourse.id, {
        questions: examForm.questions,
        passingScore: Number(examForm.passingScore) || 70
      });
      toast.success('Exam saved!');
      setExamCreateCourse(null);
      await refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Failed to save exam');
    }
  };

  // --- Take exam ---
  const openTakeExam = async (course) => {
    setExamLoading(true);
    setTakeExamCourse(course);
    setExamAnswers([]);
    setCurrentQuestion(0);
    try {
      const qs = await courseApi.getExamQuestions(course.id);
      setExamQuestions(qs);
      setExamAnswers(new Array(qs.length).fill(null));
    } catch {
      toast.error('Failed to load exam questions');
      setTakeExamCourse(null);
    } finally {
      setExamLoading(false);
    }
  };

  const handleSubmitExam = async () => {
    if (examAnswers.some(a => a === null)) {
      toast.error('Please answer all questions');
      return;
    }
    try {
      const result = await courseApi.submitExamAnswers(takeExamCourse.id, myEmployee.id, examAnswers);
      setExamResult(result);
      await refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit exam');
    }
  };

  const closeExamDialog = () => {
    setTakeExamCourse(null);
    setExamResult(null);
  };

  const retryExam = () => {
    setExamResult(null);
    setCurrentQuestion(0);
    setExamAnswers(new Array(examQuestions.length).fill(null));
  };

  const downloadCertificate = async (courseName, employeeName, certNo) => {
    try {
      const blob = await courseApi.downloadCertificatePdf(certNo);
      const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `Certificate_${(employeeName || 'Employee').replace(/\s+/g, '_')}_${courseName.replace(/[^a-zA-Z0-9]/g, '_').substring(0, 30)}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Certificate download failed', err);
    }
  };

  const getEmployeeName = () =>
    myEmployee ? `${myEmployee.firstName} ${myEmployee.lastName}` : user?.fullName || 'Employee';

  if (loading) return (
    <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
      <CircularProgress sx={{ color: '#1e3a5f' }} />
    </Box>
  );

  return (
    <Box sx={{ bgcolor: '#f8fafc', minHeight: '100%' }}>

      <Box>
      {viewCourse ? (
        /* ── DETAIL VIEW ── */
        <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
          {/* Left Sidebar */}
          <Card sx={{ width: 300, minWidth: 300, flexShrink: 0, borderRadius: 3, overflow: 'hidden' }}>
            <Box sx={{ p: 2, pb: 0 }}>
              <Button startIcon={<ArrowBackIcon />} size="small"
                onClick={() => { setViewCourse(null); setDetailTab('lessons'); }}
                sx={{ color: '#64748b', textTransform: 'none', mb: 1 }}>
                Back to Catalog
              </Button>
            </Box>
            <Box sx={{ height: 90, px: 2.5, pb: 2, background: CARD_GRADIENT, display: 'flex', alignItems: 'flex-end' }}>
              <Typography fontWeight={700} color="#fff" sx={{ lineHeight: 1.3, fontSize: '0.95rem' }}>
                {viewCourse.title}
              </Typography>
            </Box>
            <CardContent sx={{ pt: 2 }}>
              {viewCourse.durationHours && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1.5 }}>
                  <AccessTimeIcon sx={{ fontSize: 16, color: '#64748b' }} />
                  <Typography variant="body2" color="text.secondary">{viewCourse.durationHours} hours</Typography>
                </Box>
              )}
              {viewCourse.enrollmentStatus && viewCourse.enrollmentStatus !== 'NOT_ENROLLED' && (
                <Box sx={{ mb: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="caption" color="text.secondary">Progress</Typography>
                    <Typography variant="caption" fontWeight={600} sx={{ color: '#1e3a5f' }}>{getProgress(viewCourse)}%</Typography>
                  </Box>
                  <LinearProgress variant="determinate" value={getProgress(viewCourse)}
                    sx={{ height: 6, borderRadius: 3, bgcolor: '#e2e8f0', '& .MuiLinearProgress-bar': { bgcolor: '#1e3a5f' } }} />
                </Box>
              )}
              <Divider sx={{ my: 1.5 }} />
              <Typography variant="caption" fontWeight={700} color="text.secondary"
                sx={{ textTransform: 'uppercase', letterSpacing: 0.5, mb: 1.5, display: 'block' }}>
                Lessons
              </Typography>
              <Box onClick={() => {
                setDetailTab('lessons');
                if (!isAdmin && viewCourse.enrollmentStatus === 'ENROLLED' && viewCourse.youtubeUrl) {
                  handleMarkWatched(viewCourse.id);
                }
              }} sx={{
                display: 'flex', alignItems: 'center', gap: 1.5, p: 1, borderRadius: 1.5,
                cursor: 'pointer', mb: 0.5,
                bgcolor: detailTab === 'lessons' ? '#f0fdfa' : 'transparent',
                '&:hover': { bgcolor: '#f0fdfa' }
              }}>
                {['IN_PROGRESS', 'COMPLETED', 'FAILED'].includes(viewCourse.enrollmentStatus) ? (
                  <CheckCircleIcon sx={{ fontSize: 18, color: '#1e3a5f', flexShrink: 0 }} />
                ) : (
                  <Box sx={{ width: 18, height: 18, borderRadius: '50%', border: '2px solid #cbd5e1', flexShrink: 0 }} />
                )}
                <Box>
                  <Typography variant="body2" fontWeight={detailTab === 'lessons' ? 600 : 400}>Watch Video</Typography>
                  {!isAdmin && viewCourse.enrollmentStatus === 'ENROLLED' && viewCourse.youtubeUrl && (
                    <Typography variant="caption" sx={{ color: '#1e3a5f' }}>Click to mark as watched</Typography>
                  )}
                </Box>
              </Box>
              {viewCourse.hasExam && (
                <Box onClick={() => {
                  if (!isAdmin && (viewCourse.enrollmentStatus === 'IN_PROGRESS' || viewCourse.enrollmentStatus === 'FAILED')) {
                    openTakeExam(viewCourse);
                  } else {
                    setDetailTab('exam');
                  }
                }} sx={{
                  display: 'flex', alignItems: 'center', gap: 1.5, p: 1, borderRadius: 1.5,
                  cursor: 'pointer',
                  bgcolor: detailTab === 'exam' ? '#f0fdfa' : 'transparent',
                  '&:hover': { bgcolor: '#f0fdfa' }
                }}>
                  {viewCourse.enrollmentStatus === 'COMPLETED' ? (
                    <CheckCircleIcon sx={{ fontSize: 18, color: '#1e3a5f', flexShrink: 0 }} />
                  ) : (
                    <Box sx={{ width: 18, height: 18, borderRadius: '50%', border: '2px solid #cbd5e1', flexShrink: 0 }} />
                  )}
                  <Typography variant="body2" fontWeight={detailTab === 'exam' ? 600 : 400}>Take Exam</Typography>
                </Box>
              )}
              {viewCourse.enrollmentStatus === 'COMPLETED' && viewCourse.certificateNumber && (
                <>
                  <Divider sx={{ my: 1.5 }} />
                  <Box sx={{ p: 1.5, bgcolor: '#f0fdf4', borderRadius: 2, textAlign: 'center' }}>
                    <CardMembershipIcon sx={{ fontSize: 24, color: '#16a34a', mb: 0.5 }} />
                    <Typography variant="caption" fontWeight={600} color="success.dark" display="block">Certificate Earned</Typography>
                    <Typography variant="caption" color="text.secondary" display="block" mb={1}>{viewCourse.certificateNumber}</Typography>
                    <Button
                      size="small"
                      variant="contained"
                      startIcon={<DownloadIcon sx={{ fontSize: 14 }} />}
                      onClick={() => downloadCertificate(viewCourse.title, getEmployeeName(), viewCourse.certificateNumber)}
                      sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', fontSize: '0.75rem' }}
                    >
                      Download
                    </Button>
                  </Box>
                </>
              )}
              {(isAdmin || isManager) && <Divider sx={{ my: 1.5 }} />}
              {isAdmin && (
                <Stack direction="row" spacing={0.5} flexWrap="wrap">
                  <Tooltip title="Assign"><IconButton size="small" onClick={() => openAssign(viewCourse)} sx={{ color: '#1e3a5f' }}><PersonAddIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title={viewCourse.hasExam ? 'Edit Exam' : 'Add Exam'}><IconButton size="small" onClick={() => openExamCreate(viewCourse)} sx={{ color: '#7c3aed' }}><QuizIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Edit"><IconButton size="small" onClick={() => openEditCourse(viewCourse)}><AssignmentIcon fontSize="small" /></IconButton></Tooltip>
                  <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => { handleDeleteCourse(viewCourse.id); setViewCourse(null); }}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                </Stack>
              )}
              {isManager && (
                <Tooltip title="Assign to Employee">
                  <IconButton size="small" onClick={() => openAssign(viewCourse)} sx={{ color: '#1e3a5f' }}>
                    <PersonAddIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </CardContent>
          </Card>

          {/* Right Panel */}
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Box sx={{ bgcolor: '#fff', borderRadius: '12px 12px 0 0', borderBottom: '1px solid #e2e8f0' }}>
              <Tabs value={detailTab} onChange={(_, v) => setDetailTab(v)}
                sx={{ '& .MuiTab-root': { textTransform: 'none', fontWeight: 600 }, '& .MuiTabs-indicator': { bgcolor: '#1e3a5f' } }}>
                <Tab label="Lessons" value="lessons" />
                {viewCourse.hasExam && <Tab label="Exam" value="exam" />}
              </Tabs>
            </Box>

            {detailTab === 'lessons' && (
              <Box sx={{ bgcolor: '#fff', borderRadius: '0 0 12px 12px', overflow: 'hidden' }}>
                {viewCourse.youtubeUrl && extractYoutubeId(viewCourse.youtubeUrl) ? (
                  <Box sx={{ position: 'relative', paddingTop: '56.25%', bgcolor: '#000' }}>
                    <iframe
                      style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', border: 'none' }}
                      src={`https://www.youtube.com/embed/${extractYoutubeId(viewCourse.youtubeUrl)}?rel=0`}
                      title={viewCourse.title}
                      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                      allowFullScreen
                    />
                  </Box>
                ) : (
                  <Box sx={{ height: 280, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: '#f1f5f9' }}>
                    <SchoolIcon sx={{ fontSize: 56, color: '#cbd5e1' }} />
                  </Box>
                )}
                <Box sx={{ p: 3 }}>
                  {viewCourse.description && (
                    <Typography variant="body1" color="text.secondary" mb={2}>{viewCourse.description}</Typography>
                  )}
                  {viewCourse.content && (
                    <>
                      <Divider sx={{ my: 2 }} />
                      <Typography variant="subtitle2" fontWeight={600} mb={1}>Course Content</Typography>
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{viewCourse.content}</Typography>
                    </>
                  )}
                  {!isAdmin && viewCourse.enrollmentStatus === 'NOT_ENROLLED' && (
                    <Button variant="contained" onClick={() => handleEnroll(viewCourse.id)}
                      sx={{ mt: 2, bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, textTransform: 'none' }}>
                      Enroll Now
                    </Button>
                  )}
                  {!isAdmin && viewCourse.enrollmentStatus === 'ENROLLED' && viewCourse.youtubeUrl && (
                    <Button variant="outlined" color="warning" onClick={() => handleMarkWatched(viewCourse.id)} sx={{ mt: 2, textTransform: 'none' }}>
                      Mark as Watched
                    </Button>
                  )}
                </Box>
              </Box>
            )}

            {detailTab === 'exam' && viewCourse.hasExam && (
              <Box sx={{ bgcolor: '#fff', borderRadius: '0 0 12px 12px', p: 4, textAlign: 'center' }}>
                {isAdmin ? (
                  <>
                    <QuizIcon sx={{ fontSize: 60, color: '#7c3aed', mb: 2 }} />
                    <Typography variant="h6" fontWeight={700}>Exam Management</Typography>
                    <Typography color="text.secondary" mt={1} mb={3}>
                      Passing score: {viewCourse.examPassingScore}% · {viewCourse.enrollmentCount} enrolled
                    </Typography>
                    <Button variant="contained" startIcon={<QuizIcon />} onClick={() => openExamCreate(viewCourse)}
                      sx={{ textTransform: 'none', bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' } }}>
                      Edit Exam Questions
                    </Button>
                  </>
                ) : viewCourse.enrollmentStatus === 'COMPLETED' ? (
                  <>
                    <CheckCircleIcon sx={{ fontSize: 60, color: '#1e3a5f', mb: 2 }} />
                    <Typography variant="h6" fontWeight={700} color="success.dark">Exam Completed!</Typography>
                    <Typography color="text.secondary" mt={1}>Score: {viewCourse.examScore}%</Typography>
                    {viewCourse.certificateNumber && (
                      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, mt: 2 }}>
                        <Chip icon={<CardMembershipIcon />} label={`Certificate: ${viewCourse.certificateNumber}`}
                          color="success" variant="outlined" />
                        <Button
                          variant="contained"
                          startIcon={<DownloadIcon />}
                          onClick={() => downloadCertificate(viewCourse.title, getEmployeeName(), viewCourse.certificateNumber)}
                          sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none' }}
                        >
                          Download Certificate
                        </Button>
                      </Box>
                    )}
                  </>
                ) : viewCourse.enrollmentStatus === 'FAILED' ? (
                  <>
                    <QuizIcon sx={{ fontSize: 60, color: '#ef4444', mb: 2 }} />
                    <Typography variant="h6" fontWeight={700} color="error.dark">Previous Attempt Failed</Typography>
                    <Typography color="text.secondary" mt={1} mb={3}>
                      Score: {viewCourse.examScore}% · Passing: {viewCourse.examPassingScore}%
                    </Typography>
                    <Button variant="contained" color="secondary" startIcon={<QuizIcon />}
                      onClick={() => openTakeExam(viewCourse)} sx={{ textTransform: 'none' }}>
                      Retry Exam
                    </Button>
                  </>
                ) : viewCourse.enrollmentStatus === 'IN_PROGRESS' ? (
                  <>
                    <QuizIcon sx={{ fontSize: 60, color: '#7c3aed', mb: 2 }} />
                    <Typography variant="h6" fontWeight={700}>Ready for the Exam?</Typography>
                    <Typography color="text.secondary" mt={1} mb={3}>Passing score: {viewCourse.examPassingScore}%</Typography>
                    <Button variant="contained" color="secondary" startIcon={<QuizIcon />}
                      onClick={() => openTakeExam(viewCourse)} sx={{ textTransform: 'none' }}>
                      Start Exam
                    </Button>
                  </>
                ) : viewCourse.enrollmentStatus === 'NOT_ENROLLED' ? (
                  <>
                    <AssignmentIcon sx={{ fontSize: 60, color: '#cbd5e1', mb: 2 }} />
                    <Typography color="text.secondary">Enroll in this course to take the exam</Typography>
                  </>
                ) : (
                  <>
                    <AssignmentIcon sx={{ fontSize: 60, color: '#cbd5e1', mb: 2 }} />
                    <Typography color="text.secondary">Watch the video lesson first to unlock the exam</Typography>
                  </>
                )}
              </Box>
            )}
          </Box>
        </Box>
      ) : (
        /* ── CATALOG VIEW ── */
        <Box>
          {/* Header */}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}>
            <Box>
              <Typography variant="h5" fontWeight={700}>Course Catalog</Typography>
              <Typography variant="body2" color="text.secondary" mt={0.5}>Explore and manage learning courses</Typography>
            </Box>
            {isAdmin && statusFilter === 'ALL' && (
              <Button variant="contained" startIcon={<AddIcon />} onClick={openCreateCourse}
                sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' }, borderRadius: 2, textTransform: 'none' }}>
                Create Course
              </Button>
            )}
          </Box>


          {/* Search + Filter tabs */}
          <Box sx={{ mb: 3 }}>
            <TextField
              placeholder="Search courses..."
              value={courseSearch}
              onChange={(e) => setCourseSearch(e.target.value)}
              size="small"
              InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon sx={{ color: '#94a3b8' }} /></InputAdornment> }}
              sx={{ mb: 2, width: { xs: '100%', sm: 360 }, bgcolor: '#fff', '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
            />
            <Tabs value={statusFilter} onChange={(_, v) => setStatusFilter(v)}
              sx={{ '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, minWidth: 'auto', px: 2 }, '& .MuiTabs-indicator': { bgcolor: '#1e3a5f' } }}>
              <Tab label={`All (${courses.length})`} value="ALL" />
              <Tab label={`Enrolled (${isAdmin ? allEnrollments.filter(e => e.status === 'ENROLLED').length : courses.filter(c => ['ENROLLED','IN_PROGRESS','COMPLETED','FAILED'].includes(c.enrollmentStatus)).length})`} value="ENROLLED" />
              <Tab label={`In Progress (${isAdmin ? allEnrollments.filter(e => e.status === 'IN_PROGRESS').length : courses.filter(c => c.enrollmentStatus === 'IN_PROGRESS').length})`} value="IN_PROGRESS" />
              <Tab label={`Completed (${isAdmin ? allEnrollments.filter(e => e.status === 'COMPLETED').length : courses.filter(c => c.enrollmentStatus === 'COMPLETED').length})`} value="COMPLETED" />
              <Tab
                label={`Certificates (${certs.length})`}
                value="CERTIFICATE"
                icon={<CardMembershipIcon sx={{ fontSize: 15 }} />}
                iconPosition="start"
                sx={{ gap: 0.5 }}
              />
            </Tabs>
          </Box>

          {/* Enrollment table (Admin/Director system-wide view) */}
          {isAdmin && ['ENROLLED', 'IN_PROGRESS', 'COMPLETED'].includes(statusFilter) ? (() => {
            const rows = allEnrollments.filter(e => e.status === statusFilter);
            const statusLabel = { ENROLLED: 'Enrolled', IN_PROGRESS: 'In Progress', COMPLETED: 'Completed' }[statusFilter];
            const statusChipColor = { ENROLLED: 'primary', IN_PROGRESS: 'warning', COMPLETED: 'success' }[statusFilter];
            return (
              <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                        <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Employee</TableCell>
                        <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Code</TableCell>
                        <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Course Title</TableCell>
                        <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }} align="center">Progress</TableCell>
                        {statusFilter === 'COMPLETED' && <>
                          <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Completion Date</TableCell>
                          <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }} align="center">Score</TableCell>
                        </>}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={statusFilter === 'COMPLETED' ? 5 : 3} align="center" sx={{ py: 6 }}>
                            <SchoolIcon sx={{ fontSize: 48, color: '#cbd5e1', mb: 1, display: 'block', mx: 'auto' }} />
                            <Typography color="text.secondary" fontSize={14}>No {statusLabel.toLowerCase()} enrollments found.</Typography>
                          </TableCell>
                        </TableRow>
                      ) : rows.slice(enrollPage * enrollRpp, (enrollPage + 1) * enrollRpp).map((row, i) => (
                        <TableRow key={`${row.employeeId}-${row.courseId}`} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
                          <TableCell sx={{ fontSize: 13, fontWeight: 600 }}>{row.employeeName}</TableCell>
                          <TableCell sx={{ fontSize: 13, color: '#64748b', fontFamily: 'monospace' }}>{row.employeeCode}</TableCell>
                          <TableCell sx={{ fontSize: 13, maxWidth: 240 }}>
                            <Typography variant="body2" fontWeight={600} noWrap>{row.courseTitle}</Typography>
                          </TableCell>
                          <TableCell align="center">
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 80 }}>
                              <LinearProgress variant="determinate" value={row.progressPercent}
                                sx={{ flex: 1, height: 6, borderRadius: 3, bgcolor: '#e2e8f0', '& .MuiLinearProgress-bar': { bgcolor: '#1e3a5f' } }} />
                              <Typography variant="caption" fontWeight={600} sx={{ minWidth: 28 }}>{row.progressPercent}%</Typography>
                            </Box>
                          </TableCell>
                          {statusFilter === 'COMPLETED' && <>
                            <TableCell sx={{ fontSize: 13, color: '#475569', whiteSpace: 'nowrap' }}>{row.completionDate || '—'}</TableCell>
                            <TableCell align="center">
                              {row.examScore != null
                                ? <Chip label={`${row.examScore}%`} size="small" color={row.examScore >= 70 ? 'success' : 'warning'} sx={{ fontWeight: 700, fontSize: 11.5 }} />
                                : '—'}
                            </TableCell>
                          </>}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                {rows.length > enrollRpp && (
                  <TablePagination
                    component="div"
                    count={rows.length}
                    page={enrollPage}
                    onPageChange={(_, p) => setEnrollPage(p)}
                    rowsPerPage={enrollRpp}
                    onRowsPerPageChange={e => { setEnrollRpp(parseInt(e.target.value, 10)); setEnrollPage(0); }}
                    rowsPerPageOptions={[10, 25, 50]}
                  />
                )}
              </Card>
            );
          })() : null}

          {/* Certificates table */}
          {!isAdmin || !['ENROLLED', 'IN_PROGRESS', 'COMPLETED'].includes(statusFilter) ? statusFilter === 'CERTIFICATE' ? (
            <Card sx={{ borderRadius: 2, overflow: 'hidden' }}>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                      {isAdmin && <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Employee</TableCell>}
                      <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Course Title</TableCell>
                      <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Certificate ID</TableCell>
                      <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }}>Date of Completion</TableCell>
                      <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }} align="center">Score</TableCell>
                      <TableCell sx={{ fontWeight: 700, fontSize: 12.5, color: '#475569' }} align="center">Download</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {certsLoading ? (
                      <TableRow>
                        <TableCell colSpan={isAdmin ? 6 : 5} align="center" sx={{ py: 5 }}>
                          <CircularProgress size={28} sx={{ color: '#1e3a5f' }} />
                        </TableCell>
                      </TableRow>
                    ) : certs.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={isAdmin ? 6 : 5} align="center" sx={{ py: 6 }}>
                          <CardMembershipIcon sx={{ fontSize: 48, color: '#cbd5e1', mb: 1, display: 'block', mx: 'auto' }} />
                          <Typography color="text.secondary" fontSize={14}>
                            {isAdmin ? 'No certificates issued yet.' : 'You have not earned any certificates yet.'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    ) : certs.slice(certPage * certRpp, (certPage + 1) * certRpp).map((cert, i) => (
                      <TableRow key={cert.id} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
                        {isAdmin && (
                          <TableCell sx={{ fontSize: 13, fontWeight: 600 }}>{cert.employeeName}</TableCell>
                        )}
                        <TableCell sx={{ fontSize: 13, fontWeight: 600, maxWidth: 260 }}>
                          <Typography variant="body2" fontWeight={600} noWrap>{cert.courseTitle}</Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            icon={<CardMembershipIcon sx={{ fontSize: 14 }} />}
                            label={cert.certificateNumber}
                            size="small"
                            sx={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 11.5, bgcolor: '#f0fdf4', color: '#16a34a', border: '1px solid #bbf7d0' }}
                          />
                        </TableCell>
                        <TableCell sx={{ fontSize: 13, color: '#475569', whiteSpace: 'nowrap' }}>
                          {cert.issuedAt
                            ? new Date(cert.issuedAt).toLocaleDateString('en-US', { day: 'numeric', month: 'long', year: 'numeric' })
                            : '—'}
                        </TableCell>
                        <TableCell align="center">
                          {cert.score != null
                            ? <Chip label={`${cert.score}%`} size="small" color={cert.score >= 70 ? 'success' : 'warning'} sx={{ fontWeight: 700, fontSize: 11.5 }} />
                            : '—'}
                        </TableCell>
                        <TableCell align="center">
                          <Tooltip title="Download Certificate PDF">
                            <IconButton
                              size="small"
                              onClick={() => downloadCertificate(cert.courseTitle, cert.employeeName, cert.certificateNumber)}
                              sx={{ bgcolor: '#1e3a5f', color: 'white', '&:hover': { bgcolor: '#0f172a' }, borderRadius: 1.5, p: 0.75 }}
                            >
                              <DownloadIcon sx={{ fontSize: 18 }} />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              {certs.length > certRpp && (
                <TablePagination
                  component="div"
                  count={certs.length}
                  page={certPage}
                  onPageChange={(_, p) => setCertPage(p)}
                  rowsPerPage={certRpp}
                  onRowsPerPageChange={e => { setCertRpp(parseInt(e.target.value, 10)); setCertPage(0); }}
                  rowsPerPageOptions={[10, 25, 50]}
                />
              )}
            </Card>
          ) : filteredCourses.length === 0 ? (
            <Card sx={{ textAlign: 'center', py: 6 }}>
              <SchoolIcon sx={{ fontSize: 56, color: '#cbd5e1', mb: 2 }} />
              <Typography color="text.secondary">No courses found</Typography>
            </Card>
          ) : (
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' }, gap: 2.5 }}>
              {filteredCourses.map((course) => (
                <Card key={course.id} sx={{
                  borderRadius: 3, overflow: 'hidden', display: 'flex', flexDirection: 'column',
                  transition: 'box-shadow 0.2s, transform 0.2s',
                  '&:hover': { boxShadow: '0 8px 30px rgba(0,0,0,0.12)', transform: 'translateY(-2px)' }
                }}>
                  {/* Gradient header */}
                  <Box sx={{
                    height: 120, background: CARD_GRADIENT,
                    position: 'relative', p: 2,
                    display: 'flex', flexDirection: 'column', justifyContent: 'space-between'
                  }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <Chip label={CATEGORIES[course.id % CATEGORIES.length]} size="small"
                        sx={{ bgcolor: 'rgba(255,255,255,0.25)', color: '#fff', fontWeight: 600, fontSize: '0.7rem', height: 22 }} />
                      {isAdmin && (
                        <Box sx={{ display: 'flex', gap: 0.25 }}>
                          {[
                            { icon: <PersonAddIcon sx={{ fontSize: 13 }} />, title: 'Assign', fn: (e) => { e.stopPropagation(); openAssign(course); } },
                            { icon: <QuizIcon sx={{ fontSize: 13 }} />, title: course.hasExam ? 'Edit Exam' : 'Add Exam', fn: (e) => { e.stopPropagation(); openExamCreate(course); } },
                            { icon: <AssignmentIcon sx={{ fontSize: 13 }} />, title: 'Edit', fn: (e) => { e.stopPropagation(); openEditCourse(course); } },
                            { icon: <DeleteIcon sx={{ fontSize: 13 }} />, title: 'Delete', fn: (e) => { e.stopPropagation(); handleDeleteCourse(course.id); } },
                          ].map(({ icon, title, fn }) => (
                            <Tooltip key={title} title={title}>
                              <IconButton size="small" onClick={fn}
                                sx={{ color: '#fff', bgcolor: 'rgba(255,255,255,0.15)', width: 26, height: 26, '&:hover': { bgcolor: 'rgba(255,255,255,0.3)' } }}>
                                {icon}
                              </IconButton>
                            </Tooltip>
                          ))}
                        </Box>
                      )}
                      {isManager && (
                        <Tooltip title="Assign to Employee">
                          <IconButton size="small" onClick={(e) => { e.stopPropagation(); openAssign(course); }}
                            sx={{ color: '#fff', bgcolor: 'rgba(255,255,255,0.15)', width: 26, height: 26, '&:hover': { bgcolor: 'rgba(255,255,255,0.3)' } }}>
                            <PersonAddIcon sx={{ fontSize: 13 }} />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                      <AccessTimeIcon sx={{ fontSize: 13, color: 'rgba(255,255,255,0.75)' }} />
                      <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.75)' }}>
                        {course.durationHours ? `${course.durationHours}h` : 'Self-paced'}
                      </Typography>
                      {course.hasExam && (
                        <Chip label="Exam" size="small"
                          sx={{ ml: 0.5, bgcolor: 'rgba(255,255,255,0.2)', color: '#fff', height: 18, fontSize: '0.65rem' }} />
                      )}
                    </Box>
                  </Box>

                  <CardContent sx={{ flex: 1, pb: 1 }}>
                    <Typography variant="subtitle1" fontWeight={700} sx={{
                      mb: 0.75,
                      display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'
                    }}>
                      {course.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{
                      display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', mb: 1.5
                    }}>
                      {course.description || 'No description provided.'}
                    </Typography>
                    {course.enrollmentStatus && course.enrollmentStatus !== 'NOT_ENROLLED' && (
                      <Box sx={{ mb: 1 }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                          <Chip label={statusLabels[course.enrollmentStatus] || course.enrollmentStatus}
                            size="small"
                            color={course.enrollmentStatus === 'ENROLLED' ? 'default' : (statusColors[course.enrollmentStatus] || 'default')}
                            sx={{ height: 20, fontSize: '0.7rem', ...(course.enrollmentStatus === 'ENROLLED' && { bgcolor: '#1e3a5f', color: '#fff' }) }} />
                          <Typography variant="caption" color="text.secondary">{getProgress(course)}%</Typography>
                        </Box>
                        <LinearProgress variant="determinate" value={getProgress(course)}
                          sx={{ height: 5, borderRadius: 3, bgcolor: '#e2e8f0', '& .MuiLinearProgress-bar': { bgcolor: '#1e3a5f' } }} />
                      </Box>
                    )}
                    <Typography variant="caption" color="text.secondary">
                      {course.enrollmentCount} enrolled
                      {course.examScore != null && ` · Score: ${course.examScore}%`}
                    </Typography>
                  </CardContent>

                  <CardActions sx={{ px: 2, pb: 2, pt: 0 }}>
                    {!isAdmin && course.enrollmentStatus === 'NOT_ENROLLED' ? (
                      <Button fullWidth variant="outlined" onClick={() => handleEnroll(course.id)}
                        sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600 }}>
                        Enroll
                      </Button>
                    ) : (
                      <Button fullWidth variant="contained"
                        onClick={() => { setViewCourse(course); setDetailTab('lessons'); }}
                        sx={{ borderRadius: 2, textTransform: 'none', fontWeight: 600, bgcolor: '#a3a5a7', '&:hover': { bgcolor: '#0f172a' } }}>
                        Review Course
                      </Button>
                    )}
                  </CardActions>
                </Card>
              ))}
            </Box>
          ) : null}
        </Box>
      )}
      </Box>

      {/* Create / Edit Course Dialog */}
      <Dialog open={courseDialogOpen} onClose={() => setCourseDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>{editingCourse ? 'Edit Course' : 'Create New Course'}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField label="Course Title *" value={form.title}
              onChange={(e) => setForm(f => ({ ...f, title: e.target.value }))} fullWidth />
            <TextField label="Description" value={form.description}
              onChange={(e) => setForm(f => ({ ...f, description: e.target.value }))} fullWidth multiline rows={2} />
            <TextField label="YouTube URL" value={form.youtubeUrl}
              onChange={(e) => setForm(f => ({ ...f, youtubeUrl: e.target.value }))} fullWidth
              placeholder="https://www.youtube.com/watch?v=..." />
            <TextField label="Course Content" value={form.content}
              onChange={(e) => setForm(f => ({ ...f, content: e.target.value }))} fullWidth multiline rows={3} />
            <TextField label="Duration (hours)" type="number" value={form.durationHours}
              onChange={(e) => setForm(f => ({ ...f, durationHours: e.target.value }))} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setCourseDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveCourse}
            disabled={!form.title.trim()}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
            {editingCourse ? 'Save Changes' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Assign Course Dialog */}
      {(() => {
        // Employees eligible to be assigned this course
        const assignableEmployees = (() => {
          const notEnrolled = allEmployees.filter(e => !enrolledIds.includes(e.id) && e.role !== 'ADMIN' && e.role !== 'DIRECTOR');
          if (isAdmin) return notEnrolled;
          // Manager: only their direct reports (not themselves)
          return notEnrolled.filter(
            e => e.managerId === myEmployee?.id && e.id !== myEmployee?.id
          );
        })();
        return (
      <Dialog open={Boolean(assignCourse)} onClose={() => setAssignCourse(null)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Assign Course</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" mb={2}>
            Assign <strong>{assignCourse?.title}</strong> to
            {isAdmin ? ' an employee or all employees.' : ' one of your team members.'}
          </Typography>
          {isAdmin && (
            <FormControl component="fieldset" sx={{ mb: 2 }}>
              <RadioGroup row value={assignAll ? 'all' : 'one'} onChange={(e) => setAssignAll(e.target.value === 'all')}>
                <FormControlLabel value="one" control={<Radio />} label="Specific Employee" />
                <FormControlLabel value="all" control={<Radio />} label="All Employees" />
              </RadioGroup>
            </FormControl>
          )}
          {!assignAll && (
            <Autocomplete
              options={assignableEmployees}
              getOptionLabel={(e) => `${e.firstName} ${e.lastName} (${e.department || ''})`}
              value={assignTarget}
              onChange={(_, v) => setAssignTarget(v)}
              noOptionsText="No eligible employees (all already assigned)"
              renderInput={(params) => <TextField {...params} label="Select Employee" />}
              fullWidth
            />
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setAssignCourse(null)}>Cancel</Button>
          <Button variant="contained" onClick={handleAssign}
            disabled={assigning || (!assignAll && !assignTarget)}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
            {assigning ? 'Assigning…' : 'Assign'}
          </Button>
        </DialogActions>
      </Dialog>
        );
      })()}

      {/* Create / Edit Exam Dialog */}
      <Dialog open={Boolean(examCreateCourse)} onClose={() => setExamCreateCourse(null)} maxWidth="sm" fullWidth
        PaperProps={{ sx: { minHeight: 520 } }}>
        <DialogTitle sx={{ pb: 1 }}>
          <Typography fontWeight={700}>{examCreateCourse?.hasExam ? 'Edit Exam' : 'Create Exam'} — {examCreateCourse?.title}</Typography>
        </DialogTitle>

        <DialogContent sx={{ pt: 1 }}>
          {/* Toolbar */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2, flexWrap: 'wrap' }}>
            <TextField label="Passing Score (%)" type="number" value={examForm.passingScore}
              onChange={(e) => setExamForm(f => ({ ...f, passingScore: parseInt(e.target.value) || 70 }))}
              inputProps={{ min: 1, max: 100 }} size="small" sx={{ width: 140 }} />
            <Button size="small" variant="contained"
              startIcon={generating ? <CircularProgress size={14} color="inherit" /> : <AutoAwesomeIcon />}
              onClick={handleGenerateQuiz} disabled={generating}
              sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' } }}>
              {generating ? 'Generating...' : 'Generate Quiz'}
            </Button>
            <Button size="small" variant="outlined" startIcon={<AddIcon />} onClick={addQuestion}>
              Add Question
            </Button>
          </Box>

          {examForm.questions.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 6, color: 'text.secondary' }}>
              <QuizIcon sx={{ fontSize: 44, mb: 1, color: '#cbd5e1' }} />
              <Typography>No questions yet. Generate or add manually.</Typography>
            </Box>
          ) : (
            <>
              {/* Dot navigator */}
              <Box sx={{ display: 'flex', gap: 0.75, mb: 2, flexWrap: 'wrap' }}>
                {examForm.questions.map((_, qi) => (
                  <Box key={qi} onClick={() => setExamEditIndex(qi)}
                    sx={{
                      width: 28, height: 28, borderRadius: '50%', cursor: 'pointer',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '0.7rem', fontWeight: 600,
                      bgcolor: qi === examEditIndex ? '#1e3a5f' : '#f1f5f9',
                      color: qi === examEditIndex ? '#fff' : '#64748b',
                      border: qi === examEditIndex ? '2px solid #0f172a' : '2px solid transparent',
                      transition: 'all 0.15s',
                    }}>
                    {qi + 1}
                  </Box>
                ))}
              </Box>

              {/* Current question editor */}
              {(() => {
                const qi = examEditIndex;
                const q = examForm.questions[qi];
                if (!q) return null;
                return (
                  <Box sx={{ p: 2.5, border: '1px solid #e2e8f0', borderRadius: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1.5 }}>
                      <Typography variant="subtitle2" fontWeight={700} sx={{ flex: 1 }}>
                        Question {qi + 1} of {examForm.questions.length}
                      </Typography>
                      <IconButton size="small" color="error" onClick={() => removeQuestion(qi)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Box>
                    <TextField label="Question text" value={q.question} fullWidth multiline rows={2}
                      onChange={(e) => updateQuestion(qi, 'question', e.target.value)} sx={{ mb: 2 }} />
                    <FormControl component="fieldset" fullWidth>
                      <FormLabel component="legend" sx={{ mb: 1, fontSize: '0.82rem' }}>
                        Options — select the correct answer
                      </FormLabel>
                      <RadioGroup value={String(q.correctAnswer)}
                        onChange={(e) => updateQuestion(qi, 'correctAnswer', parseInt(e.target.value))}>
                        {q.options.map((opt, oi) => (
                          <Box key={oi} sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                            <Radio value={String(oi)} size="small"
                              sx={{ color: '#1e3a5f', '&.Mui-checked': { color: '#1e3a5f' } }} />
                            <TextField size="small" placeholder={`Option ${oi + 1}`} value={opt} sx={{ flex: 1 }}
                              onChange={(e) => updateOption(qi, oi, e.target.value)} />
                          </Box>
                        ))}
                      </RadioGroup>
                    </FormControl>
                  </Box>
                );
              })()}
            </>
          )}
        </DialogContent>

        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          <Button onClick={() => setExamCreateCourse(null)} sx={{ mr: 'auto' }}>Cancel</Button>
          <Button variant="outlined"
            disabled={examForm.questions.length === 0 || examEditIndex === 0}
            onClick={() => setExamEditIndex(i => i - 1)}>
            ← Previous
          </Button>
          {examEditIndex < examForm.questions.length - 1 ? (
            <Button variant="outlined"
              disabled={examForm.questions.length === 0}
              onClick={() => setExamEditIndex(i => i + 1)}>
              Next →
            </Button>
          ) : null}
          <Button variant="contained" onClick={handleSaveExam}
            sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
            Save Exam
          </Button>
        </DialogActions>
      </Dialog>

      {/* Take Exam Dialog */}
      <Dialog open={Boolean(takeExamCourse)} onClose={closeExamDialog} maxWidth="sm" fullWidth
        PaperProps={{ sx: { minHeight: 420 } }}>
        <DialogTitle sx={{ pb: 1 }}>
          <Typography fontWeight={700}>{takeExamCourse?.title}</Typography>
          {!examResult && (
            <Typography variant="caption" color="text.secondary">
              Passing score: {takeExamCourse?.examPassingScore}%
            </Typography>
          )}
        </DialogTitle>

        <DialogContent sx={{ pt: 0 }}>
          {examLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress sx={{ color: '#1e3a5f' }} />
            </Box>
          ) : examResult ? (
            /* ── Results View ── */
            <Box>
              {/* Pass / Fail banner */}
              <Box sx={{
                p: 3, textAlign: 'center', borderRadius: 2, mb: 3,
                bgcolor: examResult.passed ? '#f0fdf4' : '#fef2f2',
                border: `1px solid ${examResult.passed ? '#bbf7d0' : '#fecaca'}`,
              }}>
                {examResult.passed ? (
                  <CheckCircleIcon sx={{ fontSize: 56, color: '#22c55e', mb: 1 }} />
                ) : (
                  <CancelIcon sx={{ fontSize: 56, color: '#ef4444', mb: 1 }} />
                )}
                <Typography variant="h6" fontWeight={700}
                  sx={{ color: examResult.passed ? '#16a34a' : '#dc2626' }}>
                  {examResult.passed ? 'Congratulations! You Passed!' : 'Better Luck Next Time!'}
                </Typography>
                <Typography variant="h3" fontWeight={800} mt={0.5}
                  sx={{ color: examResult.passed ? '#16a34a' : '#dc2626' }}>
                  {examResult.score}%
                </Typography>
                <Typography variant="body2" color="text.secondary" mt={0.5}>
                  Passing score: {examResult.passingScore}%
                </Typography>
                {examResult.certificateNumber && (
                  <Box sx={{ mt: 1.5, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1 }}>
                    <Chip icon={<CardMembershipIcon />}
                      label={`Certificate: ${examResult.certificateNumber}`}
                      color="success" variant="outlined" />
                    <Button
                      variant="contained"
                      startIcon={<DownloadIcon />}
                      onClick={() => downloadCertificate(takeExamCourse.title, getEmployeeName(), examResult.certificateNumber)}
                      sx={{ bgcolor: '#16a34a', '&:hover': { bgcolor: '#15803d' }, textTransform: 'none', mt: 0.5 }}
                    >
                      Download Certificate
                    </Button>
                  </Box>
                )}
              </Box>

              {/* Per-question breakdown */}
              <Typography variant="subtitle2" fontWeight={700} mb={1.5} sx={{ color: '#475569' }}>
                Question Breakdown ({examResult.questions?.length} questions)
              </Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                {examResult.questions?.map((q, qi) => (
                  <Box key={qi} sx={{
                    border: `1px solid ${q.isCorrect ? '#bbf7d0' : '#fecaca'}`,
                    borderRadius: 2, p: 2,
                    bgcolor: q.isCorrect ? '#f0fdf4' : '#fff5f5',
                  }}>
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, mb: 1.5 }}>
                      {q.isCorrect ? (
                        <CheckCircleIcon sx={{ color: '#22c55e', fontSize: 18, mt: 0.25, flexShrink: 0 }} />
                      ) : (
                        <CancelIcon sx={{ color: '#ef4444', fontSize: 18, mt: 0.25, flexShrink: 0 }} />
                      )}
                      <Typography variant="body2" fontWeight={600}>
                        Q{qi + 1}. {q.question}
                      </Typography>
                    </Box>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                      {(q.options || []).map((opt, oi) => {
                        const isCorrectOpt = oi === q.correctAnswer;
                        const isUserOpt = oi === q.userAnswer;
                        const isWrongUserOpt = isUserOpt && !isCorrectOpt;
                        return (
                          <Box key={oi} sx={{
                            display: 'flex', alignItems: 'center', gap: 1,
                            px: 1.5, py: 0.6, borderRadius: 1,
                            bgcolor: isCorrectOpt ? '#dcfce7' : isWrongUserOpt ? '#fee2e2' : 'transparent',
                            border: `1px solid ${isCorrectOpt ? '#86efac' : isWrongUserOpt ? '#fca5a5' : 'transparent'}`,
                          }}>
                            <Typography variant="body2" sx={{
                              flex: 1,
                              color: isCorrectOpt ? '#15803d' : isWrongUserOpt ? '#dc2626' : '#475569',
                              fontWeight: (isCorrectOpt || isWrongUserOpt) ? 600 : 400,
                            }}>
                              {String.fromCharCode(65 + oi)}. {opt}
                            </Typography>
                            {isCorrectOpt && <CheckCircleIcon sx={{ fontSize: 15, color: '#22c55e', flexShrink: 0 }} />}
                            {isWrongUserOpt && <CancelIcon sx={{ fontSize: 15, color: '#ef4444', flexShrink: 0 }} />}
                          </Box>
                        );
                      })}
                    </Box>
                  </Box>
                ))}
              </Box>
            </Box>
          ) : examQuestions.length > 0 && (
            /* ── Question View ── */
            <>
              {/* Progress bar */}
              <Box sx={{ mb: 2 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="caption" color="text.secondary">
                    Question {currentQuestion + 1} of {examQuestions.length}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {examAnswers.filter(a => a !== null).length} answered
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={((currentQuestion + 1) / examQuestions.length) * 100}
                  sx={{ height: 6, borderRadius: 3, '& .MuiLinearProgress-bar': { bgcolor: '#1e3a5f' } }}
                />
              </Box>

              {/* Question dot indicators */}
              <Box sx={{ display: 'flex', gap: 0.75, mb: 3, flexWrap: 'wrap' }}>
                {examQuestions.map((_, qi) => (
                  <Box
                    key={qi}
                    onClick={() => setCurrentQuestion(qi)}
                    sx={{
                      width: 28, height: 28, borderRadius: '50%', cursor: 'pointer',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '0.7rem', fontWeight: 600,
                      bgcolor: qi === currentQuestion
                        ? '#1e3a5f'
                        : examAnswers[qi] !== null ? '#d1fae5' : '#f1f5f9',
                      color: qi === currentQuestion ? '#fff'
                        : examAnswers[qi] !== null ? '#065f46' : '#64748b',
                      border: qi === currentQuestion ? '2px solid #0f172a' : '2px solid transparent',
                      transition: 'all 0.15s',
                    }}
                  >
                    {qi + 1}
                  </Box>
                ))}
              </Box>

              {/* Current question */}
              <Box sx={{ p: 2.5, border: '1px solid #e2e8f0', borderRadius: 2, minHeight: 180 }}>
                <Typography variant="subtitle1" fontWeight={600} mb={2.5}>
                  {examQuestions[currentQuestion].question}
                </Typography>
                <RadioGroup
                  value={examAnswers[currentQuestion] !== null ? String(examAnswers[currentQuestion]) : ''}
                  onChange={(e) => {
                    const updated = [...examAnswers];
                    updated[currentQuestion] = parseInt(e.target.value);
                    setExamAnswers(updated);
                  }}
                >
                  {(examQuestions[currentQuestion].options || []).map((opt, oi) => (
                    <FormControlLabel
                      key={oi} value={String(oi)}
                      control={<Radio size="small" sx={{ color: '#1e3a5f', '&.Mui-checked': { color: '#1e3a5f' } }} />}
                      label={opt}
                      sx={{
                        mb: 0.5, px: 1, borderRadius: 1,
                        bgcolor: examAnswers[currentQuestion] === oi ? '#f0fdfa' : 'transparent',
                      }}
                    />
                  ))}
                </RadioGroup>
              </Box>
            </>
          )}
        </DialogContent>

        <DialogActions sx={{ px: 3, pb: 2.5, gap: 1 }}>
          {examResult ? (
            <>
              <Button onClick={closeExamDialog} sx={{ mr: 'auto' }}>Close</Button>
              {!examResult.passed && (
                <Button variant="contained" startIcon={<QuizIcon />} onClick={retryExam}
                  sx={{ bgcolor: '#7c3aed', '&:hover': { bgcolor: '#6d28d9' } }}>
                  Retry Exam
                </Button>
              )}
            </>
          ) : (
            <>
              <Button onClick={closeExamDialog} sx={{ mr: 'auto' }}>Cancel</Button>
              <Button
                variant="outlined"
                disabled={examLoading || currentQuestion === 0}
                onClick={() => setCurrentQuestion(q => q - 1)}
              >
                ← Previous
              </Button>
              {currentQuestion < examQuestions.length - 1 ? (
                <Button
                  variant="contained"
                  onClick={() => setCurrentQuestion(q => q + 1)}
                  sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}
                >
                  Next →
                </Button>
              ) : (
                <Button variant="contained" onClick={handleSubmitExam}
                  disabled={examLoading || examAnswers.some(a => a === null)}
                  sx={{ bgcolor: '#1e3a5f', '&:hover': { bgcolor: '#0f172a' } }}>
                  Submit Exam
                </Button>
              )}
            </>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CoursesPage;

