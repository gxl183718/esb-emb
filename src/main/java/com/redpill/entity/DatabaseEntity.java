package com.redpill.entity;


import lombok.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
public class DatabaseEntity {
    private String task_id;
    private String app_id;
    private String tenant_id;
    private String resource_id;
    private String resource_name;
    private String task_name;
    private String task_desc;
    private String task_type;
    private String state;
    private GlobalConf config = new GlobalConf();
    private List<Flow> flows = new ArrayList<>();

    @Data
    public static class GlobalConf{
        String http_host;
        Integer http_port;

        String data_type;
        String ip_address;
        Integer port;
        String database_ins;
        String user_name;
        String password;
        String character_set;
        
        String database_url;
        String driverClassName;

        public String getDriverClassName(){
            if (data_type.equalsIgnoreCase("mysql")){
                return "com.mysql.jdbc.Driver";
            }else if (data_type.equalsIgnoreCase("oracle")){
                return "oracle.jdbc.driver.OracleDriver";
            }else if (data_type.equalsIgnoreCase("sqlserver")){
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            }else if (data_type.equalsIgnoreCase("dameng")){
                return "dm.jdbc.driver.DmDriver";
            }else if (data_type.equalsIgnoreCase("highgo")){
                return "com.highgo.jdbc.Driver";
            }else if (data_type.equalsIgnoreCase("kingbase")){
                return "com.kingbase8.Driver";
            }else if (data_type.equalsIgnoreCase("shentong")){
                return "com.oscar.Driver";
            }else if (data_type.equalsIgnoreCase("db2")){
                return "com.ibm.db2.jcc.DB2Driver";
            }else {
                return null;
            }
        }

        public String getDatabase_url() {
            StringBuilder url = null;
            if (data_type.equalsIgnoreCase("mysql")){
                boolean firstParam = true;
                url = new StringBuilder("jdbc:mysql://");
                url.append(ip_address + ":" + port + "/" + database_ins);
                if (user_name != null){
                    url.append("?user=" + user_name);
                    firstParam = false;
                }
                if (password != null){
                    if (firstParam){
                        url.append("?password=" + password);
                    }else {
                        url.append("&password=" + password);
                    }
                    firstParam = false;
                }
                if (character_set != null){
                    if (firstParam){
                        url.append("?characterEncoding=" + character_set);
                    }else {
                        url.append("&characterEncoding=" + character_set);
                    }
                    firstParam = false;
                }
            } else if (data_type.equalsIgnoreCase("sqlserver")){
                    url = new StringBuilder("jdbc:sqlserver://" + ip_address + ":" + port + ";DatabaseName=" + database_ins + ";");
                    if (password != null){
                        url.append("password=" + password + ";");
                    }
                    if (user_name != null){
                        url.append("user=" + user_name + ";");
                    }
                    if (character_set != null){
                        url.append("characterEncoding=" + character_set + ";");
                    }
            }else if (data_type.equalsIgnoreCase("oracle")){
                return null;
            }else if (data_type.equalsIgnoreCase("db2")){
                boolean firstParam = true;
                url = new StringBuilder("jdbc:db2://" + ip_address + ":" + port + "/" + database_ins);
                if (user_name != null){
                    url.append(":user=" + user_name);
                    firstParam = false;
                }
                if (password != null){
                    if (firstParam){
                        url.append(":password=" + password);
                    }else {
                        url.append(";password=" + password);
                    }
                    firstParam = false;
                }
                if (character_set != null){
                    if (firstParam){
                        url.append(":characterEncoding=" + character_set);
                    }else {
                        url.append(";characterEncoding=" + character_set);
                    }
                }
            }else if (data_type.equalsIgnoreCase("dameng")){
                boolean firstParam = true;
                url = new StringBuilder("jdbc:dm://" + ip_address + ":" + port + "/" + database_ins);
                if (user_name != null){
                    url.append("?user=" + user_name);
                    firstParam = false;
                }
                if (password != null){
                    if (firstParam){
                        url.append("?password=" + password);
                    }else {
                        url.append("&password=" + password);
                    }
                    firstParam = false;
                }
                if (character_set != null){
                    if (firstParam){
                        url.append("?characterEncoding=" + character_set);
                    }else {
                        url.append("&characterEncoding=" + character_set);
                    }
                }

            }else if (data_type.equalsIgnoreCase("highgo")){
                boolean firstParam = true;
                url = new StringBuilder("jdbc:highgo://" + ip_address + ":" + port + "/" + database_ins);
                if (user_name != null){
                    url.append("?user=" + user_name);
                    firstParam = false;
                }
                if (password != null){
                    if (firstParam){
                        url.append("?password=" + password);
                    }else {
                        url.append("&password=" + password);
                    }
                    firstParam = false;
                }
                if (character_set != null){
                    if (firstParam){
                        url.append("?characterEncoding=" + character_set);
                    }else {
                        url.append("&characterEncoding=" + character_set);
                    }
                }
            }else if (data_type.equalsIgnoreCase("kingbase")){
                boolean firstParam = true;
                url = new StringBuilder("jdbc:kingbase8://" + ip_address + ":" + port + "/" + database_ins);
                if (user_name != null){
                    url.append("?user=" + user_name);
                    firstParam = false;
                }
                if (password != null){
                    if (firstParam){
                        url.append("?password=" + password);
                    }else {
                        url.append("&password=" + password);
                    }
                    firstParam = false;
                }
                if (character_set != null){
                    if (firstParam){
                        url.append("?characterEncoding=" + character_set);
                    }else {
                        url.append("&characterEncoding=" + character_set);
                    }
                }
            }else if (data_type.equalsIgnoreCase("shentong")){
                boolean firstParam = true;
                url = new StringBuilder("jdbc:oscar://" + ip_address + ":" + port + "/" + database_ins);
                if (user_name != null){
                    url.append("?user=" + user_name);
                    firstParam = false;
                }
                if (password != null){
                    if (firstParam){
                        url.append("?password=" + password);
                    }else {
                        url.append("&password=" + password);
                    }
                    firstParam = false;
                }
                if (character_set != null){
                    if (firstParam){
                        url.append("?characterEncoding=" + character_set);
                    }else {
                        url.append("&characterEncoding=" + character_set);
                    }
                }
            }
            return url.toString();
        }
    }

    @Data
    public static class Flow{
        SEsb sEsb;
        TEsb tEsb;
    }

    @Data
    public static class SEsb {
        String operate;
        String sql;
        String supply_dept_id;
        String supply_dept;

    }
    @Data
    public static class TEsb {
        String path;
        String method;
        String supply_dept_id;
        String supply_dept;

    }
}
