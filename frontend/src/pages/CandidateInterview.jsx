import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { candidateInterviewApi } from '../api/videoInterviewApi';

/* Inject keyframes once */
if (!document.getElementById('ci-styles')) {
  const style = document.createElement('style');
  style.id = 'ci-styles';
  style.textContent = `
    @keyframes spin { to { transform: rotate(360deg); } }
    @keyframes slideDown { from { transform: translateY(-100%); } to { transform: translateY(0); } }
    *, *::before, *::after { box-sizing: border-box; }
    button { font-family: inherit; }
    textarea { font-family: inherit; }
  `;
  document.head.appendChild(style);
}

/* ══════════════════════════════════════════════════════════════════════════
   Palette colour constants
══════════════════════════════════════════════════════════════════════════ */
const Q_COLORS = {
  answered:    { bg: '#22c55e', text: '#fff' },
  marked:      { bg: '#f59e0b', text: '#fff' },
  notAnswered: { bg: '#94a3b8', text: '#fff' },
  current:     { bg: '#3b82f6', text: '#fff' },
};

const s = {
  checking:         'checking',
  invalid:          'invalid',
  completed:        'completed',
  permissions:      'permissions',
  permissionDenied: 'permissionDenied',
  ready:            'ready',
  active:           'active',
  submitted:        'submitted',
};

/* ══════════════════════════════════════════════════════════════════════════
   Main component
══════════════════════════════════════════════════════════════════════════ */
export default function CandidateInterview() {
  const { token } = useParams();

  const [phase,         setPhase]         = useState(s.checking);
  const [info,          setInfo]          = useState(null);     // from validate
  const [questions,     setQuestions]     = useState([]);
  const [currentIdx,    setCurrentIdx]    = useState(0);
  const [answers,       setAnswers]       = useState({});       // { questionId: { answerText, selectedOption, isMarked } }
  const [timeRemaining, setTimeRemaining] = useState(null);     // seconds
  const [violationCount,setViolationCount]= useState(0);
  const [violationMsg,  setViolationMsg]  = useState('');
  const [showViolation, setShowViolation] = useState(false);
  const [networkStatus, setNetworkStatus] = useState('online');
  const [errorMsg,      setErrorMsg]      = useState('');
  const [submitting,    setSubmitting]    = useState(false);

  const videoRef       = useRef(null);
  const streamRef      = useRef(null);
  const recorderRef    = useRef(null);
  const chunksRef      = useRef([]);
  const timerRef       = useRef(null);
  const saveTimerRef   = useRef(null);
  const startedAtRef   = useRef(null);
  const durationRef    = useRef(45);
  const isSubmittingRef= useRef(false);
  const violationRef   = useRef(0);

  /* ── Validate token on mount ──────────────────────────────────────────── */
  useEffect(() => {
    candidateInterviewApi.validate(token)
      .then(data => {
        if (data.status === 'COMPLETED' || data.status === 'AUTO_SUBMITTED') {
          setPhase(s.completed);
          return;
        }
        setInfo(data);
        durationRef.current = data.durationMinutes || 45;
        setPhase(s.permissions);
      })
      .catch(err => {
        setErrorMsg(err.message || 'Invalid or expired interview link.');
        setPhase(s.invalid);
      });
  }, [token]);

  /* ── Anti-cheat event listeners (mounted during active phase) ─────────── */
  useEffect(() => {
    if (phase !== s.active) return;

    const handleViolation = (type, event, desc) => {
      if (isSubmittingRef.current) return;
      const newCount = violationRef.current + 1;
      violationRef.current = newCount;
      setViolationCount(newCount);

      candidateInterviewApi.logViolation(token, { violationType: type, browserEvent: event, description: desc })
        .then(res => {
          if (res?.autoSubmitted) {
            handleAutoSubmit('MAX_VIOLATIONS');
          } else {
            setViolationMsg(res?.warningMessage || `Warning ${newCount} of 3 — stay on this screen!`);
            setShowViolation(true);
            setTimeout(() => setShowViolation(false), 4000);
          }
        })
        .catch(() => {});
    };

    const onVisibilityChange = () => {
      if (document.hidden) handleViolation('TAB_SWITCH', 'visibilitychange', 'Tab switched or window minimized');
    };

    const onBlur = () => {
      handleViolation('FOCUS_LOST', 'blur', 'Browser window lost focus');
    };

    const onContextMenu = (e) => {
      e.preventDefault();
      handleViolation('RIGHT_CLICK', 'contextmenu', 'Right-click detected');
    };

    const onKeyDown = (e) => {
      const blocked = [
        e.key === 'F12',
        e.ctrlKey && e.shiftKey && ['I','J','C','K'].includes(e.key.toUpperCase()),
        e.ctrlKey && e.key.toLowerCase() === 'u',
        e.key === 'PrintScreen',
        e.metaKey,
      ];
      if (blocked.some(Boolean)) {
        e.preventDefault();
        handleViolation('KEYBOARD_SHORTCUT', 'keydown', `Blocked key: ${e.key}`);
      }
    };

    const onCopy = (e) => {
      e.preventDefault();
      handleViolation('COPY_PASTE', 'copy', 'Copy attempt detected');
    };

    const onFullscreenChange = () => {
      if (!document.fullscreenElement && phase === s.active) {
        handleViolation('FULLSCREEN_EXIT', 'fullscreenchange', 'Exited fullscreen mode');
      }
    };

    const onBeforeUnload = (e) => {
      e.preventDefault();
      e.returnValue = 'Leaving the interview will auto-submit. Are you sure?';
    };

    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('blur', onBlur);
    document.addEventListener('contextmenu', onContextMenu);
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('copy', onCopy);
    document.addEventListener('fullscreenchange', onFullscreenChange);
    window.addEventListener('beforeunload', onBeforeUnload);

    return () => {
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('blur', onBlur);
      document.removeEventListener('contextmenu', onContextMenu);
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('copy', onCopy);
      document.removeEventListener('fullscreenchange', onFullscreenChange);
      window.removeEventListener('beforeunload', onBeforeUnload);
    };
  }, [phase, token]);

  /* ── Network monitor ──────────────────────────────────────────────────── */
  useEffect(() => {
    const onOnline  = () => setNetworkStatus('online');
    const onOffline = () => setNetworkStatus('offline');
    window.addEventListener('online',  onOnline);
    window.addEventListener('offline', onOffline);
    return () => { window.removeEventListener('online', onOnline); window.removeEventListener('offline', onOffline); };
  }, []);

  /* ── Countdown timer ──────────────────────────────────────────────────── */
  useEffect(() => {
    if (phase !== s.active || timeRemaining === null) return;
    if (timeRemaining <= 0) { handleAutoSubmit('TIMER_EXPIRED'); return; }

    timerRef.current = setInterval(() => {
      setTimeRemaining(prev => {
        const next = prev - 1;
        if (next <= 0) {
          clearInterval(timerRef.current);
          handleAutoSubmit('TIMER_EXPIRED');
          return 0;
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(timerRef.current);
  }, [phase, timeRemaining === null ? null : Math.floor(timeRemaining / 60)]); // restart only on phase change

  /* ── Auto-save every 5 seconds ────────────────────────────────────────── */
  useEffect(() => {
    if (phase !== s.active) return;
    saveTimerRef.current = setInterval(() => {
      const q = questions[currentIdx];
      if (!q) return;
      const ans = answers[q.questionId];
      if (!ans) return;
      candidateInterviewApi.saveAnswer(token, { questionId: q.questionId, ...ans }).catch(() => {});
    }, 5000);
    return () => clearInterval(saveTimerRef.current);
  }, [phase, currentIdx, answers, questions, token]);

  /* ── Camera & mic permission request ─────────────────────────────────── */
  const requestPermissions = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
      setPhase(s.ready);
    } catch {
      setPhase(s.permissionDenied);
    }
  }, []);

  /* ── Start interview ──────────────────────────────────────────────────── */
  const startInterview = useCallback(async () => {
    try {
      // Enter fullscreen
      if (document.documentElement.requestFullscreen) {
        await document.documentElement.requestFullscreen().catch(() => {});
      }

      const data = await candidateInterviewApi.start(token);
      startedAtRef.current = data.startedAt ? new Date(data.startedAt) : new Date();
      const elapsed = Math.floor((Date.now() - startedAtRef.current.getTime()) / 1000);
      const remaining = Math.max(0, (data.durationMinutes || 45) * 60 - elapsed);

      // Restore saved answers if resuming
      const restored = {};
      if (data.savedAnswers) {
        data.savedAnswers.forEach(a => {
          restored[a.questionId] = { answerText: a.answerText || '', selectedOption: a.selectedOption || '', isMarked: a.isMarked || false };
        });
      }

      setQuestions(data.questions || []);
      setAnswers(restored);
      setTimeRemaining(remaining);
      setCurrentIdx(0);
      setPhase(s.active);

      // Start recording
      startRecording();
    } catch (err) {
      alert(err.message || 'Failed to start interview. Please try again.');
    }
  }, [token]);

  /* ── Video recording ──────────────────────────────────────────────────── */
  const startRecording = () => {
    const stream = streamRef.current;
    if (!stream || !window.MediaRecorder) return;
    try {
      const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
          ? 'video/webm;codecs=vp9' : 'video/webm';
      const recorder = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];
      recorder.ondataavailable = e => { if (e.data.size > 0) chunksRef.current.push(e.data); };
      recorder.start(30000);
      recorderRef.current = recorder;
    } catch { /* recording not supported — continue without it */ }
  };

  const stopAndUploadRecording = async () => {
    const recorder = recorderRef.current;
    if (!recorder || recorder.state === 'inactive') return;
    return new Promise(resolve => {
      recorder.onstop = async () => {
        const blob = new Blob(chunksRef.current, { type: 'video/webm' });
        if (blob.size > 0) {
          await candidateInterviewApi.uploadRecording(token, blob).catch(() => {});
        }
        resolve();
      };
      recorder.stop();
    });
  };

  const stopStream = () => {
    streamRef.current?.getTracks().forEach(t => t.stop());
  };

  /* ── Submit ───────────────────────────────────────────────────────────── */
  const handleSubmit = useCallback(async (confirmed = false) => {
    if (isSubmittingRef.current) return;
    if (!confirmed && !window.confirm('Submit your interview? This action cannot be undone.')) return;
    doSubmit();
  }, []);

  const handleAutoSubmit = useCallback((reason) => {
    if (isSubmittingRef.current) return;
    doSubmit(reason);
  }, []);

  const doSubmit = async (reason) => {
    if (isSubmittingRef.current) return;
    isSubmittingRef.current = true;
    setSubmitting(true);
    clearInterval(timerRef.current);
    clearInterval(saveTimerRef.current);

    // Save current answer one last time
    const q = questions[currentIdx];
    const ans = q ? answers[q.questionId] : null;
    if (q && ans) {
      await candidateInterviewApi.saveAnswer(token, { questionId: q.questionId, ...ans }).catch(() => {});
    }

    // Upload recording & stop stream
    await stopAndUploadRecording();
    stopStream();

    // Submit to backend
    await candidateInterviewApi.submit(token).catch(() => {});

    // Exit fullscreen
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});

    setPhase(s.submitted);
    setSubmitting(false);
  };

  /* ── Answer helpers ───────────────────────────────────────────────────── */
  const getAnswer = (questionId) => answers[questionId] || { answerText: '', selectedOption: '', isMarked: false };

  const setAnswer = (questionId, patch) => {
    setAnswers(prev => ({ ...prev, [questionId]: { ...getAnswer(questionId), ...patch } }));
  };

  const saveCurrentAndGo = (newIdx) => {
    const q = questions[currentIdx];
    if (q) {
      const ans = answers[q.questionId];
      if (ans) candidateInterviewApi.saveAnswer(token, { questionId: q.questionId, ...ans }).catch(() => {});
    }
    setCurrentIdx(newIdx);
  };

  /* ── Timer formatting ─────────────────────────────────────────────────── */
  const fmtTime = (secs) => {
    if (secs == null) return '--:--';
    const m = Math.floor(Math.max(0, secs) / 60);
    const s2 = Math.max(0, secs) % 60;
    return `${String(m).padStart(2,'0')}:${String(s2).padStart(2,'0')}`;
  };

  const timerColor = timeRemaining != null && timeRemaining < 300 ? '#ef4444' : timeRemaining != null && timeRemaining < 600 ? '#f59e0b' : '#22c55e';

  /* ── Palette state ────────────────────────────────────────────────────── */
  const getPaletteColor = (idx) => {
    const q = questions[idx];
    if (!q) return Q_COLORS.notAnswered;
    if (idx === currentIdx)                                   return Q_COLORS.current;
    const ans = answers[q.questionId];
    if (ans?.isMarked)                                        return Q_COLORS.marked;
    if (ans?.answerText?.trim() || ans?.selectedOption)       return Q_COLORS.answered;
    return Q_COLORS.notAnswered;
  };

  const answeredCount = questions.filter(q => {
    const ans = answers[q.questionId];
    return ans?.answerText?.trim() || ans?.selectedOption;
  }).length;

  const markedCount = questions.filter(q => answers[q.questionId]?.isMarked).length;

  /* ══════════════════════════════════════════════════════════════════════
     Phase renders
  ══════════════════════════════════════════════════════════════════════ */

  if (phase === s.checking) return <CenteredScreen><Spinner /><Msg>Verifying your interview link…</Msg></CenteredScreen>;

  if (phase === s.invalid)  return (
    <CenteredScreen>
      <Icon>❌</Icon>
      <Heading>Invalid Interview Link</Heading>
      <Msg>{errorMsg || 'This interview link is invalid or has expired.'}</Msg>
    </CenteredScreen>
  );

  if (phase === s.completed) return (
    <CenteredScreen>
      <Icon>✅</Icon>
      <Heading>Already Submitted</Heading>
      <Msg>This interview has already been completed. Thank you!</Msg>
    </CenteredScreen>
  );

  if (phase === s.submitted) return (
    <CenteredScreen>
      <Icon>🎉</Icon>
      <Heading>Interview Submitted!</Heading>
      <Msg>Your responses have been recorded. We will get back to you soon.</Msg>
      <Msg style={{ fontSize: 14, color: '#94a3b8', marginTop: 8 }}>You can close this window.</Msg>
    </CenteredScreen>
  );

  if (phase === s.permissionDenied) return (
    <CenteredScreen>
      <Icon>🎥</Icon>
      <Heading>Camera / Microphone Required</Heading>
      <Msg>Please allow camera and microphone access in your browser settings, then refresh the page.</Msg>
    </CenteredScreen>
  );

  if (phase === s.permissions) return (
    <CenteredScreen>
      <Icon>🎥</Icon>
      <Heading style={{ fontSize: 26 }}>Welcome, {info?.candidateName}</Heading>
      <div style={{ maxWidth: 540, textAlign: 'center', color: '#cbd5e1', marginBottom: 24 }}>
        <p style={{ margin: '0 0 8px' }}>
          <strong style={{ color: '#e2e8f0' }}>{info?.position || info?.technology}</strong> · {info?.durationMinutes} minutes · {info?.numQuestions} questions
        </p>
        <p style={{ margin: '0 0 16px', fontSize: 14 }}>
          This interview is video-recorded and monitored. Switching tabs, losing focus, or exiting fullscreen will be flagged as violations. After 3 violations, your interview will be auto-submitted.
        </p>
      </div>
      <div style={{ background: '#1e2d45', borderRadius: 12, padding: 20, marginBottom: 24, width: 320 }}>
        <div style={{ fontSize: 13, color: '#94a3b8', marginBottom: 12, fontWeight: 600, textTransform: 'uppercase', letterSpacing: 1 }}>Before you start</div>
        {['Use Chrome or Firefox on desktop','Allow camera & microphone access','Ensure stable internet connection','Do not switch tabs or minimise the window','Keep your face visible in the camera throughout'].map((item, i) => (
          <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
            <span style={{ color: '#22c55e', flexShrink: 0 }}>✓</span>
            <span style={{ fontSize: 14, color: '#e2e8f0' }}>{item}</span>
          </div>
        ))}
      </div>
      <button onClick={requestPermissions} style={btnStyle('#3b82f6')}>
        Allow Camera & Microphone →
      </button>
    </CenteredScreen>
  );

  if (phase === s.ready) return (
    <CenteredScreen>
      {/* Small camera preview */}
      <video ref={videoRef} autoPlay muted playsInline
        style={{ width: 240, height: 180, borderRadius: 12, objectFit: 'cover', border: '2px solid #22c55e', marginBottom: 20, transform: 'scaleX(-1)' }} />
      <Heading>Camera Active ✓</Heading>
      <Msg>Looking good! Click below to begin. The interview will enter fullscreen mode.</Msg>
      <button onClick={startInterview} style={{ ...btnStyle('#22c55e'), marginTop: 20, fontSize: 17, padding: '14px 40px' }}>
        🚀 Start Interview
      </button>
    </CenteredScreen>
  );

  /* ══════════════════════════════════════════════════════════════════════
     ACTIVE INTERVIEW
  ══════════════════════════════════════════════════════════════════════ */
  const currentQ = questions[currentIdx];
  const currentAns = currentQ ? getAnswer(currentQ.questionId) : {};

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: '#0f172a', color: '#e2e8f0', overflow: 'hidden', userSelect: 'none' }}>

      {/* ── Violation warning banner ── */}
      {showViolation && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 9999, background: '#dc2626', color: '#fff', padding: '10px 20px', textAlign: 'center', fontWeight: 700, fontSize: 15, animation: 'slideDown 0.3s ease' }}>
          ⚠️ {violationMsg}
        </div>
      )}

      {/* ── Header ── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 20px', background: '#1e2d45', borderBottom: '1px solid #1e3a5f', flexShrink: 0, height: 58 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, color: '#e2e8f0' }}>{info?.candidateName}</div>
            <div style={{ fontSize: 12, color: '#64748b' }}>{info?.position} · {info?.technology}</div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          {/* Network */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: networkStatus === 'online' ? '#22c55e' : '#ef4444' }} />
            <span style={{ fontSize: 12, color: '#94a3b8' }}>{networkStatus === 'online' ? 'Online' : 'Offline'}</span>
          </div>

          {/* Camera */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <span style={{ fontSize: 16 }}>📷</span>
            <span style={{ fontSize: 12, color: '#22c55e' }}>Camera On</span>
          </div>

          {/* Violations */}
          {violationCount > 0 && (
            <div style={{ background: '#7f1d1d', border: '1px solid #dc2626', borderRadius: 6, padding: '3px 10px', fontSize: 12, color: '#fca5a5', fontWeight: 700 }}>
              ⚠️ Violations: {violationCount}/3
            </div>
          )}

          {/* Questions left */}
          <div style={{ fontSize: 13, color: '#94a3b8' }}>
            Q {currentIdx + 1}/{questions.length} &nbsp;·&nbsp;
            <span style={{ color: '#22c55e' }}>{answeredCount} answered</span>
          </div>

          {/* Timer */}
          <div style={{ background: '#0f172a', borderRadius: 8, padding: '4px 14px', border: `2px solid ${timerColor}` }}>
            <span style={{ fontSize: 20, fontWeight: 800, fontFamily: 'monospace', color: timerColor, letterSpacing: 2 }}>
              {fmtTime(timeRemaining)}
            </span>
          </div>
        </div>
      </div>

      {/* ── Body ── */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>

        {/* ── Left: Question palette + list ── */}
        <div style={{ width: 200, background: '#1e2d45', borderRight: '1px solid #1e3a5f', display: 'flex', flexDirection: 'column', padding: 12, overflowY: 'auto', flexShrink: 0 }}>
          <div style={{ fontSize: 11, color: '#64748b', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1, marginBottom: 10 }}>Question Palette</div>

          {/* Color legend */}
          <div style={{ marginBottom: 12 }}>
            {[
              { color: Q_COLORS.answered.bg,    label: 'Answered' },
              { color: Q_COLORS.marked.bg,      label: 'Marked' },
              { color: Q_COLORS.notAnswered.bg, label: 'Not Answered' },
              { color: Q_COLORS.current.bg,     label: 'Current' },
            ].map(({ color, label }) => (
              <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                <div style={{ width: 12, height: 12, borderRadius: 3, background: color, flexShrink: 0 }} />
                <span style={{ fontSize: 11, color: '#94a3b8' }}>{label}</span>
              </div>
            ))}
          </div>

          {/* Question number grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 4 }}>
            {questions.map((_, idx) => {
              const clr = getPaletteColor(idx);
              return (
                <button key={idx} onClick={() => saveCurrentAndGo(idx)}
                  style={{ width: 36, height: 36, borderRadius: 6, border: 'none', cursor: 'pointer',
                           background: clr.bg, color: clr.text, fontWeight: 700, fontSize: 12,
                           boxShadow: idx === currentIdx ? '0 0 0 2px #fff' : 'none' }}>
                  {idx + 1}
                </button>
              );
            })}
          </div>

          <div style={{ marginTop: 'auto', paddingTop: 12, borderTop: '1px solid #1e3a5f' }}>
            <div style={{ fontSize: 11, color: '#64748b', marginBottom: 4 }}>Summary</div>
            <div style={{ fontSize: 12, color: '#22c55e' }}>{answeredCount} answered</div>
            <div style={{ fontSize: 12, color: '#f59e0b' }}>{markedCount} marked</div>
            <div style={{ fontSize: 12, color: '#94a3b8' }}>{questions.length - answeredCount} remaining</div>
          </div>
        </div>

        {/* ── Right: Question panel ── */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

          {/* Camera preview (corner) */}
          <div style={{ position: 'absolute', top: 68, right: 16, zIndex: 100, borderRadius: 8, overflow: 'hidden', border: '2px solid #22c55e', width: 160, height: 120, background: '#000' }}>
            <video ref={videoRef} autoPlay muted playsInline
              style={{ width: '100%', height: '100%', objectFit: 'cover', transform: 'scaleX(-1)' }} />
          </div>

          {/* Question content */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '24px 32px 24px 24px' }}>
            {!currentQ ? (
              <div style={{ textAlign: 'center', color: '#64748b', marginTop: 60 }}>No questions loaded</div>
            ) : (
              <div style={{ maxWidth: 780 }}>
                {/* Question header */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
                  <div style={{ background: '#1e3a5f', color: '#fff', borderRadius: '50%', width: 36, height: 36, display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 16, flexShrink: 0 }}>
                    {currentIdx + 1}
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <span style={{ ...badgeStyle('#1e3a5f', '#60a5fa') }}>{currentQ.difficulty}</span>
                    <span style={{ ...badgeStyle('#1e3a5f', '#a78bfa') }}>{currentQ.questionType}</span>
                    {currentQ.category && <span style={{ ...badgeStyle('#1e3a5f', '#94a3b8') }}>{currentQ.category}</span>}
                    <span style={{ ...badgeStyle('#1e3a5f', '#22c55e') }}>{currentQ.marks} pts</span>
                  </div>
                  {currentAns.isMarked && <span style={{ ...badgeStyle('#78350f', '#fbbf24') }}>★ Marked</span>}
                </div>

                {/* Question text */}
                <div style={{ fontSize: 18, fontWeight: 600, lineHeight: 1.6, marginBottom: 28, color: '#f1f5f9', whiteSpace: 'pre-wrap' }}>
                  {currentQ.questionText}
                </div>

                {/* Answer area */}
                {currentQ.questionType === 'MCQ' ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {(currentQ.options || []).map((opt, i) => {
                      const isSelected = currentAns.selectedOption === opt.letter;
                      return (
                        <button key={opt.letter} onClick={() => setAnswer(currentQ.questionId, { selectedOption: opt.letter })}
                          style={{ display: 'flex', alignItems: 'flex-start', gap: 14, padding: '14px 18px', borderRadius: 10,
                                   border: isSelected ? '2px solid #3b82f6' : '1px solid #1e3a5f',
                                   background: isSelected ? '#1e3a5f' : '#1e2d45', cursor: 'pointer', textAlign: 'left',
                                   transition: 'all 0.15s', color: '#e2e8f0' }}>
                          <div style={{ width: 28, height: 28, borderRadius: '50%', border: isSelected ? '2px solid #3b82f6' : '2px solid #475569',
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        fontWeight: 700, fontSize: 13, flexShrink: 0, color: isSelected ? '#3b82f6' : '#94a3b8',
                                        background: isSelected ? '#1e40af22' : 'transparent' }}>
                            {String.fromCharCode(65 + i)}
                          </div>
                          <span style={{ fontSize: 15, lineHeight: 1.5, marginTop: 2 }}>{opt.text}</span>
                        </button>
                      );
                    })}
                  </div>
                ) : (
                  <textarea
                    value={currentAns.answerText || ''}
                    onChange={e => setAnswer(currentQ.questionId, { answerText: e.target.value })}
                    placeholder="Type your answer here…"
                    style={{ width: '100%', minHeight: 200, padding: 16, borderRadius: 10, border: '1px solid #1e3a5f',
                             background: '#1e2d45', color: '#e2e8f0', fontSize: 15, lineHeight: 1.6,
                             resize: 'vertical', outline: 'none', fontFamily: 'inherit', boxSizing: 'border-box' }}
                    onFocus={e => { e.target.style.borderColor = '#3b82f6'; }}
                    onBlur={e => { e.target.style.borderColor = '#1e3a5f'; }}
                  />
                )}
              </div>
            )}
          </div>

          {/* ── Bottom navigation ── */}
          <div style={{ borderTop: '1px solid #1e3a5f', background: '#1e2d45', padding: '12px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
            <button disabled={currentIdx === 0} onClick={() => saveCurrentAndGo(currentIdx - 1)} style={navBtnStyle(currentIdx === 0)}>
              ← Previous
            </button>

            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={() => {
                const q = questions[currentIdx];
                if (q) { setAnswer(q.questionId, { isMarked: !currentAns.isMarked }); }
              }} style={{ ...navBtnStyle(false), background: currentAns.isMarked ? '#78350f' : '#1e2d45', border: currentAns.isMarked ? '1px solid #f59e0b' : '1px solid #475569', color: currentAns.isMarked ? '#fbbf24' : '#94a3b8' }}>
                {currentAns.isMarked ? '★ Unmark' : '☆ Mark for Review'}
              </button>

              <button onClick={() => {
                const q = questions[currentIdx];
                if (q) candidateInterviewApi.saveAnswer(token, { questionId: q.questionId, ...currentAns }).catch(() => {});
              }} style={{ ...navBtnStyle(false), background: '#1e3a5f', border: '1px solid #3b82f6', color: '#60a5fa' }}>
                💾 Save
              </button>
            </div>

            <div style={{ display: 'flex', gap: 8 }}>
              <button disabled={currentIdx === questions.length - 1} onClick={() => saveCurrentAndGo(currentIdx + 1)} style={navBtnStyle(currentIdx === questions.length - 1)}>
                Next →
              </button>

              <button onClick={() => handleSubmit(false)} disabled={submitting}
                style={{ ...navBtnStyle(false), background: submitting ? '#374151' : '#dc2626', border: '1px solid #dc2626', color: '#fff', fontWeight: 700 }}>
                {submitting ? '⏳ Submitting…' : '✅ Submit'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════════════
   Shared style helpers
══════════════════════════════════════════════════════════════════════════ */
const CenteredScreen = ({ children }) => (
  <div style={{ minHeight: '100vh', background: '#0f172a', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 32 }}>
    {children}
  </div>
);

const Spinner = () => (
  <div style={{ width: 48, height: 48, border: '4px solid #1e3a5f', borderTopColor: '#3b82f6', borderRadius: '50%', animation: 'spin 0.8s linear infinite', marginBottom: 20 }} />
);

const Icon = ({ children }) => <div style={{ fontSize: 64, marginBottom: 16 }}>{children}</div>;
const Heading = ({ children, style }) => <h1 style={{ color: '#e2e8f0', fontWeight: 800, fontSize: 30, margin: '0 0 12px', textAlign: 'center', ...style }}>{children}</h1>;
const Msg = ({ children, style }) => <p style={{ color: '#94a3b8', fontSize: 16, textAlign: 'center', maxWidth: 500, margin: '0 0 8px', lineHeight: 1.6, ...style }}>{children}</p>;

const btnStyle = (bg) => ({
  background: bg, color: '#fff', border: 'none', borderRadius: 10, padding: '12px 32px',
  fontSize: 16, fontWeight: 700, cursor: 'pointer',
});

const navBtnStyle = (disabled) => ({
  padding: '8px 20px', borderRadius: 8, border: '1px solid #475569',
  background: disabled ? '#1a1f2e' : '#1e2d45', color: disabled ? '#374151' : '#e2e8f0',
  cursor: disabled ? 'not-allowed' : 'pointer', fontSize: 14, fontWeight: 600,
});

const badgeStyle = (bg, color) => ({
  background: bg, color, padding: '3px 10px', borderRadius: 999,
  fontSize: 12, fontWeight: 700, border: `1px solid ${color}22`,
  display: 'inline-block',
});
