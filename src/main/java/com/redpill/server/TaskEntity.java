package com.redpill.server;

import lombok.Data;

@Data
public class TaskEntity {
    private String m_id = "";//消息id
    private String task_id = "";
    private String app_id = "";
    private String tenant_id = "";
    private String task_name = "";
    private int state;  //0:新增；1:修改；2:删除

    public TaskEntity() {
    }

    public TaskEntity(String m_id, String task_id, String app_id, String tenant_id, String task_name, int state) {
        this.m_id = m_id;
        this.task_id = task_id;
        this.app_id = app_id;
        this.tenant_id = tenant_id;
        this.task_name = task_name;
        this.state = state;
    }

    public String getM_id() {
        return m_id;
    }

    public void setM_id(String m_id) {
        this.m_id = m_id;
    }

    public String getTask_id() {
        return task_id;
    }

    public void setTask_id(String task_id) {
        this.task_id = task_id;
    }

    public String getApp_id() {
        return app_id;
    }

    public void setApp_id(String app_id) {
        this.app_id = app_id;
    }

    public String getTenant_id() {
        return tenant_id;
    }

    public void setTenant_id(String tenant_id) {
        this.tenant_id = tenant_id;
    }

    public String getTask_name() {
        return task_name;
    }

    public void setTask_name(String task_name) {
        this.task_name = task_name;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
}
