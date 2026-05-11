import React, { useEffect, useState } from 'react';
import {
  Box, Card, CardContent, Typography, Grid, Button, Select,
  FormControl, InputLabel, MenuItem, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, CircularProgress, Chip
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import { employeeApi } from '../api/employeeApi';
import { timesheetApi } from '../api/timesheetApi';
import { leaveApi } from '../api/leaveApi';
import { performanceApi } from '../api/performanceApi';
import { toast } from 'react-toastify';

const ReportsPage = () => {
  const [reportType, setReportType] = useState('employees');
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);

  const loadReport = async () => {
    setLoading(true);
    try {
      let result = [];
      switch (reportType) {
        case 'employees':
          result = await employeeApi.getAll();
          break;
        case 'leaves':
          result = await leaveApi.getAll();
          break;
        case 'performance':
          result = await performanceApi.getAll();
          break;
        default:
          result = [];
      }
      setData(result);
    } catch {
      toast.error('Failed to load report');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadReport(); }, [reportType]);

  const exportCSV = () => {
    if (data.length === 0) return;
    const headers = Object.keys(data[0]).join(',');
    const rows = data.map(row => Object.values(row).map(v => `"${v ?? ''}"`).join(','));
    const csv = [headers, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${reportType}-report.csv`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('CSV downloaded!');
  };

  const renderTable = () => {
    if (data.length === 0) return <Typography align="center" color="text.secondary" py={3}>No data found</Typography>;

    switch (reportType) {
      case 'employees':
        return (
          <Table size="small">
            <TableHead><TableRow sx={{ bgcolor: '#f8f9fa' }}>
              <TableCell>Name</TableCell><TableCell>Email</TableCell>
              <TableCell>Department</TableCell><TableCell>Position</TableCell>
              <TableCell>Role</TableCell><TableCell>Status</TableCell>
            </TableRow></TableHead>
            <TableBody>{data.map(e => (
              <TableRow key={e.id} hover>
                <TableCell fontWeight={600}>{e.fullName}</TableCell>
                <TableCell>{e.email}</TableCell>
                <TableCell>{e.department || '-'}</TableCell>
                <TableCell>{e.position || '-'}</TableCell>
                <TableCell><Chip label={e.role} size="small" /></TableCell>
                <TableCell><Chip label={e.active ? 'Active' : 'Inactive'} size="small" color={e.active ? 'success' : 'default'} /></TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        );
      case 'leaves':
        return (
          <Table size="small">
            <TableHead><TableRow sx={{ bgcolor: '#f8f9fa' }}>
              <TableCell>Employee</TableCell><TableCell>Type</TableCell>
              <TableCell>Start</TableCell><TableCell>End</TableCell>
              <TableCell>Days</TableCell><TableCell>Status</TableCell>
            </TableRow></TableHead>
            <TableBody>{data.map(l => (
              <TableRow key={l.id} hover>
                <TableCell>{l.employeeName}</TableCell>
                <TableCell>{l.leaveType}</TableCell>
                <TableCell>{l.startDate}</TableCell>
                <TableCell>{l.endDate}</TableCell>
                <TableCell>{l.totalDays}</TableCell>
                <TableCell><Chip label={l.status} size="small" color={l.status === 'APPROVED' ? 'success' : l.status === 'REJECTED' ? 'error' : 'warning'} /></TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        );
      case 'performance':
        return (
          <Table size="small">
            <TableHead><TableRow sx={{ bgcolor: '#f8f9fa' }}>
              <TableCell>Employee</TableCell><TableCell>Reviewer</TableCell>
              <TableCell>Rating</TableCell><TableCell>Period</TableCell>
              <TableCell>Date</TableCell>
            </TableRow></TableHead>
            <TableBody>{data.map(r => (
              <TableRow key={r.id} hover>
                <TableCell>{r.employeeName}</TableCell>
                <TableCell>{r.reviewerName}</TableCell>
                <TableCell><Chip label={`${r.rating}/5`} size="small" color={r.rating >= 4 ? 'success' : r.rating >= 3 ? 'warning' : 'error'} /></TableCell>
                <TableCell>{r.reviewPeriod}</TableCell>
                <TableCell>{r.reviewDate}</TableCell>
              </TableRow>
            ))}</TableBody>
          </Table>
        );
      default:
        return null;
    }
  };

  return (
    <Box>
      <Typography variant="h4" fontWeight={700} mb={3}>Reports</Typography>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} sm={4}>
              <FormControl fullWidth size="small">
                <InputLabel>Report Type</InputLabel>
                <Select value={reportType} label="Report Type" onChange={(e) => setReportType(e.target.value)}>
                  <MenuItem value="employees">Employee Report</MenuItem>
                  <MenuItem value="leaves">Leave Report</MenuItem>
                  <MenuItem value="performance">Performance Report</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item>
              <Button variant="outlined" startIcon={<DownloadIcon />} onClick={exportCSV}
                disabled={data.length === 0}>
                Export CSV
              </Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="h6" fontWeight={600} textTransform="capitalize">
              {reportType} Report
            </Typography>
            <Chip label={`${data.length} records`} size="small" color="primary" />
          </Box>
          <TableContainer>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                <CircularProgress />
              </Box>
            ) : renderTable()}
          </TableContainer>
        </CardContent>
      </Card>
    </Box>
  );
};

export default ReportsPage;
