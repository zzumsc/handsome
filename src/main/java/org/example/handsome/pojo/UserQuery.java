package org.example.handsome.pojo;

import lombok.Data;

@Data
public class UserQuery {
    // 查询条件
    private String no;
    private String name;
    private String role;
    private String email;

    // 排序参数
    private String sortField;
    private String sortDir;


    public UserQuery() {
    }

    public UserQuery(String email, String no, String name, String role, String sortField, String sortDir) {
        this.email = email;
        this.no = no;
        this.name = name;
        this.role = role;
        this.sortField = sortField;
        this.sortDir = sortDir;
    }

    public String getNo() {
        return no;
    }

    public void setNo(String no) {
        this.no = no;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }
}
