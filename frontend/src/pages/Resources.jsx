import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Box, Card, CardContent, Typography, Button, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, IconButton,
  Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  CircularProgress, Chip
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import DownloadIcon from '@mui/icons-material/Download';
import DeleteIcon from '@mui/icons-material/Delete';
import FolderIcon from '@mui/icons-material/Folder';
import api from '../api/axios';
import { toast } from 'react-toastify';

const formatSize = (bytes) => {
  if (!bytes) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const ResourcesPage = () => {
  const { user } = useSelector((s) => s.auth);
  const [resources, setResources] = useState([]);
  const [loading, setLoading] = useState(true);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [file, setFile] = useState(null);
  const [description, setDescription] = useState('');
  const [uploading, setUploading] = useState(false);

  const fetchResources = () => {
    setLoading(true);
    api.get('/resources')
      .then(setResources)
      .catch(() => toast.error('Failed to load resources'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchResources(); }, []);

  const handleUpload = async () => {
    if (!file) { toast.error('Please select a file'); return; }
    const formData = new FormData();
    formData.append('file', file);
    if (description) formData.append('description', description);
    formData.append('uploadedBy', user.userId);

    setUploading(true);
    try {
      await api.post('/resources/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      toast.success('File uploaded successfully!');
      setUploadOpen(false);
      setFile(null);
      setDescription('');
      fetchResources();
    } catch {
      toast.error('Failed to upload file');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = (id, name) => {
    const token = localStorage.getItem('accessToken');
    const a = document.createElement('a');
    a.href = `/api/resources/download/${id}`;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this resource?')) return;
    try {
      await api.delete(`/resources/${id}`);
      toast.success('Resource deleted');
      fetchResources();
    } catch {
      toast.error('Failed to delete resource');
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <FolderIcon color="primary" />
          <Typography variant="h4" fontWeight={700}>Resources</Typography>
        </Box>
        {user?.role === 'ADMIN' && (
          <Button variant="contained" startIcon={<UploadIcon />} onClick={() => setUploadOpen(true)}>
            Upload File
          </Button>
        )}
      </Box>

      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8f9fa' }}>
                <TableCell>File Name</TableCell>
                <TableCell>Description</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Size</TableCell>
                <TableCell>Uploaded By</TableCell>
                <TableCell>Date</TableCell>
                <TableCell align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={7} align="center"><CircularProgress /></TableCell></TableRow>
              ) : resources.length === 0 ? (
                <TableRow><TableCell colSpan={7} align="center">No resources found</TableCell></TableRow>
              ) : resources.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={500}>{r.originalFileName}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color="text.secondary">{r.description || '-'}</Typography>
                  </TableCell>
                  <TableCell>
                    <Chip label={r.fileType?.split('/')[1] || 'file'} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell>{formatSize(r.fileSize)}</TableCell>
                  <TableCell>{r.uploadedBy?.email}</TableCell>
                  <TableCell>{r.uploadedAt?.substring(0, 10)}</TableCell>
                  <TableCell align="center">
                    <IconButton size="small" onClick={() => handleDownload(r.id, r.originalFileName)}>
                      <DownloadIcon fontSize="small" />
                    </IconButton>
                    {user?.role === 'ADMIN' && (
                      <IconButton size="small" color="error" onClick={() => handleDelete(r.id)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Dialog open={uploadOpen} onClose={() => setUploadOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle fontWeight={700}>Upload Resource</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <Button variant="outlined" component="label" fullWidth sx={{ py: 3, border: '2px dashed' }}>
              {file ? file.name : 'Click to select file'}
              <input type="file" hidden onChange={(e) => setFile(e.target.files[0])} />
            </Button>
            <TextField label="Description (optional)" value={description}
              onChange={(e) => setDescription(e.target.value)} multiline rows={2} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setUploadOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleUpload} disabled={uploading}>
            {uploading ? <CircularProgress size={20} /> : 'Upload'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ResourcesPage;
