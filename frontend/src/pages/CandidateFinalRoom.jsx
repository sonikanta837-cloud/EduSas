import React, { useEffect, useRef, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';

/* ── Public API helpers (no JWT) ─────────────────────────────────────────── */
const BASE = process.env.REACT_APP_API_URL || '/api';
const pub = async (method, path, body) => {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
};
const pubForm = async (path, formData) => {
  const res = await fetch(`${BASE}${path}`, { method: 'POST', body: formData });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
};

const STUN = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }, { urls: 'stun:stun1.l.google.com:19302' }] };

/* ── Styles ──────────────────────────────────────────────────────────────── */
const S = {
  page:    { minHeight: '100vh', background: '#0f172a', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', fontFamily: 'Inter, system-ui, sans-serif', color: '#e2e8f0', padding: '24px' },
  card:    { background: '#1e293b', borderRadius: 16, padding: '32px', maxWidth: 680, width: '100%', boxShadow: '0 20px 60px rgba(0,0,0,0.5)' },
  h1:      { fontSize: 22, fontWeight: 700, color: '#f1f5f9', margin: '0 0 6px 0' },
  sub:     { fontSize: 14, color: '#94a3b8', margin: '0 0 24px 0' },
  video:   { width: '100%', borderRadius: 12, background: '#0f172a', aspectRatio: '16/9', objectFit: 'cover' },
  btn:     { padding: '12px 28px', borderRadius: 8, border: 'none', fontWeight: 700, fontSize: 15, cursor: 'pointer', transition: 'opacity .15s' },
  green:   { background: '#16a34a', color: '#fff' },
  red:     { background: '#dc2626', color: '#fff' },
  grey:    { background: '#334155', color: '#94a3b8', cursor: 'not-allowed' },
  badge:   { display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 12px', borderRadius: 99, fontSize: 12, fontWeight: 700 },
  info:    { background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.3)', borderRadius: 10, padding: '14px 18px', fontSize: 13, color: '#a5b4fc', margin: '16px 0' },
};

export default function CandidateFinalRoom() {
  const { token } = useParams();
  const [phase,    setPhase]    = useState('loading');  // loading | permissions | ready | active | ended | invalid
  const [room,     setRoom]     = useState(null);
  const [error,    setError]    = useState('');
  const [ending,   setEnding]   = useState(false);
  const [duration, setDuration] = useState(0); // seconds

  const localVideoRef  = useRef(null);
  const localStreamRef = useRef(null);
  const pcRef          = useRef(null);
  const recorderRef    = useRef(null);
  const chunksRef      = useRef([]);
  const iceSeenRef     = useRef(0);
  const timerRef       = useRef(null);
  const uploadingRef   = useRef(false);

  /* ── Load room info ─────────────────────────────────────────────────────── */
  useEffect(() => {
    pub('GET', `/interviews/final/candidate/${token}`)
      .then(data => {
        setRoom(data);
        if (data.interviewStatus === 'EVALUATED' || data.interviewStatus === 'CANDIDATE_SUBMITTED') {
          setPhase('ended');
        } else {
          setPhase('permissions');
        }
      })
      .catch(() => { setPhase('invalid'); setError('This interview link is invalid or has expired.'); });
  }, [token]);

  /* ── Request camera / mic ───────────────────────────────────────────────── */
  const requestPermissions = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      localStreamRef.current = stream;
      if (localVideoRef.current) localVideoRef.current.srcObject = stream;
      setPhase('ready');
    } catch {
      setError('Camera/microphone access is required. Please allow access and try again.');
    }
  };

  /* ── Start interview ────────────────────────────────────────────────────── */
  const startInterview = async () => {
    await pub('POST', `/interviews/final/candidate/${token}/start`);
    setPhase('active');
    timerRef.current = setInterval(() => setDuration(d => d + 1), 1000);
    await setupWebRTC();
    startRecording();
  };

  /* ── WebRTC offer ───────────────────────────────────────────────────────── */
  const setupWebRTC = async () => {
    try {
      const pc = new RTCPeerConnection(STUN);
      pcRef.current = pc;

      localStreamRef.current.getTracks().forEach(t => pc.addTrack(t, localStreamRef.current));

      pc.onicecandidate = async e => {
        if (e.candidate) {
          await pub('POST', `/interviews/final/candidate/${token}/signal/ice`, { ice: JSON.stringify(e.candidate) }).catch(() => {});
        }
      };

      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      await pub('POST', `/interviews/final/candidate/${token}/signal/offer`, { sdp: JSON.stringify(offer) });

      // Poll for director's answer
      let polls = 0;
      const answerPoller = setInterval(async () => {
        if (++polls > 60) { clearInterval(answerPoller); return; }
        const res = await pub('GET', `/interviews/final/candidate/${token}/signal/answer`).catch(() => null);
        if (res?.sdp) {
          clearInterval(answerPoller);
          await pc.setRemoteDescription(JSON.parse(res.sdp));

          // Poll director's ICE
          const icePoller = setInterval(async () => {
            const list = await pub('GET', `/interviews/final/candidate/${token}/signal/ice/director`).catch(() => []);
            if (list.length > iceSeenRef.current) {
              for (let i = iceSeenRef.current; i < list.length; i++) {
                await pc.addIceCandidate(JSON.parse(list[i])).catch(() => {});
              }
              iceSeenRef.current = list.length;
            }
          }, 2000);
          setTimeout(() => clearInterval(icePoller), 120000);
        }
      }, 2000);
    } catch (e) {
      console.warn('WebRTC setup failed:', e);
    }
  };

  /* ── Recording ──────────────────────────────────────────────────────────── */
  const startRecording = () => {
    if (!localStreamRef.current || !window.MediaRecorder) return;
    try {
      const recorder = new MediaRecorder(localStreamRef.current, { mimeType: 'video/webm' });
      recorderRef.current = recorder;
      recorder.ondataavailable = e => { if (e.data?.size > 0) chunksRef.current.push(e.data); };
      recorder.start(30000); // 30s chunks
    } catch { /* recording optional */ }
  };

  /* ── Upload recording ───────────────────────────────────────────────────── */
  const uploadRecording = async () => {
    if (uploadingRef.current || chunksRef.current.length === 0) return;
    uploadingRef.current = true;
    try {
      if (recorderRef.current?.state === 'recording') {
        await new Promise(res => { recorderRef.current.onstop = res; recorderRef.current.stop(); });
      }
      const blob = new Blob(chunksRef.current, { type: 'video/webm' });
      if (blob.size < 1024) return;
      const fd = new FormData();
      fd.append('file', blob, 'final_recording.webm');
      await pubForm(`/interviews/final/candidate/${token}/recording`, fd);
    } catch { /* non-fatal */ } finally { uploadingRef.current = false; }
  };

  /* ── End interview ──────────────────────────────────────────────────────── */
  const handleEnd = useCallback(async () => {
    if (ending) return;
    setEnding(true);
    clearInterval(timerRef.current);
    await uploadRecording();
    await pub('POST', `/interviews/final/candidate/${token}/submit`).catch(() => {});
    if (localStreamRef.current) localStreamRef.current.getTracks().forEach(t => t.stop());
    if (pcRef.current) { pcRef.current.close(); pcRef.current = null; }
    setPhase('ended');
  }, [ending, token]); // eslint-disable-line react-hooks/exhaustive-deps

  /* ── Cleanup on unmount ─────────────────────────────────────────────────── */
  useEffect(() => () => {
    clearInterval(timerRef.current);
    if (localStreamRef.current) localStreamRef.current.getTracks().forEach(t => t.stop());
    if (pcRef.current) pcRef.current.close();
  }, []);

  const fmtDuration = s => `${String(Math.floor(s / 3600)).padStart(2, '0')}:${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

  /* ── Render ─────────────────────────────────────────────────────────────── */
  if (phase === 'loading') return (
    <div style={S.page}>
      <div style={{ ...S.card, textAlign: 'center' }}>
        <div style={{ width: 40, height: 40, border: '3px solid #334155', borderTopColor: '#6366f1', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }} />
        <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
        <p style={{ color: '#94a3b8', margin: 0 }}>Loading interview room…</p>
      </div>
    </div>
  );

  if (phase === 'invalid') return (
    <div style={S.page}>
      <div style={{ ...S.card, textAlign: 'center' }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>🔒</div>
        <h1 style={S.h1}>Invalid Link</h1>
        <p style={{ color: '#94a3b8' }}>{error}</p>
      </div>
    </div>
  );

  if (phase === 'ended') return (
    <div style={S.page}>
      <div style={{ ...S.card, textAlign: 'center' }}>
        <div style={{ fontSize: 56, marginBottom: 12 }}>✅</div>
        <h1 style={{ ...S.h1, fontSize: 26 }}>Interview Completed</h1>
        <p style={{ color: '#94a3b8', marginTop: 8 }}>
          Thank you, <strong>{room?.candidateName || 'Candidate'}</strong>.<br />
          Your interview session has been recorded and submitted successfully.<br />
          Our team will be in touch shortly.
        </p>
      </div>
    </div>
  );

  return (
    <div style={S.page}>
      <div style={S.card}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, flexWrap: 'wrap', gap: 8 }}>
          <div>
            <h1 style={S.h1}>Final Interview</h1>
            <p style={S.sub}>{room?.appliedProfile} · {room?.candidateName}</p>
          </div>
          {phase === 'active' && (
            <div style={{ ...S.badge, background: 'rgba(220,38,38,0.15)', color: '#f87171' }}>
              🔴 {fmtDuration(duration)}
            </div>
          )}
        </div>

        {/* Video */}
        <video ref={localVideoRef} autoPlay muted playsInline style={S.video} />

        {/* Permissions phase */}
        {phase === 'permissions' && (
          <>
            <div style={S.info}>
              <strong>Before you begin:</strong><br />
              • Ensure you are in a quiet, well-lit environment<br />
              • Keep your camera and microphone enabled throughout<br />
              • The session will be recorded for review<br />
              • Click <strong>Start Interview</strong> only when you are ready
            </div>
            <div style={{ display: 'flex', justifyContent: 'center', marginTop: 20 }}>
              {error ? (
                <p style={{ color: '#f87171', textAlign: 'center' }}>{error}</p>
              ) : (
                <button style={{ ...S.btn, ...S.green }} onClick={requestPermissions}>
                  Enable Camera &amp; Microphone
                </button>
              )}
            </div>
          </>
        )}

        {/* Ready phase */}
        {phase === 'ready' && (
          <div style={{ display: 'flex', justifyContent: 'center', marginTop: 20 }}>
            <button style={{ ...S.btn, ...S.green, fontSize: 16, padding: '14px 40px' }} onClick={startInterview}>
              ▶ Start Interview
            </button>
          </div>
        )}

        {/* Active phase */}
        {phase === 'active' && (
          <div style={{ marginTop: 20 }}>
            <div style={S.info}>
              Your video is live. The interviewer can see and hear you. Speak clearly and professionally.<br />
              When the interview is over, click <strong>End Interview</strong>.
            </div>
            <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
              <button
                style={{ ...S.btn, ...(ending ? S.grey : S.red), fontSize: 16, padding: '14px 40px' }}
                onClick={handleEnd}
                disabled={ending}
              >
                {ending ? 'Submitting…' : '⏹ End Interview'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
