import React, { useRef, useState, useEffect } from 'react';
import {
  Box, Typography, Button, Card, CardContent, Chip,
  Table, TableHead, TableRow, TableCell, TableBody,
  CircularProgress, Alert, Divider, LinearProgress, Stack,
} from '@mui/material';
import UploadFileIcon    from '@mui/icons-material/UploadFile';
import DownloadIcon      from '@mui/icons-material/Download';
import CheckCircleIcon   from '@mui/icons-material/CheckCircle';
import ErrorIcon         from '@mui/icons-material/Error';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import PeopleIcon        from '@mui/icons-material/People';
import EventIcon         from '@mui/icons-material/Event';
import { leaveUploadApi } from '../api/leaveUploadApi';
import { toast } from 'react-toastify';

const SAMPLE_HOLIDAYS = [
  { name: "New Year's Day",  date: '01-January-2026'  },
  { name: 'Holi',            date: '15-June-2026'     },
  { name: 'Independence Day',date: '15-August-2026'   },
  { name: 'Diwali',          date: '20-October-2026'  },
  { name: 'Christmas',       date: '25-December-2026' },
];

const LeaveUploadPage = () => {
  const inputRef                  = useRef(null);
  const [dragging,  setDragging]  = useState(false);
  const [uploading, setUploading] = useState(false);
  const [result,    setResult]    = useState(null);
  const [holidays,  setHolidays]  = useState([]);

  const loadHolidays = () => {
    leaveUploadApi.getHolidays()
      .then(setHolidays)
      .catch(() => {});
  };

  useEffect(() => { loadHolidays(); }, []);

  const handleFile = async (file) => {
    if (!file) return;
    const ext = file.name.split('.').pop().toLowerCase();
    if (!['xlsx', 'xls'].includes(ext)) {
      toast.error('Please upload an Excel file (.xlsx or .xls)');
      return;
    }
    setUploading(true);
    setResult(null);
    try {
      const data = await leaveUploadApi.upload(file);
      setResult(data);
      if (data.imported > 0) {
        toast.success(`${data.imported} holiday(s) applied to all employees`);
        loadHolidays();
      } else {
        toast.warning('No holidays were imported — check the error log below');
      }
    } catch {
      toast.error('Upload failed. Please check the file format and try again.');
    } finally {
      setUploading(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    handleFile(e.dataTransfer.files?.[0]);
  };

  const handleDownloadTemplate = async () => {
    try {
      const blob = await leaveUploadApi.downloadTemplate();
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href     = url;
      a.download = 'company_holidays_template.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Failed to download template');
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <CalendarMonthIcon color="primary" sx={{ fontSize: 32 }} />
          <Box>
            <Typography variant="h4" fontWeight={700}>Leave Upload</Typography>
            <Typography variant="body2" color="text.secondary">
              Upload company public holidays — auto-applied to all employees
            </Typography>
          </Box>
        </Box>
        <Button
          variant="outlined"
          startIcon={<DownloadIcon />}
          onClick={handleDownloadTemplate}
        >
          Download Template
        </Button>
      </Box>

      {/* How it works */}
      <Card sx={{ mb: 3, bgcolor: '#f0fdfa', border: '1px solid #99f6e4' }}>
        <CardContent sx={{ py: 1.5 }}>
          <Stack direction="row" spacing={4} flexWrap="wrap">
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <EventIcon sx={{ color: '#14b8a6', fontSize: 20 }} />
              <Typography variant="body2">
                <strong>Step 1:</strong> List company holidays (name + date)
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <UploadFileIcon sx={{ color: '#14b8a6', fontSize: 20 }} />
              <Typography variant="body2">
                <strong>Step 2:</strong> Upload the Excel file
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <PeopleIcon sx={{ color: '#14b8a6', fontSize: 20 }} />
              <Typography variant="body2">
                <strong>Step 3:</strong> Holidays are automatically added as approved leaves for every active employee
              </Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>

      {/* Drop zone */}
      <Card
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        sx={{
          mb: 3,
          border: '2px dashed',
          borderColor: dragging ? 'primary.main' : '#e2e8f0',
          bgcolor: dragging ? 'rgba(20,184,166,0.04)' : 'white',
          transition: 'all 0.2s',
          cursor: uploading ? 'default' : 'pointer',
        }}
        onClick={() => !uploading && inputRef.current?.click()}
      >
        <CardContent sx={{ py: 5, textAlign: 'center' }}>
          <input
            ref={inputRef}
            type="file"
            accept=".xlsx,.xls"
            style={{ display: 'none' }}
            onChange={(e) => { handleFile(e.target.files?.[0]); e.target.value = ''; }}
          />
          {uploading ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
              <CircularProgress size={48} />
              <Typography color="text.secondary" fontWeight={500}>
                Processing holidays and applying to all employees…
              </Typography>
            </Box>
          ) : (
            <>
              <UploadFileIcon sx={{ fontSize: 56, color: dragging ? 'primary.main' : '#94a3b8', mb: 1.5 }} />
              <Typography variant="h6" fontWeight={600} gutterBottom>
                {dragging ? 'Drop your Excel file here' : 'Drag & drop or click to upload'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Supports .xlsx and .xls
              </Typography>
              <Button
                variant="contained"
                startIcon={<UploadFileIcon />}
                sx={{ mt: 2 }}
                onClick={(e) => { e.stopPropagation(); inputRef.current?.click(); }}
              >
                Choose File
              </Button>
            </>
          )}
        </CardContent>
      </Card>

      {/* Result */}
      {result && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6" fontWeight={700} gutterBottom>Upload Result</Typography>

            {result.imported > 0 && result.skipped === 0 && result.errors.length === 0 ? (
              <Alert severity="success" sx={{ mb: 2 }}>
                All {result.imported} holiday(s) have been applied to all active employees successfully!
              </Alert>
            ) : result.imported > 0 ? (
              <Alert severity="warning" sx={{ mb: 2 }}>
                {result.imported} of {result.totalRows} holiday(s) applied. {result.skipped} skipped.
              </Alert>
            ) : (
              <Alert severity="error" sx={{ mb: 2 }}>
                No holidays were imported. Check the errors below.
              </Alert>
            )}

            <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
              <Chip
                icon={<CheckCircleIcon />}
                label={`${result.imported} Holiday${result.imported !== 1 ? 's' : ''} Applied`}
                color="success" variant="outlined"
              />
              {result.skipped > 0 && (
                <Chip
                  label={`${result.skipped} Skipped`}
                  color="warning" variant="outlined"
                />
              )}
              <Chip
                label={`${result.totalRows} Total Rows`}
                variant="outlined"
              />
            </Stack>

            {result.errors.length > 0 && (
              <Box sx={{
                maxHeight: 220, overflowY: 'auto',
                border: '1px solid #fde68a', borderRadius: 1,
                bgcolor: '#fffbeb', p: 1.5,
              }}>
                {result.errors.map((err, i) => (
                  <Box key={i} sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, mb: 0.75 }}>
                    <ErrorIcon sx={{ fontSize: 15, color: '#d97706', mt: 0.25, flexShrink: 0 }} />
                    <Typography variant="caption" color="warning.dark">{err}</Typography>
                  </Box>
                ))}
              </Box>
            )}
          </CardContent>
        </Card>
      )}

      {/* ── Uploaded Holidays List ── */}
      {holidays.length > 0 && (
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
              <CalendarMonthIcon sx={{ color: '#14b8a6', fontSize: 20 }} />
              <Typography variant="h6" fontWeight={700}>
                Uploaded Public Holidays ({holidays.length})
              </Typography>
            </Box>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: '#f1f5f9' }}>
                  <TableCell sx={{ fontWeight: 700, width: 40 }}>#</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Holiday Name</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>Date</TableCell>
                  <TableCell sx={{ fontWeight: 700 }} align="center">Applied To</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {holidays.map((h, i) => (
                  <TableRow key={i} hover sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
                    <TableCell sx={{ color: '#94a3b8', fontSize: 12 }}>{i + 1}</TableCell>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{h.name}</Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={h.date}
                        size="small"
                        sx={{ bgcolor: '#f0fdfa', color: '#0f766e', fontFamily: 'monospace', fontSize: 12 }}
                      />
                    </TableCell>
                    <TableCell align="center">
                      <Chip
                        icon={<PeopleIcon sx={{ fontSize: '14px !important' }} />}
                        label={`${h.employeeCount} employees`}
                        size="small"
                        color="primary"
                        variant="outlined"
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {/* Format + preview */}
      <Card>
        <CardContent>
          <Typography variant="h6" fontWeight={700} gutterBottom>Excel Format</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Your file needs just <strong>two columns</strong>. Row 1 is the header and is skipped automatically.
          </Typography>

          {/* Format table */}
          <Table size="small" sx={{ mb: 3 }}>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                <TableCell sx={{ fontWeight: 700, width: 50 }}>Column</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Field</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Accepted Formats</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              <TableRow hover>
                <TableCell><Typography variant="caption" fontWeight={700} color="text.secondary">A</Typography></TableCell>
                <TableCell>
                  <Typography variant="body2" fontWeight={600}>Holiday Name
                    <Chip label="required" size="small" color="error" sx={{ ml: 1, height: 16, fontSize: 10 }} />
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.secondary">Any text — e.g. Holi, Christmas, Independence Day</Typography>
                </TableCell>
              </TableRow>
              <TableRow hover>
                <TableCell><Typography variant="caption" fontWeight={700} color="text.secondary">B</Typography></TableCell>
                <TableCell>
                  <Typography variant="body2" fontWeight={600}>Date
                    <Chip label="required" size="small" color="error" sx={{ ml: 1, height: 16, fontSize: 10 }} />
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.secondary">
                    15-June-2026 &nbsp;·&nbsp; 15-Jun-2026 &nbsp;·&nbsp; 15/06/2026 &nbsp;·&nbsp; 2026-06-15
                  </Typography>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>

          <Divider sx={{ mb: 2 }} />

          {/* Sample preview */}
          <Typography variant="subtitle2" fontWeight={700} gutterBottom>
            Sample Data Preview
          </Typography>
          <Box sx={{ border: '1px solid #e2e8f0', borderRadius: 1, overflow: 'hidden' }}>
            <Table size="small">
              <TableHead>
                <TableRow sx={{ bgcolor: '#0f172a' }}>
                  <TableCell sx={{ color: 'white', fontWeight: 700, fontSize: 12 }}>Holiday Name</TableCell>
                  <TableCell sx={{ color: 'white', fontWeight: 700, fontSize: 12 }}>Date</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {SAMPLE_HOLIDAYS.map((h, i) => (
                  <TableRow key={i} sx={{ bgcolor: i % 2 === 0 ? 'white' : '#f8fafc' }}>
                    <TableCell sx={{ fontSize: 13 }}>{h.name}</TableCell>
                    <TableCell sx={{ fontSize: 13, fontFamily: 'monospace', color: '#14b8a6' }}>{h.date}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>

          <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1.5 }}>
            <strong>Tip:</strong> Download the template — it includes sample holidays and a full instructions sheet.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
};

export default LeaveUploadPage;
