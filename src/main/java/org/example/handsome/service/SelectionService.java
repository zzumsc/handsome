package org.example.handsome.service;

import org.example.handsome.pojo.DTO.Result;

public interface SelectionService {

    Result getSelectionsByStudentId(Long targetStudentId);
}