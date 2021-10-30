package com.redpill.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TaskInfoEntity {
    private String task_type;
    private FConfig fConfig = null;
    private List<FFlow> flows = new ArrayList<>();

    @Data
    public static class FConfig{
        private Integer http_port;
    }
    @Data
    public static class FFlow{
        TEsb t_esb;
    }
    @Data
    public static class TEsb {
        String port;
    }

    public String getServerPort(){
        String s_port = null;
        if (null != fConfig){
            s_port = String.valueOf(fConfig.getHttp_port());
        }
        if (flows.size() > 0){
            s_port = flows.get(0).getT_esb().getPort();
        }
        return s_port;
    }

}
