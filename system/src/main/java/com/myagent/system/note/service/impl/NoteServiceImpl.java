package com.myagent.system.note.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.note.controller.request.NoteSaveRequest;
import com.myagent.system.note.dao.entity.NoteDO;
import com.myagent.system.note.dao.mapper.NoteMapper;
import com.myagent.system.note.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteMapper noteMapper;

    @Override
    public NoteDO create(NoteSaveRequest request) {
        Long userId = currentUserId();
        NoteDO note = new NoteDO();
        note.setUserId(userId);
        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent());
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());
        note.setDeleted(0);
        noteMapper.insert(note);
        return note;
    }

    @Override
    public List<NoteDO> listMine() {
        return noteMapper.selectList(Wrappers.lambdaQuery(NoteDO.class)
                .eq(NoteDO::getUserId, currentUserId())
                .orderByDesc(NoteDO::getUpdateTime));
    }

    @Override
    public NoteDO getMine(Long id) {
        return requireOwned(id);
    }

    @Override
    public NoteDO updateMine(Long id, NoteSaveRequest request) {
        NoteDO note = requireOwned(id);
        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent());
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        return note;
    }

    @Override
    public void deleteMine(Long id) {
        requireOwned(id);
        noteMapper.deleteById(id);
    }

    private NoteDO requireOwned(Long id) {
        NoteDO note = noteMapper.selectById(id);
        if (note == null || !note.getUserId().equals(currentUserId())) {
            throw new ClientException("笔记不存在或无权访问");
        }
        return note;
    }

    private Long currentUserId() {
        return Long.valueOf(UserContext.requireUser().getUserId());
    }
}
