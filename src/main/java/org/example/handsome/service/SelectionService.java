package org.example.handsome.service;

import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.Selection;

import java.util.List;

public interface SelectionService {

    Result getSelectionsByStudentId(Long targetStudentId);
}