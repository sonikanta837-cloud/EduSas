import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';

/* ─────────────────── API helpers (no JWT) ─────────────────────────────────── */
const BASE = process.env.REACT_APP_API_URL || '/api';
const pub = async (method, path, body) => {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(`${BASE}/interviews${path}`, opts);
  if (!res.ok) throw new Error(await res.text());
  const ct = res.headers.get('content-type') || '';
  if (res.status === 204 || !ct.includes('application/json')) return null;
  return res.json();
};
const uploadBlob = async (token, blob) => {
  const fd = new FormData();
  fd.append('file', blob, 'recording.webm');
  await fetch(`${BASE}/interviews/technical/candidate/${token}/recording`, { method: 'POST', body: fd });
};

/* ─────────────────── Inline CSS ──────────────────────────────────────────── */
const injectStyles = () => {
  if (document.getElementById('tir-styles')) return;
  const el = document.createElement('style');
  el.id = 'tir-styles';
  el.textContent = `
    *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
    body{font-family:'Segoe UI',Arial,sans-serif;background:#0a0f1e;color:#e2e8f0}
    @keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
    @keyframes spin{to{transform:rotate(360deg)}}
    @keyframes slideIn{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}
    @keyframes timerWarn{0%,100%{color:#f59e0b}50%{color:#ef4444}}
    .spinner{width:40px;height:40px;border:4px solid rgba(99,102,241,.3);border-top-color:#6366f1;border-radius:50%;animation:spin 1s linear infinite}
    .pulse{animation:pulse 1.5s ease-in-out infinite}
    .slide-in{animation:slideIn .4s ease}
    .timer-warn{animation:timerWarn 1s ease-in-out infinite}
    ::-webkit-scrollbar{width:6px}::-webkit-scrollbar-track{background:#1e2435}::-webkit-scrollbar-thumb{background:#4a5568;border-radius:3px}
  `;
  document.head.appendChild(el);
};

/* ─────────────────── Constants ────────────────────────────────────────────── */
const STUN_CONFIG = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }] };
const PALETTE_COLORS = {
  answered:   { bg: '#059669', border: '#047857' },
  current:    { bg: '#6366f1', border: '#4f46e5' },
  flagged:    { bg: '#f59e0b', border: '#d97706' },
  unanswered: { bg: '#1e2d4a', border: '#334155' },
};

/* ═══════════════════════════════════════════════════════════════════════════ */
export default function TechnicalInterviewRoom() {
  const { token } = useParams();

  /* ── Phase state machine ── */
  const [phase,   setPhase]   = useState('validating'); // validating|permissions|ready|active|submitted|invalid|completed
  const [room,    setRoom]    = useState(null);
  const [error,   setError]   = useState('');

  /* ── Interview state ── */
  const [currentQ,  setCurrentQ]  = useState(0);
  const [answers,   setAnswers]   = useState({});   // { [questionId]: { answerText, selectedOption } }
  const [flagged,   setFlagged]   = useState({});   // { [questionId]: true }
  const [score,     setScore]     = useState(null);
  const [totalMarks,setTotalMarks]= useState(null);

  /* ── Timer ── */
  const [secsLeft, setSecsLeft] = useState(0);
  const timerRef = useRef(null);

  /* ── Violation tracking ── */
  const violationRef = useRef(0);
  const isSubmittingRef = useRef(false);
  const [violations, setViolations] = useState(0);
  const [violationMsg, setViolationMsg] = useState('');

  /* ── Media / WebRTC ── */
  const localVideoRef  = useRef(null);
  const localStreamRef = useRef(null);
  const pcRef          = useRef(null);
  const mediaRecorder  = useRef(null);
  const recordedChunks = useRef([]);

  /* ── Auto-save interval ── */
  const autoSaveRef = useRef(null);
  const lastSavedRef = useRef({});

  /* ────────────── INIT: Validate token ────────────────────────────────── */
  useEffect(() => {
    injectStyles();
    (async () => {
      try {
        const data = await pub('GET', `/technical/candidate/${token}`);
        if (data.interviewStatus === 'CANDIDATE_SUBMITTED' || data.interviewStatus === 'EVALUATED') {
          setPhase('completed'); return;
        }
        const restored = {};
        (data.savedAnswers || []).forEach(a => {
          restored[a.questionId] = { answerText: a.answerText || '', selectedOption: a.selectedOption || '' };
        });
        setAnswers(restored);
        setRoom(data);
        setSecsLeft((data.durationMinutes || 45) * 60);
        if (data.interviewStatus === 'IN_PROGRESS') {
          // Resume interrupted session
          const elapsed = data.startedAt ? Math.floor((Date.now() - new Date(data.startedAt).getTime()) / 1000) : 0;
          const remaining = (data.durationMinutes || 45) * 60 - elapsed;
          setSecsLeft(Math.max(remaining, 0));
        }
        setPhase('permissions');
      } catch {
        setPhase('invalid');
      }
    })();
    return () => cleanup();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /* ────────────── Camera/Mic permissions ─────────────────────────────── */
  const requestPermissions = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      localStreamRef.current = stream;
      if (localVideoRef.current) {
        localVideoRef.current.srcObject = stream;
        localVideoRef.current.muted = true;
      }
      setPhase('ready');
    } catch {
      setError('Camera and microphone access is required to start the interview.');
    }
  };

  /* ────────────── Start interview ──────────────────────────────────────── */
  const startInterview = async () => {
    await document.documentElement.requestFullscreen().catch(() => {});
    await pub('POST', `/technical/candidate/${token}/start`);
    startRecording();
    setupWebRTC();
    setPhase('active');
    startTimer();
    startAutoSave();
    mountAntiCheat();
  };

  /* ────────────── Timer ────────────────────────────────────────────────── */
  const startTimer = () => {
    timerRef.current = setInterval(() => {
      setSecsLeft(s => {
        if (s <= 1) { clearInterval(timerRef.current); handleSubmit(true); return 0; }
        return s - 1;
      });
    }, 1000);
  };

  /* ────────────── Auto-save ────────────────────────────────────────────── */
  const startAutoSave = () => {
    autoSaveRef.current = setInterval(() => { flushPendingAnswers(); }, 5000);
  };

  const flushPendingAnswers = useCallback(() => {
    setAnswers(current => {
      Object.entries(current).forEach(([qid, ans]) => {
        const last = lastSavedRef.current[qid];
        const changed = !last ||
          last.answerText !== ans.answerText ||
          last.selectedOption !== ans.selectedOption;
        if (changed) {
          pub('POST', `/technical/candidate/${token}/answer`, {
            questionId: Number(qid),
            answerText: ans.answerText || null,
            selectedOption: ans.selectedOption || null,
          }).catch(() => {});
          lastSavedRef.current[qid] = { ...ans };
        }
      });
      return current;
    });
  }, [token]);

  /* ────────────── Recording ────────────────────────────────────────────── */
  const startRecording = () => {
    if (!localStreamRef.current) return;
    try {
      const mr = new MediaRecorder(localStreamRef.current, { mimeType: 'video/webm;codecs=vp8,opus' });
      mr.ondataavailable = e => { if (e.data.size > 0) recordedChunks.current.push(e.data); };
      mr.start(30000); // 30-second chunks
      mediaRecorder.current = mr;
    } catch { /* MediaRecorder not supported — silently skip */ }
  };

  const stopAndUploadRecording = async () => {
    return new Promise(resolve => {
      if (!mediaRecorder.current || mediaRecorder.current.state === 'inactive') { resolve(); return; }
      mediaRecorder.current.onstop = async () => {
        if (recordedChunks.current.length > 0) {
          const blob = new Blob(recordedChunks.current, { type: 'video/webm' });
          await uploadBlob(token, blob).catch(() => {});
        }
        resolve();
      };
      mediaRecorder.current.stop();
    });
  };

  /* ────────────── WebRTC (candidate creates offer → manager joins) ─────── */
  const setupWebRTC = async () => {
    try {
      const pc = new RTCPeerConnection(STUN_CONFIG);
      pcRef.current = pc;
      localStreamRef.current.getTracks().forEach(t => pc.addTrack(t, localStreamRef.current));

      pc.onicecandidate = async e => {
        if (e.candidate) {
          await pub('POST', `/technical/candidate/${token}/signal/ice`,
            { ice: JSON.stringify(e.candidate) }).catch(() => {});
        }
      };

      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await pub('POST', `/technical/candidate/${token}/signal/offer`, { sdp: JSON.stringify(offer) });

      // Poll for manager's answer
      let polls = 0;
      const pollAnswer = setInterval(async () => {
        if (++polls > 60) { clearInterval(pollAnswer); return; }
        try {
          const res = await pub('GET', `/technical/candidate/${token}/signal/answer`);
          if (res.sdp) {
            clearInterval(pollAnswer);
            await pc.setRemoteDescription(JSON.parse(res.sdp));
            startPollingManagerIce(pc);
          }
        } catch { /* retry */ }
      }, 3000);
    } catch { /* WebRTC not supported — record-only fallback */ }
  };

  const startPollingManagerIce = (pc) => {
    let seen = 0;
    const poll = setInterval(async () => {
      try {
        const list = await pub('GET', `/technical/candidate/${token}/signal/ice/manager`);
        if (list.length > seen) {
          for (let i = seen; i < list.length; i++) {
            await pc.addIceCandidate(JSON.parse(list[i])).catch(() => {});
          }
          seen = list.length;
        }
      } catch { /* retry */ }
    }, 3000);
    // Stop after 2 min — handshake should complete by then
    setTimeout(() => clearInterval(poll), 120000);
  };

  /* ────────────── Anti-cheat ───────────────────────────────────────────── */
  const mountAntiCheat = () => {
    const flag = (type, desc) => {
      violationRef.current += 1;
      const count = violationRef.current;
      setViolations(count);
      const msg = count >= 3 ? 'Too many violations — interview auto-submitted.' : `Warning ${count}/3: ${desc}`;
      setViolationMsg(msg);
      setTimeout(() => setViolationMsg(''), 5000);
      pub('POST', `/technical/candidate/${token}/violation`,
        { violationType: type, description: desc }).then(r => {
          if (r?.autoSubmitted) handleSubmit(true);
        }).catch(() => {});
    };

    const onVisibility = () => { if (document.hidden) flag('TAB_SWITCH', 'Candidate switched browser tab'); };
    const onBlur       = () => flag('WINDOW_BLUR', 'Browser window lost focus');
    const onContextMenu = e => { e.preventDefault(); flag('RIGHT_CLICK', 'Right-click attempted'); };
    const onCopy       = e => { e.preventDefault(); flag('COPY_ATTEMPT', 'Text copy attempted'); };
    const onFullscreen = () => {
      if (!document.fullscreenElement) {
        flag('FULLSCREEN_EXIT', 'Exited fullscreen mode');
        document.documentElement.requestFullscreen().catch(() => {});
      }
    };
    const onKey = e => {
      const blocked = e.key === 'F12' || (e.ctrlKey && e.shiftKey && e.key === 'I') ||
                      (e.ctrlKey && e.shiftKey && e.key === 'J') || (e.ctrlKey && e.key === 'u') ||
                      e.key === 'PrintScreen' || (e.ctrlKey && e.key === 'c') || (e.altKey && e.key === 'Tab');
      if (blocked) { e.preventDefault(); flag('KEY_BLOCK', `Blocked key: ${e.key}`); }
    };
    const onBeforeUnload = e => { e.preventDefault(); e.returnValue = ''; };

    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('blur', onBlur);
    document.addEventListener('contextmenu', onContextMenu);
    document.addEventListener('copy', onCopy);
    document.addEventListener('fullscreenchange', onFullscreen);
    document.addEventListener('keydown', onKey);
    window.addEventListener('beforeunload', onBeforeUnload);

    // Cleanup stored on ref so we can remove on unmount
    window._tirCleanup = () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('blur', onBlur);
      document.removeEventListener('contextmenu', onContextMenu);
      document.removeEventListener('copy', onCopy);
      document.removeEventListener('fullscreenchange', onFullscreen);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('beforeunload', onBeforeUnload);
    };
  };

  /* ────────────── Submit ───────────────────────────────────────────────── */
  const handleSubmit = async (auto = false) => {
    if (isSubmittingRef.current) return;
    if (!auto && phase === 'active') {
      const answered = Object.keys(answers).length;
      const total    = room?.questions?.length || 0;
      if (answered < total) {
        const ok = window.confirm(`You have answered ${answered}/${total} questions. Submit anyway?`);
        if (!ok) return;
      }
    }
    isSubmittingRef.current = true;
    clearInterval(timerRef.current);
    clearInterval(autoSaveRef.current);
    if (window._tirCleanup) window._tirCleanup();

    // Flush remaining answers
    const pendingFlushes = Object.entries(answers).map(([qid, ans]) =>
      pub('POST', `/technical/candidate/${token}/answer`, {
        questionId: Number(qid),
        answerText: ans.answerText || null,
        selectedOption: ans.selectedOption || null,
      }).catch(() => {})
    );
    await Promise.all(pendingFlushes);

    // Stop recording and upload
    await stopAndUploadRecording();

    // Submit
    try {
      const result = await pub('POST', `/technical/candidate/${token}/submit`);
      setScore(result.score);
      setTotalMarks(result.totalMarks);
    } catch { /* already submitted */ }

    // Stop stream + exit fullscreen
    if (localStreamRef.current) localStreamRef.current.getTracks().forEach(t => t.stop());
    if (pcRef.current) { pcRef.current.close(); pcRef.current = null; }
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});

    setPhase('submitted');
  };

  const cleanup = () => {
    clearInterval(timerRef.current);
    clearInterval(autoSaveRef.current);
    if (window._tirCleanup) window._tirCleanup();
    if (localStreamRef.current) localStreamRef.current.getTracks().forEach(t => t.stop());
    if (pcRef.current) { pcRef.current.close(); pcRef.current = null; }
  };

  /* ────────────── Answer helpers ──────────────────────────────────────── */
  const currentQuestion = room?.questions?.[currentQ];

  const setAnswer = (questionId, field, value) => {
    setAnswers(a => ({ ...a, [questionId]: { ...(a[questionId] || {}), [field]: value } }));
  };

  const getAnswerStatus = (q) => {
    const a = answers[q.questionId];
    if (!a) return 'unanswered';
    if (q.questionType === 'MCQ' && a.selectedOption) return 'answered';
    if (q.questionType === 'TEXT' && a.answerText && a.answerText.trim()) return 'answered';
    return 'unanswered';
  };

  const saveCurrentAndNavigate = (idx) => {
    flushPendingAnswers();
    setCurrentQ(idx);
  };

  /* ────────────── Timer display ────────────────────────────────────────── */
  const mins = Math.floor(secsLeft / 60);
  const secs = secsLeft % 60;
  const timerCritical = secsLeft < 300 && secsLeft > 0; // last 5 minutes
  const timerStr = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;

  /* ────────────── Render ───────────────────────────────────────────────── */
  const s = {
    root:    { minHeight: '100vh', background: '#0a0f1e', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 24, fontFamily: "'Segoe UI', Arial, sans-serif" },
    card:    { background: '#111827', borderRadius: 16, padding: '40px 48px', maxWidth: 480, width: '100%', textAlign: 'center', boxShadow: '0 25px 50px rgba(0,0,0,.6)', border: '1px solid #1e2d4a' },
    title:   { fontSize: 22, fontWeight: 700, color: '#e2e8f0', marginBottom: 8 },
    sub:     { fontSize: 14, color: '#94a3b8', marginBottom: 24 },
    btn:     { background: '#6366f1', color: '#fff', border: 'none', borderRadius: 10, padding: '13px 32px', fontSize: 15, fontWeight: 700, cursor: 'pointer', width: '100%', transition: 'background .2s' },
    btnGreen:{ background: '#059669', color: '#fff', border: 'none', borderRadius: 10, padding: '13px 32px', fontSize: 15, fontWeight: 700, cursor: 'pointer', width: '100%' },
    btnRed:  { background: '#dc2626', color: '#fff', border: 'none', borderRadius: 10, padding: '12px 24px', fontSize: 14, fontWeight: 700, cursor: 'pointer', borderRadius: 8 },
    err:     { color: '#f87171', background: 'rgba(220,38,38,.1)', border: '1px solid rgba(220,38,38,.3)', borderRadius: 8, padding: '10px 16px', fontSize: 13, marginTop: 16 },
  };

  if (phase === 'validating') return (
    <div style={s.root}>
      <div style={{ textAlign: 'center' }}>
        <div className="spinner" style={{ margin: '0 auto 16px' }} />
        <div style={{ color: '#94a3b8', fontSize: 14 }}>Validating interview link…</div>
      </div>
    </div>
  );

  if (phase === 'invalid') return (
    <div style={s.root}>
      <div style={{ ...s.card, className: 'slide-in' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>⛔</div>
        <div style={s.title}>Invalid Link</div>
        <div style={s.sub}>This interview link is invalid or has expired. Please contact HR.</div>
      </div>
    </div>
  );

  if (phase === 'completed') return (
    <div style={s.root}>
      <div style={s.card}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>✅</div>
        <div style={s.title}>Interview Submitted</div>
        <div style={s.sub}>You have already completed this interview. Results will be communicated by HR.</div>
      </div>
    </div>
  );

  if (phase === 'submitted') return (
    <div style={s.root}>
      <div style={s.card}>
        <div style={{ fontSize: 52, marginBottom: 16 }}>🎉</div>
        <div style={s.title}>Interview Submitted!</div>
        <div style={s.sub}>Thank you, {room?.candidateName}. Your answers have been recorded.</div>
        {score !== null && (
          <div style={{ background: '#0d1b2a', borderRadius: 12, padding: 20, marginTop: 20 }}>
            <div style={{ fontSize: 13, color: '#94a3b8', marginBottom: 6 }}>MCQ Score</div>
            <div style={{ fontSize: 36, fontWeight: 800, color: '#6366f1' }}>{score}<span style={{ fontSize: 18, color: '#64748b' }}>/{totalMarks}</span></div>
          </div>
        )}
        <div style={{ ...s.err, background: 'rgba(99,102,241,.1)', border: '1px solid rgba(99,102,241,.3)', color: '#a5b4fc', marginTop: 20 }}>
          Your interviewer will review your answers and contact you shortly.
        </div>
      </div>
    </div>
  );

  if (phase === 'permissions') return (
    <div style={s.root}>
      <div style={s.card}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>🎥</div>
        <div style={s.title}>Camera & Microphone Access</div>
        <div style={s.sub}>
          This interview requires your camera and microphone.<br />
          The session will be recorded.
        </div>
        <div style={{ background: '#0d1b2a', borderRadius: 10, padding: '14px 18px', marginBottom: 20, textAlign: 'left' }}>
          {[
            `📋 ${room?.questions?.length || 20} technical questions`,
            `⏱ ${room?.durationMinutes || 45} minute time limit`,
            '🚫 Tab switching & fullscreen exit are flagged',
            '⚠️ 3 violations trigger auto-submission',
            '💾 Answers auto-saved every 5 seconds',
          ].map(t => <div key={t} style={{ fontSize: 13, color: '#94a3b8', marginBottom: 6 }}>{t}</div>)}
        </div>
        {error && <div style={s.err}>{error}</div>}
        <button style={s.btn} onClick={requestPermissions}>Allow Camera & Microphone</button>
      </div>
    </div>
  );

  if (phase === 'ready') return (
    <div style={s.root}>
      <div style={s.card}>
        <video ref={localVideoRef} autoPlay playsInline muted
          style={{ width: '100%', borderRadius: 10, marginBottom: 20, background: '#000', aspectRatio: '16/9', objectFit: 'cover' }} />
        <div style={s.title}>Ready to Start?</div>
        <div style={s.sub}>Your camera is working. When you click Start, the interview will enter fullscreen and the timer begins.</div>
        <button style={s.btnGreen} onClick={startInterview}>Start Interview</button>
      </div>
    </div>
  );

  /* ─────── Active interview layout ─────── */
  const answeredCount  = room?.questions?.filter(q => getAnswerStatus(q) === 'answered').length || 0;
  const unansweredCount= (room?.questions?.length || 0) - answeredCount;

  return (
    <div style={{ display: 'flex', height: '100vh', background: '#0a0f1e', overflow: 'hidden', position: 'relative' }}>

      {/* Violation banner */}
      {violationMsg && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 9999, background: violations >= 3 ? '#7f1d1d' : '#78350f', color: '#fef3c7', padding: '12px 20px', textAlign: 'center', fontWeight: 700, fontSize: 14 }}>
          ⚠️ {violationMsg}
        </div>
      )}

      {/* ── Left panel: palette + camera ── */}
      <div style={{ width: 220, background: '#111827', display: 'flex', flexDirection: 'column', padding: 16, gap: 12, flexShrink: 0 }}>
        {/* Timer */}
        <div style={{ textAlign: 'center', background: '#0d1b2a', borderRadius: 10, padding: '12px 8px' }}>
          <div style={{ fontSize: 11, color: '#64748b', marginBottom: 4, textTransform: 'uppercase', letterSpacing: 1 }}>Time Left</div>
          <div className={timerCritical ? 'timer-warn' : ''} style={{ fontSize: 28, fontWeight: 800, fontFamily: 'monospace', color: timerCritical ? '#f59e0b' : '#e2e8f0' }}>{timerStr}</div>
        </div>

        {/* Camera preview */}
        <video ref={localVideoRef} autoPlay playsInline muted
          style={{ width: '100%', borderRadius: 8, background: '#000', aspectRatio: '4/3', objectFit: 'cover' }} />

        {/* Stats */}
        <div style={{ display: 'flex', gap: 6 }}>
          <div style={{ flex: 1, background: '#065f46', borderRadius: 8, padding: '8px 4px', textAlign: 'center' }}>
            <div style={{ fontSize: 18, fontWeight: 800, color: '#34d399' }}>{answeredCount}</div>
            <div style={{ fontSize: 10, color: '#6ee7b7' }}>Answered</div>
          </div>
          <div style={{ flex: 1, background: '#1e2d4a', borderRadius: 8, padding: '8px 4px', textAlign: 'center' }}>
            <div style={{ fontSize: 18, fontWeight: 800, color: '#94a3b8' }}>{unansweredCount}</div>
            <div style={{ fontSize: 10, color: '#64748b' }}>Pending</div>
          </div>
        </div>

        {/* Question palette */}
        <div style={{ overflowY: 'auto', flex: 1 }}>
          <div style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 8 }}>Questions</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 4 }}>
            {(room?.questions || []).map((q, idx) => {
              const status = flagged[q.questionId] ? 'flagged' : getAnswerStatus(q);
              const c = PALETTE_COLORS[status === 'answered' && idx === currentQ ? 'current' : (idx === currentQ ? 'current' : status)];
              return (
                <button key={q.questionId} onClick={() => saveCurrentAndNavigate(idx)}
                  style={{ background: c.bg, border: `2px solid ${c.border}`, borderRadius: 6, color: '#fff', fontSize: 12, fontWeight: 700, padding: '6px 0', cursor: 'pointer' }}>
                  {idx + 1}
                </button>
              );
            })}
          </div>
          {/* Legend */}
          <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 4 }}>
            {[['#059669','Answered'],['#6366f1','Current'],['#f59e0b','Flagged'],['#1e2d4a','Pending']].map(([c,l]) => (
              <div key={l} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: '#64748b' }}>
                <div style={{ width: 12, height: 12, borderRadius: 3, background: c }} />{l}
              </div>
            ))}
          </div>
        </div>

        {/* Violations indicator */}
        {violations > 0 && (
          <div style={{ background: '#7f1d1d', borderRadius: 8, padding: '8px 12px', textAlign: 'center' }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: '#fca5a5' }}>⚠️ {violations}/3 violations</div>
          </div>
        )}
      </div>

      {/* ── Right panel: question ── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Header */}
        <div style={{ background: '#111827', borderBottom: '1px solid #1e2d4a', padding: '12px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#6366f1' }}>Technical Interview</span>
            <span style={{ fontSize: 12, color: '#64748b', marginLeft: 12 }}>{room?.candidateName} · {room?.appliedProfile}</span>
          </div>
          <button style={s.btnRed} onClick={() => handleSubmit(false)}>Submit Interview</button>
        </div>

        {/* Question body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 32 }}>
          {currentQuestion && (
            <div className="slide-in">
              {/* Question header */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
                <div>
                  <span style={{ background: '#1e2d4a', color: '#94a3b8', fontSize: 12, fontWeight: 700, padding: '4px 10px', borderRadius: 20, marginRight: 10 }}>
                    Q{currentQ + 1} / {room.questions.length}
                  </span>
                  <span style={{ background: currentQuestion.questionType === 'MCQ' ? '#1e1b4b' : '#0d2d1a', color: currentQuestion.questionType === 'MCQ' ? '#a5b4fc' : '#4ade80', fontSize: 11, fontWeight: 700, padding: '4px 10px', borderRadius: 20, marginRight: 8 }}>
                    {currentQuestion.questionType}
                  </span>
                  <span style={{ background: '#1a1a2e', color: '#fbbf24', fontSize: 11, fontWeight: 700, padding: '4px 10px', borderRadius: 20 }}>
                    {currentQuestion.marks} marks
                  </span>
                </div>
                <button
                  onClick={() => setFlagged(f => ({ ...f, [currentQuestion.questionId]: !f[currentQuestion.questionId] }))}
                  style={{ background: flagged[currentQuestion.questionId] ? '#92400e' : '#1e2d4a', color: flagged[currentQuestion.questionId] ? '#fbbf24' : '#64748b', border: 'none', borderRadius: 8, padding: '6px 14px', fontSize: 12, cursor: 'pointer' }}>
                  {flagged[currentQuestion.questionId] ? '🚩 Flagged' : '⚑ Flag'}
                </button>
              </div>

              {/* Question text */}
              <div style={{ background: '#111827', border: '1px solid #1e2d4a', borderRadius: 12, padding: '20px 24px', marginBottom: 24, fontSize: 16, lineHeight: 1.7, color: '#e2e8f0', whiteSpace: 'pre-wrap' }}>
                {currentQuestion.questionText}
              </div>

              {/* Answer area */}
              {currentQuestion.questionType === 'MCQ' ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  {(currentQuestion.options || []).map(opt => {
                    const selected = answers[currentQuestion.questionId]?.selectedOption === opt.letter;
                    return (
                      <button key={opt.letter} onClick={() => setAnswer(currentQuestion.questionId, 'selectedOption', opt.letter)}
                        style={{ display: 'flex', alignItems: 'center', gap: 14, background: selected ? 'rgba(99,102,241,.2)' : '#111827',
                          border: `2px solid ${selected ? '#6366f1' : '#1e2d4a'}`, borderRadius: 10, padding: '14px 18px',
                          cursor: 'pointer', textAlign: 'left', transition: 'all .15s', color: '#e2e8f0', fontSize: 14 }}>
                        <div style={{ width: 32, height: 32, borderRadius: '50%', background: selected ? '#6366f1' : '#1e2d4a',
                          color: selected ? '#fff' : '#64748b', display: 'flex', alignItems: 'center', justifyContent: 'center',
                          fontWeight: 800, fontSize: 14, flexShrink: 0 }}>{opt.letter}</div>
                        <span style={{ flex: 1 }}>{opt.text}</span>
                      </button>
                    );
                  })}
                </div>
              ) : (
                <textarea
                  value={answers[currentQuestion.questionId]?.answerText || ''}
                  onChange={e => setAnswer(currentQuestion.questionId, 'answerText', e.target.value)}
                  placeholder="Type your answer here…"
                  style={{ width: '100%', minHeight: 200, background: '#111827', border: '2px solid #1e2d4a', borderRadius: 10,
                    padding: '14px 16px', color: '#e2e8f0', fontSize: 14, lineHeight: 1.6, resize: 'vertical',
                    outline: 'none', fontFamily: 'inherit' }}
                />
              )}

              {/* Navigation */}
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 28 }}>
                <button
                  onClick={() => saveCurrentAndNavigate(currentQ - 1)} disabled={currentQ === 0}
                  style={{ background: '#1e2d4a', color: currentQ === 0 ? '#334155' : '#94a3b8', border: 'none', borderRadius: 10, padding: '11px 24px', fontSize: 14, cursor: currentQ === 0 ? 'default' : 'pointer', fontWeight: 600 }}>
                  ← Previous
                </button>
                {currentQ < (room?.questions?.length || 1) - 1 ? (
                  <button onClick={() => saveCurrentAndNavigate(currentQ + 1)}
                    style={{ background: '#6366f1', color: '#fff', border: 'none', borderRadius: 10, padding: '11px 24px', fontSize: 14, cursor: 'pointer', fontWeight: 600 }}>
                    Next →
                  </button>
                ) : (
                  <button onClick={() => handleSubmit(false)}
                    style={{ background: '#059669', color: '#fff', border: 'none', borderRadius: 10, padding: '11px 24px', fontSize: 14, cursor: 'pointer', fontWeight: 700 }}>
                    Submit Interview
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
