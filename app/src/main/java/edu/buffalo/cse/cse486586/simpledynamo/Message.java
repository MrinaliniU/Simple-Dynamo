package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

 class Message implements Serializable {

    private String value;
    private String port;
    private String preferenceListPort;
    private SELECTION selection;
    private QUERY_TYPE query_type;
    private RECOVERY_TYPE recovery_type;
    private String key;
    ConcurrentHashMap<String, String> recoveryQuerySaved;


    String getKey() {
        return key;
    }

    void setKey(String key) {
        this.key = key;
    }

    String getValue() {
        return value;
    }

    void setValue(String value) {
        this.value = value;
    }

    String getPort() {
        return port;
    }

    void setPort(String port) {
        this.port = port;
    }

    String getPreferenceListPort() {
        return preferenceListPort;
    }

    void setPreferenceListPort(String preferenceListPort) {
        this.preferenceListPort = preferenceListPort;
    }

    SELECTION getSelection() {
        return selection;
    }

    void setSelection(SELECTION selection) {
        this.selection = selection;
    }

    QUERY_TYPE getQuery_type() {
        return query_type;
    }

    void setQuery_type(QUERY_TYPE query_type) {
        this.query_type = query_type;
    }

    RECOVERY_TYPE getRecovery_type() {
        return recovery_type;
    }

    void setRecovery_type(RECOVERY_TYPE recovery_type) {
        this.recovery_type = recovery_type;
    }


    enum SELECTION {INSERT, QUERY, DELETE, RECOVERY}
    enum QUERY_TYPE {QUERY_ALL,QUERY_ONE, QUERY_DONE, QUERY_ONE_DONE}
    enum RECOVERY_TYPE {RECOVERY, RECOVERY_DONE}
}
