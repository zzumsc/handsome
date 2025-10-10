package org.example.handsome.service;

import org.example.handsome.pojo.DTO.Result;
import org.example.handsome.pojo.User;
import org.example.handsome.pojo.UserQuery;

public interface CrudService {

    Result add(User user);       // 新增

    Result delete(Long id);      // 删除

    Result update(User user);    // 更新

    Result getById(Long id);     // 按ID查

    Result listByCondition(UserQuery query); // 多条件查（全量查+内存过滤）

}
