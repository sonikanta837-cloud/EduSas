package com.emp.management.service;

import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.entity.Announcement;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository repo;

    public List<AnnouncementDTO> getAll() {
        return repo.findAllByOrderByPinnedDescCreatedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public AnnouncementDTO create(AnnouncementDTO dto) {
        Announcement saved = repo.save(Announcement.builder()
                .title(dto.getTitle())
                .body(dto.getBody())
                .authorName(dto.getAuthorName())
                .pinned(dto.isPinned())
                .build());
        return toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Announcement", id);
        repo.deleteById(id);
    }

    private AnnouncementDTO toDTO(Announcement a) {
        return AnnouncementDTO.builder()
                .id(a.getId())
                .title(a.getTitle())
                .body(a.getBody())
                .authorName(a.getAuthorName())
                .pinned(a.isPinned())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
