package com.redpill.entity;

import com.alibaba.fastjson.JSON;
import com.redpill.tool.MuleConfig;
import com.redpill.tool.RedisUtils;
import com.zzq.dolls.config.LoadConfig;
import lombok.Data;
import org.springframework.http.converter.json.GsonBuilderUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
public class HttpProxyEntity {
    private String task_id;
    private String app_id;
    private String tenant_id;
    private String resource_id;
    private String resource_name;
    private String task_name;
    private String task_desc;
    private String task_type;
    private String state;
    private List<HttpFlow> flows = new ArrayList<>();

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

    public String getResource_id() {
        return resource_id;
    }

    public void setResource_id(String resource_id) {
        this.resource_id = resource_id;
    }

    public String getResource_name() {
        return resource_name;
    }

    public void setResource_name(String resource_name) {
        this.resource_name = resource_name;
    }

    public String getTask_name() {
        return task_name;
    }

    public void setTask_name(String task_name) {
        this.task_name = task_name;
    }

    public String getTask_desc() {
        return task_desc;
    }

    public void setTask_desc(String task_desc) {
        this.task_desc = task_desc;
    }

    public String getTask_type() {
        return task_type;
    }

    public void setTask_type(String task_type) {
        this.task_type = task_type;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public List<HttpFlow> getFlows() {
        return flows;
    }

    public void setFlows(List<HttpFlow> flows) {
        this.flows = flows;
    }

    public static class HttpFlow {
        SEsb s_esb;
        TEsb t_esb;

        public SEsb getS_esb() {
            return s_esb;
        }

        public void setS_esb(SEsb s_esb) {
            this.s_esb = s_esb;
        }

        public TEsb getT_esb() {
            return t_esb;
        }

        public void setT_esb(TEsb t_esb) {
            this.t_esb = t_esb;
        }
    }

    public static class SEsb {
        String data_type;
        String ip_address;
        String port;
        String path;
        String supply_dept_id;
        String supply_dept;

        public String getData_type() {
            return data_type;
        }

        public void setData_type(String data_type) {
            this.data_type = data_type;
        }

        public String getIp_address() {
            return ip_address;
        }

        public void setIp_address(String ip_address) {
            this.ip_address = ip_address;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSupply_dept_id() {
            return supply_dept_id;
        }

        public void setSupply_dept_id(String supply_dept_id) {
            this.supply_dept_id = supply_dept_id;
        }

        public String getSupply_dept() {
            return supply_dept;
        }

        public void setSupply_dept(String supply_dept) {
            this.supply_dept = supply_dept;
        }
    }
    public static class TEsb {
        String data_type;
        String ip_address;
        String port;
        String path;
        String supply_dept_id;
        String supply_dept;
        String method;

        public String getData_type() {
            return data_type;
        }

        public void setData_type(String data_type) {
            this.data_type = data_type;
        }

        public String getIp_address() {
            return ip_address;
        }

        public void setIp_address(String ip_address) {
            this.ip_address = ip_address;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSupply_dept_id() {
            return supply_dept_id;
        }

        public void setSupply_dept_id(String supply_dept_id) {
            this.supply_dept_id = supply_dept_id;
        }

        public String getSupply_dept() {
            return supply_dept;
        }

        public void setSupply_dept(String supply_dept) {
            this.supply_dept = supply_dept;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }

    public static void main(String[] args) throws IOException {

        LoadConfig.load(MuleConfig.class);
        String task = RedisUtils.redisPool.jedis(jedis -> {
           return jedis.hget("task_info","33d25bcd-b5dd-4079-9650-389c71c93f0a");
        });
        System.out.println(task);

        HttpProxyEntity httpProxyEntity1 = JSON.parseObject(task, HttpProxyEntity.class);
        System.out.println(httpProxyEntity1.getTask_type());
    }
}
