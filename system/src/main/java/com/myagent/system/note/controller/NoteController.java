package com.myagent.system.note.controller;

import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.note.controller.request.NoteSaveRequest;
import com.myagent.system.note.dao.entity.NoteDO;
import com.myagent.system.note.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "笔记")
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @Operation(summary = "创建笔记")
    @PostMapping
    public Result<NoteDO> create(@Valid @RequestBody NoteSaveRequest request) {
        return Results.success(noteService.create(request));
    }

    @Operation(summary = "我的笔记列表")
    @GetMapping
    public Result<List<NoteDO>> list() {
        return Results.success(noteService.listMine());
    }

    @Operation(summary = "笔记详情")
    @GetMapping("/{id}")
    public Result<NoteDO> detail(@PathVariable Long id) {
        return Results.success(noteService.getMine(id));
    }

    @Operation(summary = "更新笔记")
    @PutMapping("/{id}")
    public Result<NoteDO> update(@PathVariable Long id, @Valid @RequestBody NoteSaveRequest request) {
        return Results.success(noteService.updateMine(id, request));
    }

    @Operation(summary = "删除笔记")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noteService.deleteMine(id);
        return Results.success();
    }
}
