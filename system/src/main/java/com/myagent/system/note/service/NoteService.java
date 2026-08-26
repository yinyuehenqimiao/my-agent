package com.myagent.system.note.service;

import com.myagent.system.note.controller.request.NoteSaveRequest;
import com.myagent.system.note.dao.entity.NoteDO;

import java.util.List;

public interface NoteService {

    NoteDO create(NoteSaveRequest request);

    List<NoteDO> listMine();

    NoteDO getMine(Long id);

    NoteDO updateMine(Long id, NoteSaveRequest request);

    void deleteMine(Long id);
}
